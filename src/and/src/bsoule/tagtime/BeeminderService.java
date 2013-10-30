package bsoule.tagtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import com.beeminder.beedroid.api.Session;
import com.beeminder.beedroid.api.Session.SessionState;

public class BeeminderService extends IntentService {
	private static final String TAG = "BeeminderService";
	private static final boolean LOCAL_LOGV = true && !Timepie.DISABLE_LOGV;

	public static final String ACTION_EDITPING = "editping";

	public static final String KEY_PID = "ping_id";
	public static final String KEY_OLDTAGS = "oldtags";
	public static final String KEY_NEWTAGS = "newtags";

	private BeeminderDbAdapter mBeeDB;
	private PingsDbAdapter mPingDB;
	private Session mBeeminder;

	private final Semaphore mSubmitSem = new Semaphore(0, true);
	private final Semaphore mOpenSem = new Semaphore(0, true);
	private boolean mWaitingOpen = false;

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

	private Point mPoint = new Point();

	private class SessionStatusCallback implements Session.StatusCallback {
		@Override
		public void call(Session session, SessionState state) {
			if (LOCAL_LOGV) Log.v(TAG, "Session Callback: Beeminder status changed:" + state);

			if (state == SessionState.OPENED) {
				if (mWaitingOpen) {
					mOpenSem.release();
					mWaitingOpen = false;
				}
			} else if (state == SessionState.CLOSED_ON_ERROR) {
				if (mWaitingOpen) {
					mOpenSem.release();
					mWaitingOpen = false;
				}
			} else if (state == SessionState.CLOSED) {
				// Nothing here since it is a normal close.
			}

		}
	}

	private class PointSubmissionCallback implements Session.SubmissionCallback {
		@Override
		public void call(Session session, int submission_id, String request_id, String error) {
			if (LOCAL_LOGV) Log.v(TAG, "Point Callback: Point submission completed, id=" + submission_id + ", req_id="
					+ request_id + ", error=" + error);
			if (error == null && submission_id == mPoint.submissionId) {
				mPoint.requestId = request_id;
			} else {
				Log.w(TAG, "Point Callback: Submission error or ID mismatch. msg=" + error);
				mPoint.requestId = null;
			}
			mSubmitSem.release();
		}
	}

	private void initializePointFromGoal(long goal_id) {
		Cursor c = mBeeDB.fetchGoal(goal_id);
		mPoint.requestId = null;
		mPoint.user = c.getString(1);
		mPoint.slug = c.getString(2);
		c.close();
	}

	private void initializePoint(long point_id) {
		Cursor c = mBeeDB.fetchPoint(point_id);
		initializePointFromGoal(c.getLong(5));
		mPoint.requestId = c.getString(1);
		c.close();
	}

