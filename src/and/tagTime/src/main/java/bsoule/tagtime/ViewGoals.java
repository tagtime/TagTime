package bsoule.tagtime;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.MenuItem;

public class ViewGoals extends SherlockListActivity {

	private static final int ACTIVITY_EDIT = 0;
	private static final String TAG = "ViewGoals";

	private BeeminderDbAdapter mDbHelper;
	private SimpleDateFormat mSDF;

	private ActionBar mAction;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tagtime_viewgoals);

		mAction = getSupportActionBar();
		mAction.setHomeButtonEnabled(true);
		mAction.setIcon(R.drawable.tagtime_03);

		mSDF = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault());

		mDbHelper = BeeminderDbAdapter.getInstance();
		mDbHelper.openDatabase();
		fillData();

		Button beeminder = (Button) findViewById(R.id.vg_add);
		beeminder.setClickable(true);
		beeminder.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				Intent i = new Intent(ViewGoals.this, EditGoal.class);
				startActivityForResult(i, ACTIVITY_EDIT);
			}
		});
	}

	private void fillData() {
		Cursor goalsCursor = mDbHelper.fetchAllGoals();
		startManagingCursor(goalsCursor);
		// Create an array to specify the fields we want to display in the list
		String[] from = new String[] { BeeminderDbAdapter.KEY_USERNAME, BeeminderDbAdapter.KEY_SLUG,
				BeeminderDbAdapter.KEY_UPDATEDAT, BeeminderDbAdapter.KEY_ROWID };
		// and an array of the fields we want to bind those field to
		int[] to = new int[] { R.id.viewgoals_row_user, R.id.viewgoals_row_slug, R.id.viewgoals_row_time,
				R.id.viewgoals_row_tags };
		// Now create a simple cursor adapter and set it to display
		GoalsCursorAdapter goals = new GoalsCursorAdapter(this, R.layout.tagtime_viewgoals_row, goalsCursor, from, to);
		setListAdapter(goals);
	}

	private class GoalsCursorAdapter extends SimpleCursorAdapter {

		public GoalsCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
			super(context, layout, c, from, to);

			setViewBinder(new GoalsViewBinder());
		}

		public class GoalsViewBinder implements SimpleCursorAdapter.ViewBinder {

			public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
				int userIdx = cursor.getColumnIndex(BeeminderDbAdapter.KEY_USERNAME);
				int slugIdx = cursor.getColumnIndex(BeeminderDbAdapter.KEY_SLUG);
				int timeIdx = cursor.getColumnIndex(BeeminderDbAdapter.KEY_UPDATEDAT);
				if (userIdx == columnIndex || slugIdx == columnIndex) {
					TextView tv = (TextView) view;
					String username = cursor.getString(columnIndex);
					tv.setText(username);
					return true;
				} else if (timeIdx == columnIndex) {
					TextView tv = (TextView) view;
					long updated = cursor.getLong(columnIndex);
					tv.setText("Linked to pings after: "+mSDF.format(new Date(updated * 1000)));
					return true;
				} else {
					// should be tags case
					TextView tv = (TextView) view;
					try {
						String t = mDbHelper.fetchTagString(cursor.getLong(columnIndex));
						tv.setText("Tags: " + t);
						return true;
					} catch (Exception e) {
						Log.e(TAG, "error loading tags for viewlog.");
						Log.e(TAG, e.getMessage());
						tv.setText("error fetching tags!");
						return true;
					}
				}
			}
		}
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		Intent i = new Intent(this, EditGoal.class);
		i.putExtra(PingsDbAdapter.KEY_ROWID, id);
		startActivityForResult(i, ACTIVITY_EDIT);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		// if (intent.getExtras() != null) {
		// Log.v(TAG, intent.getExtras().getString("tags"));
		// }
		fillData();
	}

	@Override
	protected void onDestroy() {
		mDbHelper.closeDatabase();
		super.onDestroy();
	}
	
	/** Handles menu item selections */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case android.R.id.home:
			// app icon in action bar clicked; go home
			Intent intent = new Intent(this, TPController.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
}
