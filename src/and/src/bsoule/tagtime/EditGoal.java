package bsoule.tagtime;

import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.beeminder.beedroid.api.Session;
import com.beeminder.beedroid.api.Session.SessionError;
import com.beeminder.beedroid.api.Session.SessionException;
import com.beeminder.beedroid.api.Session.SessionState;

// ChangeLog:

/* 2013.10.26 Uluc: If the activity is invoked with RowId = null, it will let the user 
 * select a number of tags and send them back to the invoking activity in the response. */

public class EditGoal extends SherlockActivity {

	private final static String TAG = "EditGoal";
	private static final boolean LOCAL_LOGV = false && !TagTime.DISABLE_LOGV;

	private static final int ACTIVITY_EDIT = 0;

	public static final String KEY_EDIT = "editor";

	private BeeminderDbAdapter mBeeminderDB;

	public static final String KEY_USERNAME = "user";
	public static final String KEY_GOALNAME = "goal";

	// private EditText mNotesEdit;
	private Long mRowId;
	private String mUsername = null;
	private String mGoalSlug = null;
	private String mToken = null;
	private String[] mTags;
	private String mTagString = null;
	private boolean mChanged = false;

	private TextView mGoalInfo;
	private TextView mTagInfo;
	private Button mVisitGoal;
	
	Session mSession;

	private void notifyVersionError(String submsg) {
		String msg = "Error opening Beeminder session.";
		Intent intent = new Intent(this, TPController.class);
		PendingIntent ci = PendingIntent.getActivity(this, 0, intent, 0);
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Notification notif = new NotificationCompat.Builder(this).setContentTitle(msg).setContentText(submsg)
				.setSmallIcon(R.drawable.error_ticker).setContentIntent(ci).build();
		notif.flags |= Notification.FLAG_AUTO_CANCEL;
		nm.notify(0, notif);
	}

	private class SessionStatusCallback implements Session.StatusCallback {
		@Override
		public void call(Session session, SessionState state, SessionError error) {
			Log.v(TAG, "Beeminder status changed:" + state);

			if (state == SessionState.OPENED) {
				if (mUsername != null && !mUsername.equals(session.getUsername())) mChanged = true;
				if (mGoalSlug != null && !mGoalSlug.equals(session.getGoalSlug())) mChanged = true;
				if (mToken != null && !mToken.equals(session.getToken())) mChanged = true;
				mUsername = session.getUsername();
				mGoalSlug = session.getGoalSlug();
				mToken = session.getToken();
				Log.v(TAG, "Goal = " + mUsername + "/" + mGoalSlug + ", token=" + session.getToken());

				updateFields();

			} else if (state == SessionState.CLOSED_ON_ERROR) {
				if (error.type == Session.ErrorType.ERROR_UNAUTHORIZED) {
					// clearCurGoal();
				}
				if (error.type == Session.ErrorType.ERROR_BADVERSION) {
					Toast.makeText(EditGoal.this, "Protocol error: " + error.message, Toast.LENGTH_LONG).show();
					notifyVersionError(session.getError().message);
				}
				resetFields();
			} else if (state == SessionState.CLOSED) {
				// resetFields();
			}
		}
	}

	private void resetFields() {
		mToken = null;
		mUsername = null;
		mGoalSlug = null;
		mGoalInfo.setText("not selected");
		mTagInfo.setText("not selected");
		mVisitGoal.setVisibility(View.GONE);
	}

	private void updateFields() {
		mGoalInfo.setText(mUsername + "/" + mGoalSlug);
		if (mUsername != null && mGoalSlug != null) {
			mVisitGoal.setVisibility(View.VISIBLE);
		}
	}

	private void updateWithGoal(Cursor goal) {
		int goalIdx = goal.getColumnIndex(BeeminderDbAdapter.KEY_ROWID);
		int userIdx = goal.getColumnIndex(BeeminderDbAdapter.KEY_USERNAME);
		int slugIdx = goal.getColumnIndex(BeeminderDbAdapter.KEY_SLUG);
		int tokenIdx = goal.getColumnIndex(BeeminderDbAdapter.KEY_TOKEN);
		long goal_id = goal.getLong(goalIdx);
		mUsername = goal.getString(userIdx);
		mGoalSlug = goal.getString(slugIdx);
		mToken = goal.getString(tokenIdx);
		try {
			mTagString = mBeeminderDB.fetchTagString(goal_id).trim();
			mTagInfo.setText(mTagString);
			mTags = mTagString.split(" ");
		} catch (Exception e) {
			Log.w(TAG, "Could not fetch tag string for goal!");
		}

		updateFields();
	}

	private void updateGoal() {
		if (mUsername == null || !mChanged) return;

		if (mRowId >= 0) {
			// Called on an existing goal, update
			mBeeminderDB.updateGoal(mRowId, mUsername, mGoalSlug, mToken);
			mBeeminderDB.updateGoalTags(mRowId, new ArrayList<String>(Arrays.asList(mTags)));
		} else {
			// No existing goals, attempt to create
			mBeeminderDB.createGoal(mUsername, mGoalSlug, mToken, new ArrayList<String>(Arrays.asList(mTags)));
		}
	}

