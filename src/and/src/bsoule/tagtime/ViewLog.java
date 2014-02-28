package bsoule.tagtime;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;

public class ViewLog extends SherlockFragmentActivity implements LoaderManager.LoaderCallbacks<Cursor> {

	private static final int ACTIVITY_EDIT = 0;
	private static final String TAG = "ViewLog";
	private static final boolean LOCAL_LOGV = true && !TagTime.DISABLE_LOGV;

	private PingsDbAdapter mDbHelper;
	private BeeminderDbAdapter mBeeDb;
	private PingCursorAdapter mPingAdapter;

	private SimpleDateFormat mSDF;
	private Map<Long, String> mTagList = new HashMap<Long, String>();
	private ListView mListView;
	private ProgressBar mProgress;
	private TextView mNoData;

	private ActionBar mAction;

	private void refreshTagList() {
		Cursor c = mDbHelper.fetchAllTags("ROWID");
		mTagList.clear();

		int idxrow = c.getColumnIndex(PingsDbAdapter.KEY_ROWID);
		int idxtag = c.getColumnIndex(PingsDbAdapter.KEY_TAG);

		try {
			c.moveToFirst();
			while (!c.isAfterLast()) {
				mTagList.put(c.getLong(idxrow), c.getString(idxtag));
				c.moveToNext();
			}
		} finally {
			c.close();
		}
	}

	public static final class PingsCursorLoader extends SimpleCursorLoader {

		private PingsDbAdapter mHelper;

		public PingsCursorLoader(Context context, PingsDbAdapter helper) {
			super(context);
			mHelper = helper;
		}

		@Override
		public Cursor loadInBackground() {
			return mHelper.fetchAllPings(true);
		}

	}

	public final class PingCursorAdapter extends CursorAdapter {

		private Context mContext;

		public class ViewHolder {
			TextView pingText;
			TextView tagText;
			TextView yellowBeeText;
			TextView redBeeText;
		}

		public PingCursorAdapter(Context context, Cursor c, int flags) {
			super(context, c, flags);
			mContext = context;
		}

		public PingCursorAdapter(Context context, Cursor c, boolean autoRequery) {
			super(context, c, autoRequery);
			mContext = context;
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			View view = LayoutInflater.from(mContext).inflate(R.layout.tagtime_viewlog_ping_row, parent, false);
			ViewHolder viewHolder = new ViewHolder();
			viewHolder.pingText = (TextView) view.findViewById(R.id.viewlog_row_time);
			viewHolder.tagText = (TextView) view.findViewById(R.id.viewlog_row_tags);
			viewHolder.yellowBeeText = (TextView) view.findViewById(R.id.viewlog_row_beeminder);
			viewHolder.redBeeText = (TextView) view.findViewById(R.id.viewlog_row_beeminder_red);
			view.setTag(viewHolder);
			return view;
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			ViewHolder vh = (ViewHolder) view.getTag();

			// Convert ping time to readable text
			int pingidx = cursor.getColumnIndex(PingsDbAdapter.KEY_PING);
			long pingtime = cursor.getLong(pingidx);
			vh.pingText.setText(mSDF.format(new Date(pingtime * 1000)));

			// Figure out Beeminder submission status and update icons, also
			// setting the tag string
			try {
				Cursor c = mBeeDb.fetchPointPings(cursor.getLong(0), BeeminderDbAdapter.KEY_PID);
				List<Long> tags = mDbHelper.fetchTagsForPing(cursor.getLong(0));
				Set<Long> goals = mBeeDb.findGoalsForTags(tags);
				int numgoals = 0;
				for (long gid : goals) {
					// If goal was updated later than the ping, skip this goal
					long updated_at = mBeeDb.getGoalUpdatedAt(gid);
					if (updated_at < pingtime) numgoals++;
				}

				if (c.getCount() != numgoals) {
					// TODO: We should check whether existing points and
					// goals match exactly instead of just checking the
					// count
					vh.yellowBeeText.setVisibility(View.GONE);
					vh.redBeeText.setVisibility(View.VISIBLE);
				} else if (c.getCount() != 0) {
					vh.yellowBeeText.setVisibility(View.VISIBLE);
					vh.redBeeText.setVisibility(View.GONE);
				} else {
					vh.yellowBeeText.setVisibility(View.GONE);
					vh.redBeeText.setVisibility(View.GONE);
				}
				c.close();
				String tagstr = "";
				for (long tag : tags)
					tagstr = tagstr + " " + mTagList.get(tag);
				vh.tagText.setText(tagstr);
			} catch (Exception e) {}
		}

	}

	// Called when a new Loader needs to be created
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		// Now create and return a CursorLoader that will take care of
		// creating a Cursor for the data being displayed.
		return new PingsCursorLoader(ViewLog.this, mDbHelper);
	}

	// Called when a previously created loader has finished loading
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		// Swap the new cursor in. (The framework will take care of closing the
		// old cursor once we return.)
		mProgress.setVisibility(View.GONE);
		mListView.setEmptyView(mNoData);
		mPingAdapter.swapCursor(data);
	}

	// Called when a previously created loader is reset, making the data
	// unavailable
	public void onLoaderReset(Loader<Cursor> loader) {
		// This is called when the last Cursor provided to onLoadFinished()
		// above is about to be closed. We need to make sure we are no
		// longer using it.
		mPingAdapter.swapCursor(null);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tagtime_viewlog);

		mAction = getSupportActionBar();
		mAction.setHomeButtonEnabled(true);
		mAction.setIcon(R.drawable.tagtime_03);

		mDbHelper = PingsDbAdapter.getInstance();
		mDbHelper.openDatabase();
		mBeeDb = BeeminderDbAdapter.getInstance();
		mBeeDb.openDatabase();

		mSDF = new SimpleDateFormat("yyyy.MM.dd'\n'HH:mm:ss", Locale.getDefault());
		mPingAdapter = new PingCursorAdapter(this, null, true);
		mListView = (ListView) findViewById(R.id.listview);
		mListView.setFastScrollEnabled(true);
		mListView.setAdapter(mPingAdapter);
		mListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View view, int position, long id) {
				Intent i = new Intent(ViewLog.this, EditPing.class);
				i.putExtra(PingsDbAdapter.KEY_ROWID, id);
				startActivityForResult(i, ACTIVITY_EDIT);
			}
		});
		mNoData = (TextView) findViewById(R.id.nodata);
		mProgress = (ProgressBar) findViewById(R.id.progressbar);
		mListView.setEmptyView(mProgress);

		getSupportLoaderManager().initLoader(0, null, this);
		refreshTagList();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		refreshTagList();
		mPingAdapter.notifyDataSetChanged();
	}

	@Override
	protected void onDestroy() {
		mBeeDb.closeDatabase();
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