	private String createBeeminderPoint(long goal_id, double value, long time, String comment) {
		initializePointFromGoal(goal_id);
		mPoint.value = value;
		mPoint.timestamp = time;
		mPoint.comment = comment;

		if (mBeeminder != null) {
			try {
				if (LOCAL_LOGV) Log.v(TAG, "createBeeminderPoint: Requesting open for " + mPoint.user + "/"
						+ mPoint.slug);

				mWaitingOpen = true;
				mBeeminder.openForGoal(mPoint.user, mPoint.slug);

				if (LOCAL_LOGV) Log.v(TAG, "createBeeminderPoint: Open finished, waiting for open semaphore.");

				mOpenSem.acquire();

				if (LOCAL_LOGV) Log.v(TAG, "createBeeminderPoint: Open semaphore acquired.");

				if (mBeeminder.getState() == Session.SessionState.OPENED) {

					if (LOCAL_LOGV) Log.v(TAG, "createBeeminderPoint: Submitting point.");

					mPoint.submissionId = mBeeminder.createPoint(mPoint.value, mPoint.timestamp, mPoint.comment);

					if (LOCAL_LOGV) Log.v(TAG, "createBeeminderPoint: Submission done, waiting for point semaphore");

					mSubmitSem.acquire();

					if (LOCAL_LOGV) Log.v(TAG, "createBeeminderPoint: Submit semaphore acquired.");
				}

				if (LOCAL_LOGV) Log.v(TAG, "createBeeminderPoint: Closing session.");

				mBeeminder.close();
			} catch (Session.SessionException e) {
				Log.w(TAG, "createBeeminderPoint: Error opening session or submitting point. msg=" + e.getMessage());
			} catch (InterruptedException e) {
				Log.w(TAG, "createBeeminderPoint: interrupted. msg=" + e.getMessage());
			}

		}
		if (mPoint.requestId == null) {
			String msg = "Error updating "+ mPoint.user + "/" + mPoint.slug;
			String submsg = "Goal points may now be inconsistent";
	        Intent intent = new Intent( this, ViewLog.class );
	        PendingIntent ci = PendingIntent.getActivity( this, mPoint.submissionId, intent, 0 );
			NotificationManager nm = (NotificationManager) getSystemService( Context.NOTIFICATION_SERVICE );
			Notification notif = new NotificationCompat.Builder(this)
	         	.setContentTitle(msg)
	         	.setContentText(submsg)
	         	.setSmallIcon(R.drawable.error_ticker)
	         	.setContentIntent(ci)
	         	.build();
	        notif.flags |= Notification.FLAG_AUTO_CANCEL;
			nm.notify(mPoint.submissionId, notif);
		}
		return mPoint.requestId;
	}

