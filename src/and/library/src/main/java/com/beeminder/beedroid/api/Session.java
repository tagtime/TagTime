/*
 * Copyright (C) 2012 Uluc Saranli
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.beeminder.beedroid.api;

import java.util.Random;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

/**
 * This class captures all communication tasks with the Beeminder app for
 * applications wanting to submit datapoints for goals of signed in users.
 * 
 * A Session object captures interactions with a single goal for a single user.
 * Following creation, users of this class should call either the
 * openForNewGoal() or the openForGoal() method to initialize the session. The
 * former invokes a Beeminder activity to let the user authorize access and pick
 * a goal. In contrast, the latter probes an internal database for previously
 * established sessions and authorizations to see whether a specific goal can be
 * accessed without an additional authorization step.
 */
public class Session {
	private final static String TAG = "Session";
	private final static boolean LOCAL_LOGV = false;

	/** Preference database name for storing session information */
	private static final String BEEDROID_SESSION_PREFS = "com.beeminder.beedroid.api.sessions";

	/** Beeminder app's package name and API protovol version */
	private static final String BEEDROID_PACKAGE = "com.beeminder.beeminder";
	private static final String BEEDROID_PROTOCOL_VERSION = "20131030";

	/** Intent action to visit a Beeminder goal. */
	private static final String ACTION_VISITGOAL = "com.beeminder.beeminder.VISITGOAL";
	/** Intent action to initiate the Beeminder API authorization activity. */
	private static final String ACTION_API_AUTHORIZE = "com.beeminder.beeminder.AUTHORIZE";
	/** Intent action to remove an authorization using the Beeminder API. */
	private static final String ACTION_API_UNAUTHORIZE = "com.beeminder.beeminder.UNAUTHORIZE";
	/** Intent action to initiate datapoint submission. */
	private static final String ACTION_API_EDITPOINT = "com.beeminder.beeminder.EDITPOINT";

	/*
	 * Keys used for communicating with the Beeminder authorization activity and
	 * datapoint submission service.
	 */
	private static final String KEY_API_PACKAGENAME = "pkgname";
	private static final String KEY_API_APPLICATIONNAME = "appname";
	private static final String KEY_API_PROTOCOLVERSION = "protover";
	private static final String KEY_API_USERNAME = "username";
	private static final String KEY_API_GOALSLUG = "slug";
	private static final String KEY_API_TOKEN = "token";

	private static final String KEY_API_REQUESTID = "req_id";
	private static final String KEY_API_POINTID = "ptid";
	private static final String KEY_API_VALUE = "value";
	private static final String KEY_API_TIMESTAMP = "timestamp";
	private static final String KEY_API_COMMENT = "comment";

	private static final String KEY_API_ERRORMSG = "error";
	private static final String KEY_API_ERRORCODE = "errorcode";

	private static final String KEY_VISITGOAL_USERNAME = "user";
	private static final String KEY_VISITGOAL_GOALSLUG = "goal";
	/*
	 * Definitions for message types used for communicating with Beeminder
	 * activities and services
	 */
	/** Identifies a message to initiate datapoint submission */
	private static final int MSG_API_CREATEPOINT = 1;
	private static final int MSG_API_DELETEPOINT = 2;
	private static final int MSG_API_UPDATEPOINT = 3;
	/** Identifies a message indicating a successful datapoint submission */
	private static final int MSG_API_RESPONSE_OK = 100;
	/**
	 * Identifies a message indicating a failed datapoint submission due to lack
	 * of authorization
	 */
	private static final int MSG_API_RESPONSE_UNAUTHORIZED = 101;
	/** Identifies a message indicating a failed datapoint submission */
	private static final int MSG_API_RESPONSE_ERROR = 102;
	/** Identifies a message indicating a version mismatch */
	private static final int MSG_API_RESPONSE_BADVERSION = 103;
	/** Identifies a message indicating that a data point was not found */
	private static final int MSG_API_RESPONSE_NOTFOUND = 104;

	/** Unique identifier integer for the Beeminder authorization activity */
	private static final int ACTIVITY_BEEMINDER_AUTH = 105674;

	/**
	 * This enumerated type collects various types of errors that can occur
	 * during Session usage
	 */
	public enum ErrorType {
		ERROR_NONE, ERROR_UNAUTHORIZED, ERROR_BADVERSION, ERROR_NOTFOUND, ERROR_OPEN, ERROR_OTHER
	}

