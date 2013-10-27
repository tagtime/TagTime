package bsoule.tagtime;

import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.beeminder.beedroid.api.Session;
import com.beeminder.beedroid.api.Session.SessionError;
import com.beeminder.beedroid.api.Session.SessionException;
import com.beeminder.beedroid.api.Session.SessionState;

// ChangeLog:

/* 2013.10.26 Uluc: If the activity is invoked with RowId = null, it will let the user 
 * select a number of tags and send them back to the invking activity in the response. */

public class EditGoal extends Activity {

	private final static String TAG = "EditGoal";
	private static final boolean LOCAL_LOGV = true && !Timepie.DISABLE_LOGV;

	private static final int ACTIVITY_EDIT = 0;

	public static final String KEY_EDIT = "editor";

	private BeeminderDbAdapter mBeeminderDB;

	// private EditText mNotesEdit;
	private Long mRowId;
	private String mUsername = null;
	private String mGoalSlug = null;
	private String mToken = null;
	private String[] mTags;
	private String mTagString = null;

	private TextView mGoalInfo;
	private TextView mTokenInfo;
	private TextView mTagInfo;

	Session mSession;

	private class SessionStatusCallback implements Session.StatusCallback {
		@Override
		public void call(Session session, SessionState state) {
			Log.v(TAG, "Beeminder status changed:" + state);

			if (state == SessionState.OPENED) {
				mToken = session.getToken();
				mUsername = session.getUsername();
				mGoalSlug = session.getGoalSlug();
				Log.v(TAG, "Goal = " + mUsername + "/" + mGoalSlug + ", token=" + session.getToken());

				updateFields();

			} else if (state == SessionState.CLOSED_ON_ERROR) {
				SessionError error = mSession.getError();
				if (error.type == Session.ErrorType.ERROR_UNAUTHORIZED) {
					// clearCurGoal();
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
		mGoalInfo.setText("Goal: none");
		mTokenInfo.setText("Token: none");
	}

	private void updateFields() {
		mGoalInfo.setText("Goal: " + mUsername + "/" + mGoalSlug);
		mTokenInfo.setText("Token: " + mToken);
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
			mTagInfo.setText("Tags: " + mTagString);
			mTags = mTagString.split(" ");
		} catch (Exception e) {
			Log.w(TAG, "Could not fetch tag string for goal!");
		}

		updateFields();
	}

	private void updateGoal() {
		if (mUsername == null)
			return;

		if (mRowId >= 0) {
			// Called on an existing goal, update
			mBeeminderDB.updateGoal(mRowId, mUsername, mGoalSlug, mToken);
			mBeeminderDB.updateGoalTags(mRowId, new ArrayList<String>(Arrays.asList(mTags)));
		} else {
			// No existing goals, attempt to create
			mBeeminderDB.createGoal(mUsername, mGoalSlug, mToken, new ArrayList<String>(Arrays.asList(mTags)));
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tagtime_editgoal);

		mRowId = getIntent().getLongExtra(PingsDbAdapter.KEY_ROWID, -1);
		mBeeminderDB = new BeeminderDbAdapter(this);
		mBeeminderDB.open();

		mGoalInfo = (TextView) findViewById(R.id.goalinfo);
		mTokenInfo = (TextView) findViewById(R.id.token);
		mTagInfo = (TextView) findViewById(R.id.tags);

		mTags = new String[0];
		mTagString = "";

		if (mRowId >= 0) {
			Cursor goal = mBeeminderDB.fetchGoal(mRowId);
			updateWithGoal(goal);
			goal.close();
		} else {
			mGoalInfo.setText("Goal: not selected");
			mTokenInfo.setText("Token: none");

		}

		try {
			mSession = new Session(this);
			mSession.setStatusCallback(new SessionStatusCallback());
		} catch (SessionException e) {
			Log.v(TAG, "Exception creating session: " + e.getMessage());
			mSession = null;
		}

		Button select = (Button) findViewById(R.id.select);
		if (mSession == null) {
			select.setEnabled(false);
		} else {
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

		Button selecttags = (Button) findViewById(R.id.select_tags);
		selecttags.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Intent i = new Intent(EditGoal.this, EditPing.class);
				i.putExtra("tags", mTagString);
				startActivityForResult(i, ACTIVITY_EDIT);
			}
		});

		Button confirm = (Button) findViewById(R.id.confirm);
		if (mRowId < 0)
			confirm.setText(getText(R.string.editgoal_create));
		else		
			confirm.setText(getText(R.string.editgoal_confirm));
		confirm.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (mUsername == null) {
					Toast.makeText(EditGoal.this, getText(R.string.editgoal_nogoal), Toast.LENGTH_SHORT).show();
					return;
				}
				if (mTags.length == 0) {
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
		if (mRowId < 0)
			delete.setVisibility(View.GONE);
		else
			delete.setOnClickListener(new OnClickListener() {
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
		if (LOCAL_LOGV)
			Log.i(TAG, "Was paused...");
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (LOCAL_LOGV)
			Log.i(TAG, "Resuming...");
	}

	private void saveState() {
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);

		if (requestCode == ACTIVITY_EDIT) {
			if (intent != null && intent.getExtras() != null) {
				mTagString = intent.getExtras().getString("tags").trim();
				mTags = mTagString.split(" ");
				if (LOCAL_LOGV)
					Log.v(TAG, mTags.length + " tags:" + mTagString);
				mTagInfo.setText("Tags: " + mTagString);
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
		if (mSession != null)
			mSession.close();
		mBeeminderDB.close();
		super.onDestroy();
	}
}
