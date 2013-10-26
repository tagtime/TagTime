package bsoule.tagtime;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class ViewLog extends ListActivity {


	private static final int ACTIVITY_EDIT=0;
	private static final String TAG = "ViewLog";
	
	private PingsDbAdapter mDbHelper;
	private SimpleDateFormat mSDF;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tagtime_viewlog);
		mDbHelper = new PingsDbAdapter(this);
		mDbHelper.open();
		mSDF = new SimpleDateFormat("yyyy.MM.dd'\n'HH:mm:ss", Locale.getDefault());
		fillData();
	}

	private void fillData() {
		Cursor pingsCursor = mDbHelper.fetchAllPings(true);
		startManagingCursor(pingsCursor);
		// Create an array to specify the fields we want to display in the list
		String[] from = new String[]{PingsDbAdapter.KEY_PING, PingsDbAdapter.KEY_ROWID};
		// and an array of the fields we want to bind those field to
		int[] to = new int[]{R.id.viewlog_row_time, R.id.viewlog_row_tags};
		// Now create a simple cursor adapter and set it to display
		LogCursorAdapter pings =
			new LogCursorAdapter(this, R.layout.tagtime_viewlog_ping_row,
					pingsCursor, from, to);
		setListAdapter(pings);
	}

	private class LogCursorAdapter extends SimpleCursorAdapter {

		public LogCursorAdapter(Context context, int layout, Cursor c,
				String[] from, int[] to) {
			super(context, layout, c, from, to);

			setViewBinder(new LogViewBinder());
		}

		public class LogViewBinder implements 
		SimpleCursorAdapter.ViewBinder {

			public boolean setViewValue(View view, 
					Cursor cursor, int columnIndex) {
				int pingIdx = cursor.getColumnIndex(PingsDbAdapter.KEY_PING);
				if (pingIdx==columnIndex) { // if colidx given is the pingidx
					TextView tv = (TextView)view;
					long pingtime = cursor.getLong(columnIndex);
					tv.setText(mSDF.format(new Date(pingtime*1000)));
					return true;
				} else { // should be tags case
					TextView tv = (TextView)view;
					try {
						//ArrayList<String> tags = mDbHelper.getTagsAsList(PingsDbAdapter.KEY_ROWID,columnIndex);
						String t = mDbHelper.fetchTagString(cursor.getLong(columnIndex));
						tv.setText(t);
						return true;
					} catch (Exception e) {
						Log.e(TAG,"error loading tags for viewlog.");
						Log.e(TAG,e.getMessage());
						tv.setText("");
						return false;
					}
				}
			}
		}
	}
	

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		Intent i = new Intent(this, EditPing.class);
		i.putExtra(PingsDbAdapter.KEY_ROWID, id);
		i.putExtra("editorFlag", true);
		startActivityForResult(i, ACTIVITY_EDIT);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		if (intent.getExtras() != null) {
			Log.v(TAG, intent.getExtras().getString("tags"));
		}
		fillData();
	}

	@Override
	protected void onDestroy() {
		mDbHelper.close();
		super.onDestroy();
	}
}