	/**
	 * This class captures errors generated throughout regular Session
	 * operation, incorporating an error type and a message in its public
	 * fields.
	 */
	public class SessionError {
		public ErrorType type;
		public String message;

		public SessionError(ErrorType errtype, String errmsg) {
			type = errtype;
			message = errmsg;
		}
	};

	/**
	 * This exception captures various fault conditions that can occur during
	 * method calls to the Session class
	 */
	public class SessionException extends Exception {
		private static final long serialVersionUID = 42L;

		private String mMessage;

		@Override
		public String getMessage() {
			return mMessage;
		}

		public SessionException(String msg) {
			mMessage = msg;
		}
	};

	/** This type captures various states associated with the Session object */
	public enum SessionState {
		CLOSED, OPENING, OPENED, CLOSED_ON_ERROR
	}

	/**
	 * This callback interface allows users of the Session class to register a
	 * callback function to be invoked on session states changes
	 */
	public interface StatusCallback {
		public void call(Session session, SessionState state, SessionError error);
	}

	/**
	 * This callback interface allows users of the Session class to register a
	 * callback function to be invoked when the result of a datapoint submission
	 * request has been received.
	 */
	public interface SubmissionCallback {
		public void call(Session session, int submission_id, String request_id, String error);
	}

	// Shared preferences to associate tokens with username/goal slug pairs
	private SharedPreferences mSP;
	// Activity that created the Session object
	private Context mContext;
	// Currently registered status callback object
	private StatusCallback mStatusCallback = null;
	// Currently registered submission callback object
	private SubmissionCallback mSubmissionCallback = null;
	// Handler to delegate callback execution to the main thread
	private Handler mMainHandler;

	// Objects related to the Messenger interface to the Beeminder app
	private Messenger mService = null;
	private boolean mBound;
	private Messenger mReplyTo = null;

	// Current session state
	private SessionState mState = SessionState.CLOSED;

	// Information related to the application using the Session
	private String mPackageName;
	private String mAppName;

	// Username, goal slug and token information for the currently active
	// session.
	private String mUsername;
	private String mGoalSlug;
	private String mToken;
	// Initialize a value for the error object in case Service is killed and
	// restarted by the system
	private SessionError mError = new SessionError(ErrorType.ERROR_NONE, "");

	/**
	 * This method returns the access token associated with the current session.
	 */
	public String getToken() {
		return mToken;
	}

	/** This method returns the username associated with the current session. */
	public String getUsername() {
		return mUsername;
	}

	/** This method returns the goal slug associated with the current session. */
	public String getGoalSlug() {
		return mGoalSlug;
	}

	/** This method returns the latest error generated during Session operation. */
	public final SessionError getError() {
		// TODO: Google play indicates mError ends up null here somehow. Solve.
		return mError;
	}

	/** This method returns the current state of the Session */
	public SessionState getState() {
		return mState;
	}

	/**
	 * This method sets the status callback to be called on session status
	 * changes.
	 */
	public Session setStatusCallback(StatusCallback statusCallback) {
		mStatusCallback = statusCallback;
		return this;
	}

	/** This method returns the currently registered status callback object. */
	public StatusCallback getStatusCallback() {
		return mStatusCallback;
	}

	/**
	 * This method sets the submission callback to be invoked when a datapoint
	 * submission is completed.
	 */
	public Session setSubmissionCallback(SubmissionCallback submissionCallback) {
		mSubmissionCallback = submissionCallback;
		return this;
	}

	/** This method returns the currently registered submission callback object. */
	public SubmissionCallback getSubmissionCallback() {
		return mSubmissionCallback;
	}

	/**
	 * Constructor for creating a session object associated with an activity.
	 * Performsnecessary initialization steps.
	 */
	public Session(Context ctx) throws SessionException {
		try {
			ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo(ctx.getPackageName(), 0);
			mPackageName = ctx.getPackageName();
			mAppName = ctx.getPackageManager().getApplicationLabel(ai).toString();
			mContext = ctx;
		} catch (NameNotFoundException e) {
			throw new SessionException("Could not retrieve package name or application label");
		}

		// This is for storing internal persistent state information
		mSP = ctx.getSharedPreferences(BEEDROID_SESSION_PREFS, Activity.MODE_PRIVATE);

		mMainHandler = new Handler(Looper.getMainLooper());
		mReplyTo = new Messenger(new IncomingHandler());
	}