	private boolean deleteBeeminderPoint(long point_id) {
		boolean result = false;
		initializePoint(point_id);

		if (mBeeminder != null) {
			try {
				if (LOCAL_LOGV) Log.v(TAG, "deleteBeeminderPoint: Requesting open for " + mPoint.user + "/"
						+ mPoint.slug);

				mWaitingOpen = true;
				mBeeminder.openForGoal(mPoint.user, mPoint.slug);

				if (LOCAL_LOGV) Log.v(TAG, "deleteBeeminderPoint: Open finished, waiting for open semaphore.");

				mOpenSem.acquire();

				if (LOCAL_LOGV) Log.v(TAG, "deleteBeeminderPoint: Open semaphore acquired.");

				if (mBeeminder.getState() == Session.SessionState.OPENED) {

					if (LOCAL_LOGV) Log.v(TAG, "deleteBeeminderPoint: Deleting point.");

					mPoint.submissionId = mBeeminder.deletePoint(mPoint.requestId);

					if (LOCAL_LOGV) Log.v(TAG, "deleteBeeminderPoint: Submission done, waiting for point semaphore");

					mSubmitSem.acquire();

					if (LOCAL_LOGV) Log.v(TAG, "deleteBeeminderPoint: Submit semaphore acquired.");
					if (mPoint.requestId != null) result = true;
				}

				if (LOCAL_LOGV) Log.v(TAG, "deleteBeeminderPoint: Closing session.");

				mBeeminder.close();
			} catch (Session.SessionException e) {
				Log.w(TAG, "deleteBeeminderPoint: Error opening session or deleting point. msg=" + e.getMessage());
			} catch (InterruptedException e) {
				Log.w(TAG, "deleteBeeminderPoint: interrupted. msg=" + e.getMessage());
			}

		}
		if (!result) {
			String msg = "Error updating "+ mPoint.user + "/" + mPoint.slug;
			String submsg = "Goal points may now be inconsistent";
	        Intent intent = new Intent( this, ViewLog.class );
	        PendingIntent ci = PendingIntent.getActivity( this, mPoint.submissionId, intent, 0 );
			NotificationManager nm = (NotificationManager) getSystemService( Context.NOTIFICATION_SERVICE );
			Notification notif = new NotificationCompat.Builder(this)
	         	.setContentTitle(msg)
	         	.setContentText(submsg)
	         	.setSmallIcon(R.drawable.error_ticker)
	         	.setContentIntent(ci)
	         	.build();
	        notif.flags |= Notification.FLAG_AUTO_CANCEL;
			nm.notify(mPoint.submissionId, notif);
		}
		return result;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		if (LOCAL_LOGV) Log.v(TAG, "onCreate: Beeminder service created.");

		mBeeDB = new BeeminderDbAdapter(this);
		mPingDB = new PingsDbAdapter(this);
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

	private Set<Long> findGoalsForTags(String[] tags) {
		Set<Long> goals = new HashSet<Long>(0);
		int idx;
		long tid;
		Cursor c;

		for (String t : tags) {
			if (t.length() == 0) continue;
			tid = mPingDB.getTID(t);
			if (tid < 0) {
				Log.w(TAG, "findGoalsForTags: Could not find tag \"" + t + "\" in the tags database!");
				continue;
			}
			c = mBeeDB.fetchGoalTags(tid, BeeminderDbAdapter.KEY_TID);
			c.moveToFirst();
			idx = c.getColumnIndex(BeeminderDbAdapter.KEY_GID);
			while (!c.isAfterLast()) {
				long gid = c.getLong(idx);
				goals.add(gid);
				c.moveToNext();
			}
			c.close();
		}
		if (LOCAL_LOGV) {
			String goalstr = "";
			for (long gid : goals) {
				goalstr += Long.toString(gid) + " ";
			}
			if (LOCAL_LOGV) Log.v(TAG,
					"findGoalsForTags: Found goals " + goalstr + "for new tags " + TextUtils.join(" ", tags));
		}
		return goals;
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
		int idx = ping.getColumnIndex(PingsDbAdapter.KEY_PING);
		long time = ping.getLong(idx);
		ping.close();
		// TODO: This should depend on the time setting for the ping
		double value = 0.75;
		String comment = "TagTime ping";

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

	@Override
	protected void onHandleIntent(Intent intent) {
		if (intent != null) {
			String action = intent.getAction();
			if (LOCAL_LOGV) Log.v(TAG, "onHandleIntent: Beeminder notification service: " + action);
			if (action == null) {
				Log.w(TAG, "onHandleIntent: No action specified!");
				return;
			}
			if (action.equals(ACTION_EDITPING)) {
				// Ping edited. Retrieve changes in tags and manage datapoints

				long ping_id = intent.getLongExtra(KEY_PID, -1);
				String oldtagsin = intent.getStringExtra(KEY_OLDTAGS);
				String newtagsin = intent.getStringExtra(KEY_NEWTAGS);
				if (oldtagsin == null || newtagsin == null || ping_id < 0) {
					Log.w(TAG, "onHandleIntent: Incomplete intent! ping_id=" + ping_id + ", oldtags=" + oldtagsin
							+ ", newtags=" + newtagsin);
					return;
				}

				if (LOCAL_LOGV) Log.v(TAG, "onHandleIntent: Got ping_id=" + ping_id + ", oldtags=" + oldtagsin
						+ ", newtags=" + newtagsin);

				mBeeDB.open();
				mPingDB.open();

				// Find data points that were previously generated by this ping.
				List<Long> points = findPingPoints(ping_id);

				// Find all goals that match the new set of tags
				String[] newtags = newtagsin.trim().split(" ");
				Set<Long> goals = findGoalsForTags(newtags);

				// Create new data points for all goals matching the edited ping
				// if they were not found in the database
				for (long gid : goals) {
					long ptid = findGoalInPoints(gid, points);

					// If found an existing data point for this goal among
					// points for this ping. Remove the point from the list
					// since a point is always associated with only a single
					// goal and ping
					if (ptid >= 0) {
						points.remove(ptid);
						continue;
					}

					// Create a new data point for this goal together with a
					// ping pairing
					newPointForPing(ping_id, gid);
				}

				// Remove all points that were left unassociated with any goals
				// that matched the new set of tags.
				for (long ptid : points) {
					if (LOCAL_LOGV) Log.v(TAG, "onHandleIntent: Removing point " + ptid);
					boolean deleted = deleteBeeminderPoint(ptid);
					if (deleted) mBeeDB.removePoint(ptid);
				}

				mPingDB.close();
				mBeeDB.close();
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