	ActionBar mAction;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tagtime_editgoal);

		mAction = getSupportActionBar();
		mAction.setHomeButtonEnabled(true);
		mAction.setDisplayHomeAsUpEnabled(true);
		mAction.setIcon(R.drawable.tagtime_03);

		mRowId = getIntent().getLongExtra(PingsDbAdapter.KEY_ROWID, -1);
		mBeeminderDB = BeeminderDbAdapter.getInstance();
		mBeeminderDB.openDatabase();

		mGoalInfo = (TextView) findViewById(R.id.goalinfo);
		mTagInfo = (TextView) findViewById(R.id.tags);

		mTags = new String[0];
		mTagString = "";

		mVisitGoal = (Button) findViewById(R.id.visit);

		if (mRowId >= 0) {
			Cursor goal = mBeeminderDB.fetchGoal(mRowId);
			updateWithGoal(goal);
			goal.close();
		} else {
			mGoalInfo.setText("not selected");
			mTagInfo.setText("not selected");
		}

		try {
			mSession = new Session(this);
			mSession.setStatusCallback(new SessionStatusCallback());
		} catch (SessionException e) {
			Log.v(TAG, "Exception creating session: " + e.getMessage());
			mSession = null;
		}

		Button select = (Button) findViewById(R.id.select);
		if (mRowId >= 0) {
			mVisitGoal.setVisibility(View.VISIBLE);
			select.setVisibility(View.GONE);
		} else if (mSession == null) {
			mVisitGoal.setVisibility(View.GONE);
			select.setEnabled(false);
		} else {
			mVisitGoal.setVisibility(View.GONE);
			select.setEnabled(true);
			select.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					try {
						mSession.close();
						mSession.openForNewGoal();
					} catch (SessionException e) {
						Log.v(TAG, "Exception opening session: " + e.getMessage());
					}
				}
			});
		}

		mVisitGoal.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				try {
					if (mSession != null && mUsername != null && mGoalSlug != null) mSession.visitGoal(mUsername,
							mGoalSlug);
				} catch (SessionException e) {
					Log.v(TAG, "Exception visiting goal: " + e.getMessage());
				}
			}
		});

		Button selecttags = (Button) findViewById(R.id.select_tags);
		selecttags.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Intent i = new Intent(EditGoal.this, EditPing.class);
				i.putExtra(EditPing.KEY_TAGS, mTagString);
				startActivityForResult(i, ACTIVITY_EDIT);
			}
		});

		Button confirm = (Button) findViewById(R.id.confirm);
		if (mRowId < 0) confirm.setText(getText(R.string.editgoal_create));
		else confirm.setText(getText(R.string.editgoal_confirm));
		confirm.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (mUsername == null) {
					Toast.makeText(EditGoal.this, getText(R.string.editgoal_nogoal), Toast.LENGTH_SHORT).show();
					return;
				}
				if (mTagString.length() == 0) {
					Toast.makeText(EditGoal.this, getText(R.string.editgoal_notags), Toast.LENGTH_SHORT).show();
					return;
				}
				updateGoal();
				Intent resultIntent = new Intent();
				setResult(RESULT_OK, resultIntent);
				finish();
			}
		});

		Button delete = (Button) findViewById(R.id.delete);
		if (mRowId < 0) delete.setVisibility(View.GONE);
		else delete.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (mUsername == null) {
					Toast.makeText(EditGoal.this, getText(R.string.editgoal_nogoal), Toast.LENGTH_SHORT).show();
					return;
				}

				mBeeminderDB.deleteGoal(mRowId);
				mRowId = -1L;
				Intent resultIntent = new Intent();
				setResult(RESULT_OK, resultIntent);
				finish();
			}
		});
	}

	@Override
	protected void onPause() {
		super.onPause();
		saveState();
		if (LOCAL_LOGV) Log.i(TAG, "Was paused...");
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (LOCAL_LOGV) Log.i(TAG, "Resuming...");
	}

	private void saveState() {
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);

		if (requestCode == ACTIVITY_EDIT) {
			if (intent != null && intent.getExtras() != null) {
				String newtags = intent.getExtras().getString(EditPing.KEY_TAGS).trim();
				if (!newtags.equals(mTagString)) mChanged = true;
				mTagString = newtags;
				mTags = mTagString.split(" ");
				if (LOCAL_LOGV) Log.v(TAG, mTags.length + " tags:" + mTagString);
				mTagInfo.setText(mTagString);
			}
		} else {
			mSession.onActivityResult(requestCode, resultCode, intent);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onDestroy() {
		if (mSession != null) mSession.close();
		mBeeminderDB.closeDatabase();
		super.onDestroy();
	}

	/** Handles menu item selections */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case android.R.id.home:
			// app icon in action bar clicked; go home
			Intent intent = new Intent(this, ViewGoals.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
}