	// Utility method to handle state changes, invoking callbacks as necessary
	private void postStateChange(final SessionState oldState, final SessionState newState) {
		if ((oldState == newState) || mStatusCallback == null) {
			return;
		}

		Runnable runCallback = new Runnable() {
			public void run() {
				mStatusCallback.call(Session.this, newState, mError);
			}
		};
		mMainHandler.post(runCallback);
	}

	/**
	 * This method closes the current session, detaching from the Beeminder
	 * datapoint submission service if necessary
	 */
	public final void close() {
		if (mBound) {
			mContext.unbindService(mConnection);
			mBound = false;
		}
		SessionState oldstate = mState;
		mState = SessionState.CLOSED;
		postStateChange(oldstate, mState);
	}

	// This utility method closes the current Session after an error, putting
	// the Session in the CLOSED_ON_ERROR state.
	private final void closeWithError(SessionError err) {
		if (mBound) {
			mContext.unbindService(mConnection);
			mBound = false;
		}

		mError = err;
		SessionState oldstate = mState;
		mState = SessionState.CLOSED_ON_ERROR;
		postStateChange(oldstate, mState);
	}

	// This utility method performs tasks common to all open actions
	private void openCommon(boolean reopen) throws SessionException {

		if (!reopen && mState == SessionState.OPENED) {
			throw new SessionException("Attempt to reopen an already open session");
		} else if (mState == SessionState.OPENING) {
			throw new SessionException("Attempt to reopen an opening session");
		}

	}

	/**
	 * This method attempts to open a session by directing the user to a
	 * Beeminder authorization activity where a user and goal slug pair can be
	 * chosen the user can confirm that access is allowed. The response from the
	 * activity will include an access token together withselected goal
	 * information if the user approved access
	 */
	public final void openForNewGoal() throws SessionException {

		openCommon(false);

		Intent intent = new Intent().setAction(ACTION_API_AUTHORIZE).setPackage(BEEDROID_PACKAGE)
				.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).addCategory(Intent.CATEGORY_DEFAULT)
				.putExtra(KEY_API_PACKAGENAME, mPackageName).putExtra(KEY_API_APPLICATIONNAME, mAppName)
				.putExtra(KEY_API_PROTOCOLVERSION, BEEDROID_PROTOCOL_VERSION);
		try {
			if (mContext instanceof Activity) {
				Activity act = (Activity) mContext;
				act.startActivityForResult(intent, ACTIVITY_BEEMINDER_AUTH);
			} else throw new SessionException("Provided context is not an Activity");
		} catch (ActivityNotFoundException e) {
			throw new SessionException("Could not initiate Beeminder authorization activity");
		}

