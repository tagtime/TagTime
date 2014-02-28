package bsoule.tagtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.beeminder.beedroid.api.Session;
import com.beeminder.beedroid.api.Session.SessionError;
import com.beeminder.beedroid.api.Session.SessionState;

/**
 * This service is used by the rest of TagTime to access Beeminder through its
 * API. The onHandleIntent() method listens for incoming intents, currently
 * supporting ping editing action with the ping id, old tag list and the new tag
 * list supplied.
 */
public class BeeminderService extends IntentService {
	private static final String TAG = "BeeminderService";
	private static final boolean LOCAL_LOGV = true && !TagTime.DISABLE_LOGV;

	public static final String ACTION_EDITPING = "editping";

	public static final String KEY_PID = "ping_id";
	public static final String KEY_OLDTAGS = "oldtags";
	public static final String KEY_NEWTAGS = "newtags";
	public static final String KEY_RETRIES = "retries";

	private static final int SEMAPHORE_TIMEOUT = 30;
	private static final int RETRY_DELAY = 60;
	private static final int MAX_RETRIES = 5;

	private BeeminderDbAdapter mBeeDB;
	private PingsDbAdapter mPingDB;
	private Session mBeeminder;

	private final Semaphore mSubmitSem = new Semaphore(0, true);
	private final Semaphore mOpenSem = new Semaphore(0, true);
	private boolean mWaitingOpen = false;
	private Session.ErrorType mLastError = null;

	/** Class used to record and maintain points to be submitted */
	private class Point {
		public int submissionId;
		public String requestId;
		public String user;
		public String slug;
		public double value;
		public long timestamp;
		public String comment;

		public Point() {
			submissionId = -1;
			requestId = null;
		}
	};

	/**
	 * Global Beeminder point object initialized for submission and later
	 * accessed by callback functions
	 */
	private Point mPoint = new Point();

	/**
	 * This method generates a Notification that indicates a protocol version
	 * mismatch between the Beeminder API library and the Beeminder version
	 * installed on the device
	 */
	private void notifyVersionError(String submsg) {
		String msg = "Error opening Beeminder session.";
		Intent intent = new Intent(getApplicationContext(), TPController.class);
		PendingIntent ci = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Notification notif = new NotificationCompat.Builder(getApplicationContext()).setContentTitle(msg)
				.setContentText(submsg).setSmallIcon(R.drawable.error_ticker).setContentIntent(ci).build();
		notif.flags |= Notification.FLAG_AUTO_CANCEL;
		nm.notify(0, notif);
	}

	/**
	 * This method generates a Notification that indicates an authorization
	 * error generated while attempting to authorize a particular goal with the
	 * Beeminder app. This might happen if, for some reason, the Beeminder app's
	 * authorization database has been reset. TODO: Is this correct? Also, is
	 * soft recovery through re-authorization possible?
	 */
	private void notifyAuthorizationError(String user, String goal) {
		String msg = "Authorization lost for " + user + "/" + goal;
		String submsg = "You should re-link to this goal";
		Intent intent = new Intent(getApplicationContext(), ViewGoals.class);
		PendingIntent ci = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Notification notif = new NotificationCompat.Builder(getApplicationContext()).setContentTitle(msg)
				.setContentText(submsg).setSmallIcon(R.drawable.error_ticker).setContentIntent(ci).build();
		notif.flags |= Notification.FLAG_AUTO_CANCEL;
		nm.notify(0, notif);
	}

	/**
	 * This method resends the edit ping request (with a delay) to
	 * BeeminderService in cases where point submission failed. We will keep
	 * retrying until submission succeeds, or until MAX_RETRIES is reached
	 * (checked by onHandleIntent()).
	 */
	private void retryIntent() {
		Intent intent = new Intent(getApplicationContext(), BeeminderService.class);
		intent.setAction(ACTION_EDITPING);
		intent.putExtra(KEY_PID, mPingId);
		intent.putExtra(KEY_OLDTAGS, mOldTagsIn);
		intent.putExtra(KEY_NEWTAGS, mNewTagsIn);
		intent.putExtra(KEY_RETRIES, mRetries);
		intent.setData(Uri.parse("file://" + mPoint.requestId + mRetries));
		PendingIntent sender = PendingIntent.getService(getApplicationContext(), 0, intent, 0);
		AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.SECOND, RETRY_DELAY);
		if (LOCAL_LOGV) Log.v(TAG, "Retrying edits for ping " + mPingId + " at " + cal.getTimeInMillis());
		am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), sender);
	}

	/**
	 * This method generates a notification that indicates that the maximum
	 * number of retries for point submission has been reached. If the user
	 * clicks the notification, the offending will be opened so the user can
	 * re-edit it and attempt submission.
	 */
	private void notifyForResubmit() {
		String msg = "Error updating ping " + mPingId;
		String submsg = "Click to re-edit ping";
		Intent intent = new Intent(this, EditPing.class);
		intent.putExtra(PingsDbAdapter.KEY_ROWID, mPingId);
		PendingIntent ci = PendingIntent.getActivity(this, mPoint.submissionId, intent, 0);
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Notification notif = new NotificationCompat.Builder(this).setContentTitle(msg).setContentText(submsg)
				.setSmallIcon(R.drawable.error_ticker).setContentIntent(ci).build();
		notif.flags |= Notification.FLAG_AUTO_CANCEL;
		nm.notify(mPoint.submissionId, notif);
	}

	/**
	 * This callback tracks Beeminder API library status changes. It uses
	 * semaphores to synchronize with the rest of the service execution. This
	 * will be called from the main thread (depends on the implementation of the
	 * Beeminder API library), so proper thread synchronization will be
	 * necessary.
	 */
	private class SessionStatusCallback implements Session.StatusCallback {
		@Override
		public void call(Session session, SessionState state, SessionError error) {
			if (LOCAL_LOGV) Log.v(TAG,
					"Session Callback: Beeminder status changed:" + state + ", error=" + session.getError());

			if (state == SessionState.OPENED) {
				// Successful open. Signal the service thread to continue.
				if (mWaitingOpen) {
					mOpenSem.release();
					mWaitingOpen = false;
				}
			} else if (state == SessionState.CLOSED_ON_ERROR) {
				// Failed with error. Signal the service thread to continue.
				if (mWaitingOpen) {
					mOpenSem.release();
					mWaitingOpen = false;
				}
				// Generate a notification depending on the type of error
				if (error == null) {
					Log.w(TAG, "Session closed with unknown error");
				} else { 
					if (error.type == Session.ErrorType.ERROR_BADVERSION) {
						notifyVersionError(session.getError().message);
					} else if (error.type == Session.ErrorType.ERROR_UNAUTHORIZED) {
						// TODO: Must remove this goal from the list of
						// Beeminder
						// links. This might happen when Beeminder app is
						// uninstalled and reinstalled
						notifyAuthorizationError(mPoint.user, mPoint.slug);
					}
					mLastError = session.getError().type;
				}
			} else if (state == SessionState.CLOSED) {
				// Nothing here since it is a normal close.
			}
		}
	}

	/**
	 * This class implements a callback handling the response at the end of
	 * datapoint submission. This will be called from the main thread (depends
	 * on the implementation of the Beeminder API library), so proper thread
	 * synchronization will be necessary.
	 */
	private class PointSubmissionCallback implements Session.SubmissionCallback {
		@Override
		public void call(Session session, int submission_id, String request_id, String error) {
			if (LOCAL_LOGV) Log.v(TAG, "Point Callback: Point operation completed, id=" + submission_id + ", req_id="
					+ request_id + ", error=" + error);
			if (error == null && submission_id == mPoint.submissionId) {
				// All is well. Record the returned request ID.
				mPoint.requestId = request_id;
				mLastError = null;
			} else {
				// Point submission failed. Figure out why and inform the user.
				Log.w(TAG, "Point Callback: Submission error or ID mismatch. msg=" + error);
				if (session.getError().type == Session.ErrorType.ERROR_BADVERSION) {
					notifyVersionError(session.getError().message);
				} else if (session.getError().type == Session.ErrorType.ERROR_UNAUTHORIZED) {
					// TODO: Remove this goal from the list of Beeminder links.
					notifyAuthorizationError(mPoint.user, mPoint.slug);
				} else if (session.getError().type == Session.ErrorType.ERROR_NOTFOUND) {
					// Points that did not yet make it to the server may appear
					// as not found. Give up only after all the retries.
				}
				mLastError = session.getError().type;
			}
			mSubmitSem.release();
		}
	}

	/**
	 * This method initializes the global point object from user and slug
	 * details associated with a goal in the Beeminder goal database. It assumes
	 * mBeeDB is opened and ready.
	 */
	private boolean initializePointFromGoal(long goal_id) {
		Cursor c = mBeeDB.fetchGoal(goal_id);
		if (c.getCount() == 0) return false;
		mPoint.user = c.getString(1);
		mPoint.slug = c.getString(2);
		mPoint.requestId = null;
		c.close();
		return true;
	}

	/**
	 * This method initializes the global point object from an existing point in
	 * the Beeminder goal database. It assumes mBeeDB is opened and ready.
	 */
	private boolean initializePoint(long point_id) {
		Cursor c = mBeeDB.fetchPoint(point_id);
		if (c.getCount() == 0) return false;
		initializePointFromGoal(c.getLong(5));
		mPoint.requestId = c.getString(1);
		c.close();
		return true;
	}

	/**
	 * This method starts the process for creating a Beeminder point. It first
	 * reopens a session using the Beeminder API library for the specified goal
	 * and waits for the result. It then issues a point creation request through
	 * the API and waits for it to finish. Called by the worker thread.
	 */
	private String createBeeminderPoint(long goal_id, double value, long time, String comment) {
		mLastError = null;
		if (!initializePointFromGoal(goal_id)) return null;
		mPoint.value = value;
		mPoint.timestamp = time;
		mPoint.comment = comment;

		if (mBeeminder != null) {
			try {
				if (LOCAL_LOGV) Log.v(TAG, "createBeeminderPoint: Requesting open for " + mPoint.user + "/"
						+ mPoint.slug);

				mWaitingOpen = true;
				mBeeminder.reopenForGoal(mPoint.user, mPoint.slug);

				// if (LOCAL_LOGV) Log.v(TAG,
				// "createBeeminderPoint: Open finished, waiting for open semaphore.");

				// Try to acquire semaphore with a timeout of 2 seconds
				boolean opened = mOpenSem.tryAcquire(1, SEMAPHORE_TIMEOUT, TimeUnit.SECONDS);

				// if (LOCAL_LOGV) Log.v(TAG,
				// "createBeeminderPoint: Open semaphore acquired:" + opened);

				if (mBeeminder.getState() == Session.SessionState.OPENED) {

					if (LOCAL_LOGV) Log.v(TAG, "createBeeminderPoint: Submitting point.");

					mPoint.submissionId = mBeeminder.createPoint(mPoint.value, mPoint.timestamp, mPoint.comment);

					// if (LOCAL_LOGV) Log.v(TAG,
					// "createBeeminderPoint: Submission done, waiting for point semaphore");

					// Try to acquire semaphore with a timeout of 2 seconds
					boolean submitted = mSubmitSem.tryAcquire(1, SEMAPHORE_TIMEOUT, TimeUnit.SECONDS);

					if (LOCAL_LOGV) Log.v(TAG, "createBeeminderPoint: Submit semaphore acquired:" + submitted);
				}

				if (LOCAL_LOGV) Log.v(TAG, "createBeeminderPoint: Closing session.");

				// mBeeminder.close();
			} catch (Session.SessionException e) {
				Log.w(TAG, "createBeeminderPoint: Error opening session or submitting point. msg=" + e.getMessage());
				Session.SessionError err = mBeeminder.getError();
				if (err != null && err.type == Session.ErrorType.ERROR_UNAUTHORIZED) {
					Log.w(TAG, "createBeeminderPoint: Unauthorized goal. Deleting link to goal " + goal_id);
					mBeeDB.deleteGoal(goal_id);
				}
			} catch (InterruptedException e) {
				Log.w(TAG, "createBeeminderPoint: interrupted. msg=" + e.getMessage());
			}

		}

		if (mLastError != null) {
			retryIntent();
			return null;
		}

		return mPoint.requestId;
	}

	/** Called by the worker thread */
	private boolean deleteBeeminderPoint(long point_id) {
		boolean result = false;
		mLastError = null;
		if (!initializePoint(point_id)) return false;

		if (mBeeminder != null) {
			try {
				if (LOCAL_LOGV) Log.v(TAG, "deleteBeeminderPoint: Requesting open for " + mPoint.user + "/"
						+ mPoint.slug);

				mWaitingOpen = true;
				mBeeminder.reopenForGoal(mPoint.user, mPoint.slug);

				// if (LOCAL_LOGV) Log.v(TAG,
				// "deleteBeeminderPoint: Open finished, waiting for open semaphore.");

				// Try to acquire semaphore with a timeout of 2 seconds
				boolean opened = mOpenSem.tryAcquire(1, SEMAPHORE_TIMEOUT, TimeUnit.SECONDS);

				// if (LOCAL_LOGV) Log.v(TAG,
				// "deleteBeeminderPoint: Open semaphore acquired:" + opened);

				if (mBeeminder.getState() == Session.SessionState.OPENED) {

					if (LOCAL_LOGV) Log.v(TAG, "deleteBeeminderPoint: Deleting point.");

					mPoint.submissionId = mBeeminder.deletePoint(mPoint.requestId);

					// if (LOCAL_LOGV) Log.v(TAG,
					// "deleteBeeminderPoint: Submission done, waiting for point semaphore");

					// Try to acquire semaphore with a timeout of 2 seconds
					boolean submitted = mSubmitSem.tryAcquire(1, SEMAPHORE_TIMEOUT, TimeUnit.SECONDS);
					if (mLastError == null) result = true;

					if (LOCAL_LOGV) Log.v(TAG, "deleteBeeminderPoint: Submit semaphore acquired:" + submitted);
				}

				if (LOCAL_LOGV) Log.v(TAG, "deleteBeeminderPoint: Closing session.");

				// mBeeminder.close();
			} catch (Session.SessionException e) {
				Log.w(TAG, "deleteBeeminderPoint: Error opening session or deleting point. msg=" + e.getMessage());
			} catch (InterruptedException e) {
				Log.w(TAG, "deleteBeeminderPoint: interrupted. msg=" + e.getMessage());
			}

		}
		if (!result) retryIntent();

		return result;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		if (LOCAL_LOGV) Log.v(TAG, "onCreate: Beeminder service created.");

		mBeeDB = BeeminderDbAdapter.getInstance();
		mPingDB = PingsDbAdapter.getInstance();
		
		try {
			mBeeminder = new Session(getApplicationContext());
			mBeeminder.setStatusCallback(new SessionStatusCallback());
			mBeeminder.setSubmissionCallback(new PointSubmissionCallback());
		} catch (Session.SessionException e) {
			Log.w(TAG, "onCreate: Error creating Beeminder Session: " + e.getMessage());
			mBeeminder = null;
		}
	}

	@Override
	public void onDestroy() {
		if (LOCAL_LOGV) Log.v(TAG, "onDestroy()");
		if (mBeeminder != null) mBeeminder.close();
		super.onDestroy();
	}

	private List<Long> findPingPoints(long ping_id) {
		List<Long> points = new ArrayList<Long>(0);
		Cursor pp = mBeeDB.fetchPointPings(ping_id, BeeminderDbAdapter.KEY_PID);
		pp.moveToFirst();
		int idx = pp.getColumnIndex(BeeminderDbAdapter.KEY_POINTID);
		while (!pp.isAfterLast()) {
			long ptid = pp.getLong(idx);
			if (LOCAL_LOGV) Log.v(TAG, "findPingPoints: Found point " + ptid + " for ping " + ping_id);
			points.add(ptid);
			pp.moveToNext();
		}
		pp.close();
		return points;
	}

	private long findGoalInPoints(long goal_id, List<Long> points) {
		int idx;
		Cursor ptc;
		long ptgoal;

		// Search for a previously submitted data point for the current goal id
		for (long ptid : points) {
			ptc = mBeeDB.fetchPoint(ptid);
			idx = ptc.getColumnIndex(BeeminderDbAdapter.KEY_GID);
			ptgoal = ptc.getLong(idx);
			ptc.close();
			if (ptgoal == goal_id) {
				if (LOCAL_LOGV) Log.v(TAG, "findGoalInPoints: Found point " + ptid + " for goal " + goal_id);
				return ptid;
			}
		}
		return -1;
	}

	private void newPointForPing(long ping_id, long goal_id) {
		if (LOCAL_LOGV) Log.v(TAG, "newPointForPing: Creating new point for ping " + ping_id + " and goal " + goal_id);

		Cursor ping = mPingDB.fetchPing(ping_id);
		int ping_idx = ping.getColumnIndex(PingsDbAdapter.KEY_PING);
		int period_idx = ping.getColumnIndex(PingsDbAdapter.KEY_PERIOD);
		long time = ping.getLong(ping_idx);
		int period = ping.getInt(period_idx);
		ping.close();
		double value = 1.0 / 60.0 * period;
		String comment = "TagTime ping: " + mNewTagsIn;

		// Initiate creation of a Beeminder point submission. Will block until
		// response with request ID is received
		String req_id = createBeeminderPoint(goal_id, value, time, comment);

		if (req_id != null) {
			long ptid = mBeeDB.createPoint(req_id, value, time, comment, goal_id);
			try {
				mBeeDB.newPointPing(ptid, ping_id);
			} catch (Exception e) {
				Log.w(TAG, "newPointForPing: Could not create pair for point=" + ptid + ", ping=" + ping_id
						+ " for goal " + goal_id);
			}
		} else {
			Log.w(TAG, "newPointForPing: Beeminder submission failed for ping=" + ping_id + " to goal " + goal_id);
		}
	}

	private long mPingId;
	private long mPingTime;
	private String mOldTagsIn;
	private String mNewTagsIn;
	private int mRetries = 0;

	/** Method to handle incoming intents within the worker thread. */
	@Override
	protected void onHandleIntent(Intent intent) {
		if (intent != null) {
			String action = intent.getAction();
			if (LOCAL_LOGV) Log.v(TAG, "onHandleIntent: act=" + action + ", data=" + intent.getDataString());
			if (action == null) {
				Log.w(TAG, "onHandleIntent: No action specified!");
				return;
			}
			if (action.equals(ACTION_EDITPING)) {
				// Ping edited. Retrieve changes in tags and manage datapoints

				if (!TagTime.checkBeeminder()) {
					Log.w(TAG, "onHandleIntent: Beeminder app is not installed. Unlinking goals and aborting.");
					mBeeDB.openDatabase();
					mBeeDB.deleteAllGoals();
					mBeeDB.closeDatabase();
					return;
				}

				mPingId = intent.getLongExtra(KEY_PID, -1);
				mOldTagsIn = intent.getStringExtra(KEY_OLDTAGS);
				mNewTagsIn = intent.getStringExtra(KEY_NEWTAGS);

				mRetries = intent.getIntExtra(KEY_RETRIES, 0);
				mRetries++;

				if (LOCAL_LOGV) {
					Log.v(TAG, "onHandleIntent: =================================================");
					Log.v(TAG, "onHandleIntent: Got ping_id=" + mPingId + ", oldtags=" + mOldTagsIn + ", newtags="
							+ mNewTagsIn + ", attempt=" + mRetries);
				}

				if (mRetries >= MAX_RETRIES) {
					Log.w(TAG, "onHandleIntent: Exceeded maximum retries for ping " + mPingId);
					notifyForResubmit();
					return;
				}
				if (mOldTagsIn == null || mNewTagsIn == null || mPingId < 0) {
					Log.w(TAG, "onHandleIntent: Incomplete intent! ping_id=" + mPingId + ", oldtags=" + mOldTagsIn
							+ ", newtags=" + mNewTagsIn);
					return;
				}

				mBeeDB.openDatabase();
				mPingDB.openDatabase();

				// Find data points that were previously generated by this ping.
				List<Long> points = findPingPoints(mPingId);
				Cursor pc = mPingDB.fetchPing(mPingId);
				if (pc.getCount() == 0) {
					Log.w(TAG, "onHandleIntent: Could not find requested ping with id " + mPingId);
					return;
				}
				int idx = pc.getColumnIndex(PingsDbAdapter.KEY_PING);
				if (idx < 0) {
					Log.w(TAG, "onHandleIntent: Could not retrieve ping time for id " + mPingId);
					return;
				}
				mPingTime = pc.getLong(idx);

				// Find all goals that match the new set of tags
				String[] newtags = mNewTagsIn.trim().split(" ");
				Set<Long> goals = mBeeDB.findGoalsForTagNames(Arrays.asList(newtags));

				// Create new data points for all goals matching the edited ping
				// if they were not found in the database
				for (long gid : goals) {
					// If goal was updated later than the ping, skip this goal
					long updated_at = mBeeDB.getGoalUpdatedAt(gid);
					if (updated_at > mPingTime) {
						if (LOCAL_LOGV) Log.v(TAG, "onHandleIntent: Skipping goal " + gid + " since " + updated_at
								+ ">" + mPingTime);
						continue;
					}

					// If we find an existing data point for this goal among
					// points for this ping. Remove the point from the list
					// since a point is always associated with only a single
					// goal and ping
					long ptid = findGoalInPoints(gid, points);
					if (ptid >= 0) {
						points.remove(ptid);
						continue;
					}

					// Create a new data point for this goal together with a
					// ping pairing
					newPointForPing(mPingId, gid);
				}

				// Remove all points that were left unassociated with any goals
				// that matched the new set of tags.
				for (long ptid : points) {
					if (LOCAL_LOGV) Log.v(TAG, "onHandleIntent: Removing point " + ptid);
					boolean deleted = deleteBeeminderPoint(ptid);
					if (deleted) mBeeDB.removePoint(ptid);
					else if (mRetries >= (MAX_RETRIES - 1) && mLastError == Session.ErrorType.ERROR_NOTFOUND) {
						// We give up on retrying point deletion after a number
						// of retries with NOTFOUND as a result. The last retry
						// will have gone through already
						Log.w(TAG, "onHandleIntent: Giving up on delete for " + mPoint.user + "/" + mPoint.slug);
						mBeeDB.removePoint(ptid);
					}
				}

				mPingDB.closeDatabase();
				mBeeDB.closeDatabase();
			}
		} else {
			Log.w(TAG, "onHandleIntent: No intent received!");
			return;
		}
	}

	public BeeminderService() {
		super(TAG);
	}
}