		SessionState oldstate = mState;
		mState = SessionState.OPENING;
		postStateChange(oldstate, mState);
	}

	public final void reopenForGoal(String username, String slug) throws SessionException {
		openCommon(true);

		String key = username + "/" + slug + "_token";
		String token = mSP.getString(key, null);
		if (token == null) {
			String errmsg = "Could not find existing session token";
			mError = new SessionError(ErrorType.ERROR_UNAUTHORIZED, errmsg);
			throw new SessionException(errmsg);
		}

		mUsername = username;
		mGoalSlug = slug;
		mToken = token;

		if (mState == SessionState.OPENED) {
			mState = SessionState.OPENED;
			// Forces callback to be issued again
			postStateChange(SessionState.OPENING, mState);
		} else {
			SessionState oldstate = mState;
			mState = SessionState.OPENING;
			postStateChange(oldstate, mState);
		}

		finishOpen();
	}

	/**
	 * This method attempts to open a session associated with a particular user
	 * and goal slug pair. This requires a previous openForNewGoal() to have
	 * succeeded for this user and goal slug pair, for which the access token
	 * will have been recorded internally. This cached token will be used for
	 * the opened session. However, if this token generates an authorization
	 * error, the session willbe closed and the token will be removed from the
	 * internal cache.
	 */
	public final void openForGoal(String username, String slug) throws SessionException {
		openCommon(false);

		String key = username + "/" + slug + "_token";
		String token = mSP.getString(key, null);
		if (token == null) {
			String errmsg = "Could not find existing session token";
			mError = new SessionError(ErrorType.ERROR_UNAUTHORIZED, errmsg);
			throw new SessionException(errmsg);
		}

		mUsername = username;
		mGoalSlug = slug;
		mToken = token;

		SessionState oldstate = mState;
		mState = SessionState.OPENING;
		postStateChange(oldstate, mState);

		finishOpen();
	}

	// This utility method performs post-open actions common to all open
	// requests.
	private void finishOpen() {
		if (LOCAL_LOGV) Log.v(TAG, "finishOpen()");
		if (mState == SessionState.OPENING) {
			Intent intent = new Intent().setAction(ACTION_API_EDITPOINT).setPackage(BEEDROID_PACKAGE);
			mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
		}
	}

	/**
	 * This method must be called from the onActivityResult() method of the
	 * activity that created the session. It processes the response from the
	 * Beeminder authorization activity to retrieve various pieces of important
	 * information as well as the authorization status.
	 */
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (LOCAL_LOGV) Log.v(TAG, "onActivityResult(" + resultCode + ")");
		switch (requestCode) {
		case ACTIVITY_BEEMINDER_AUTH:
			if (resultCode == Activity.RESULT_OK && intent != null) {
				Bundle extras = intent.getExtras();

				mUsername = extras.getString(KEY_API_USERNAME);
				mGoalSlug = extras.getString(KEY_API_GOALSLUG);
				mToken = extras.getString(KEY_API_TOKEN);

				SharedPreferences.Editor edit = mSP.edit();
				edit.putString(mUsername + "/" + mGoalSlug + "_token", mToken);
				edit.commit();

				SessionState oldstate = mState;
				mState = SessionState.OPENING;
				postStateChange(oldstate, mState);
			} else {
				int errorcode = MSG_API_RESPONSE_ERROR;
				String errormsg = "unknown";
				if (intent != null) {
					Bundle extras = intent.getExtras();
					errorcode = extras.getInt(KEY_API_ERRORCODE, MSG_API_RESPONSE_ERROR);
					errormsg = extras.getString(KEY_API_ERRORMSG);
				}

				SessionError error;
				switch (errorcode) {
				case MSG_API_RESPONSE_BADVERSION:
					error = new SessionError(ErrorType.ERROR_BADVERSION, errormsg);
					break;
				case MSG_API_RESPONSE_UNAUTHORIZED:
					error = new SessionError(ErrorType.ERROR_UNAUTHORIZED, errormsg);
					break;
				case MSG_API_RESPONSE_ERROR:
				default:
					error = new SessionError(ErrorType.ERROR_OPEN, errormsg);
					break;
				}

				closeWithError(error);
			}
			finishOpen();
			break;
		default:
			break;
		}
	}

	/**
	 * This method can be used to ask the Beeminder app to display a particular
	 * goal. The session object does not need to be open for this method to be
	 * called, it can be called immediately after the session is created.
	 */
	public void visitGoal(String username, String goalslug) throws SessionException {
		Intent intent = new Intent().setAction(ACTION_VISITGOAL).setPackage(BEEDROID_PACKAGE)
				.addCategory(Intent.CATEGORY_DEFAULT).putExtra(KEY_VISITGOAL_USERNAME, username)
				.putExtra(KEY_VISITGOAL_GOALSLUG, goalslug);
		try {
			if (mContext instanceof Activity) {
				Activity act = (Activity) mContext;
				act.startActivity(intent);
			} else throw new SessionException("Provided context is not an Activity");
		} catch (ActivityNotFoundException e) {
			throw new SessionException("Could not initiate Beeminder goal detail activity");
		}
	}

	/**
	 * This method can be called by the activity that created the session to
	 * submit a new data point with the supplied content. It generates a random
	 * integer ID for the data point, then forwards the request to the Beeminder
	 * app. Once the request is completed and a response is received, the
	 * submission callback will be called if it was previously registered.
	 */
	public final int createPoint(double value, long timestamp, String comment) throws SessionException {
		if (!mBound) {
			throw new SessionException("Beeminder service not bound");
		}

		if (mState != SessionState.OPENED) {
			throw new SessionException("Attempt to submit on closed session");
		}

		int submitId = newSubmissionId();
		Message msg = Message.obtain(null, MSG_API_CREATEPOINT, 0, 0);
		Bundle extras = new Bundle();
		extras.putString(KEY_API_PACKAGENAME, mPackageName);
		extras.putString(KEY_API_PROTOCOLVERSION, Session.BEEDROID_PROTOCOL_VERSION);
		extras.putString(KEY_API_TOKEN, mToken);
		extras.putString(KEY_API_USERNAME, mUsername);
		extras.putString(KEY_API_GOALSLUG, mGoalSlug);

		extras.putInt(KEY_API_POINTID, submitId);
		extras.putDouble(KEY_API_VALUE, value);
		if (timestamp != 0) extras.putLong(KEY_API_TIMESTAMP, timestamp);
		if (comment != null) extras.putString(KEY_API_COMMENT, comment);

		msg.setData(extras);
		msg.replyTo = mReplyTo;

		try {
			mService.send(msg);
		} catch (RemoteException e) {
			e.printStackTrace();
			throw new SessionException("Could not send message to Beeminder service");
		}
		return submitId;
	}

	/**
	 * This method can be called by the activity that created the session to
	 * delete a datapoint with the supplied request_id. It forwards the request
	 * to the Beeminder app. Once the request is completed and a response is
	 * received, the submission callback will be called if it was previously
	 * registered.
	 */
	public final int deletePoint(String requestId) throws SessionException {

		if (!mBound) throw new SessionException("Beeminder service not bound");
		if (mState != SessionState.OPENED) throw new SessionException("Attempt to submit on closed session");

		int submitId = newSubmissionId();
		Message msg = Message.obtain(null, MSG_API_DELETEPOINT, 0, 0);
		Bundle extras = new Bundle();
		extras.putString(KEY_API_PACKAGENAME, mPackageName);
		extras.putString(KEY_API_PROTOCOLVERSION, Session.BEEDROID_PROTOCOL_VERSION);
		extras.putString(KEY_API_TOKEN, mToken);
		extras.putString(KEY_API_USERNAME, mUsername);
		extras.putString(KEY_API_GOALSLUG, mGoalSlug);

		extras.putInt(KEY_API_POINTID, submitId);
		extras.putString(KEY_API_REQUESTID, requestId);

		msg.setData(extras);
		msg.replyTo = mReplyTo;

		try {
			mService.send(msg);
		} catch (RemoteException e) {
			e.printStackTrace();
			throw new SessionException("Could not send message to Beeminder service");
		}
		return submitId;
	}

	// Class for interacting with the main interface of the service.
	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			if (LOCAL_LOGV) Log.v(TAG, "onServiceConnected()");
			mService = new Messenger(service);
			mBound = true;
			SessionState oldstate = mState;
			mState = SessionState.OPENED;
			postStateChange(oldstate, mState);
		}

		public void onServiceDisconnected(ComponentName className) {
			if (LOCAL_LOGV) Log.v(TAG, "onServiceDisconnected()");
			mService = null;
			mBound = false;
		}
	};

	// Handler to process response messages coming from the Beeminder app.
	private class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			if (LOCAL_LOGV) Log.v(TAG, "handleMessage: Response received with " + msg.what);

			Bundle extras = msg.getData();
			String errormsg = null, username = null, slug = null, request_id = null;
			int pointId = -1;

			if (extras != null) {
				errormsg = extras.getString(Session.KEY_API_ERRORMSG);
				pointId = extras.getInt(Session.KEY_API_POINTID);
				username = extras.getString(Session.KEY_API_USERNAME);
				slug = extras.getString(Session.KEY_API_GOALSLUG);
				request_id = extras.getString(Session.KEY_API_REQUESTID);
			}
			int what = msg.what;

			if (what == Session.MSG_API_RESPONSE_UNAUTHORIZED) {
				if (username != null && slug != null) {
					// Seems like authorization has been revoked. Clear database
					// and close session
					SharedPreferences.Editor edit = mSP.edit();
					edit.remove(mUsername + "/" + mGoalSlug + "_token");
					edit.commit();
					closeWithError(new SessionError(ErrorType.ERROR_UNAUTHORIZED, errormsg));
				}
			} else if (what == Session.MSG_API_RESPONSE_ERROR) {
				mError = new SessionError(ErrorType.ERROR_OTHER, errormsg);
			} else if (what == Session.MSG_API_RESPONSE_BADVERSION) {
				mError = new SessionError(ErrorType.ERROR_BADVERSION, errormsg);
			} else if (what == Session.MSG_API_RESPONSE_NOTFOUND) {
				mError = new SessionError(ErrorType.ERROR_NOTFOUND, errormsg);
			}

			if (mSubmissionCallback != null) {
				final int pointId_final = pointId;
				final String error_final = errormsg, requestId_final = request_id;
				Runnable runCallback = new Runnable() {
					public void run() {
						mSubmissionCallback.call(Session.this, pointId_final, requestId_final, error_final);
					}
				};
				mMainHandler.post(runCallback);
			}
		}
	}

	private static Random random = null;

	// Facility to generate random integer point identifiers. No need to be
	// secure here.
	private static int newSubmissionId() {
		if (random == null) random = new Random();
		return random.nextInt(Integer.MAX_VALUE);
	}

}
