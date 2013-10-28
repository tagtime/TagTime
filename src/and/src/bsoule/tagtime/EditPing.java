package bsoule.tagtime;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/* 2013.10.26 Uluc: If the activity is invoked with RowId = null, it will let the user 
 * select a number of tags and send them back to the invoking activity in the response. */

/* @formatter:off
 * UI flow for the EditPing Activity:
 * 
 * Three possible entries: 
 * 1. Pin clicked (rowId >=0),
 * 2. Goal tag edit (rowId = -1),
 * 3. Orientation change
 * 
 * First type of entry retrieves the initial tag selection from the database, and all 
 * subsequent updates to the tag list are immediately reflected in the database.
 * 
 * In landscape mode, the EditText field is present. Its contents are read when the
 * confirmation button is pressed, or when saveState() is called on orientation change 
 * 
 * @formatter:on
 */
public class EditPing extends Activity {

	private final static String TAG = "EditPing";
	private static final boolean LOCAL_LOGV = true && !Timepie.DISABLE_LOGV;

	public static final String KEY_TAGS = "tags";

	private PingsDbAdapter mPingsDB;
	private EditText mTagsEdit = null;
	private TextView mPingTitle;
	private Long mRowId;
	private Long mPingUTC;
	private LinearLayout tagParent;
	private Cursor mTagsCursor;
	private Cursor mTaggings;
	private int FIXTAGS = R.layout.tagtime_editping;
	private ViewTreeObserver mVto;

	private boolean landscape;
	private List<String> mCurrentTags;
	private String mCurrentTagString = "";

	public static int LAUNCH_VIEW = 0;
	public static int LAUNCH_NOTE = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if (LOCAL_LOGV) Log.v(TAG, "onCreate()");
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tagtime_editping);

		// Hack to figure out whether we are in landscape or portrait mode
		View v = findViewById(R.id.tags_editText);
		if (v == null) {
			landscape = false;
			tagParent = (LinearLayout) findViewById(R.id.lintags);
			mVto = tagParent.getViewTreeObserver();
		} else {
			landscape = true;
			mTagsEdit = (EditText) v;
		}
		mPingTitle = (TextView) findViewById(R.id.pingtime);

		// This is the sort ordering preference for the tag list
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String ordering = prefs.getString("sortOrderPref", "FREQ");

		// cancel the notification
		// TODO: only cancel note if it is for same ping as we are editing
		// TODO: We could make the notification auto-cancelling when clicked.
		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.cancel(R.layout.tagtime_editping);

		// If rowId is supplied, that means we are editing a ping
		mRowId = getIntent().getLongExtra(PingsDbAdapter.KEY_ROWID, -1);

		if (LOCAL_LOGV) Log.w(TAG, "Getting Tags with order: " + ordering);
		mPingsDB = new PingsDbAdapter(this);
		mPingsDB.open();
		mTagsCursor = mPingsDB.fetchAllTags(ordering);
		startManagingCursor(mTagsCursor);
		if (mRowId >= 0) {
			// Valid row ID, this is a ping edit
			mTaggings = mPingsDB.fetchTaggings(mRowId, PingsDbAdapter.KEY_PID);
			startManagingCursor(mTaggings);
		} else {
			// No row id supplied, this is just tag selection
			mTaggings = null;

			String savedtags = null;
			if (savedInstanceState != null) {
				// Check for previously saved tag list to handle orientation
				// change
				savedtags = savedInstanceState.getString("editping_tagsave").trim();
			} else {
				// Otherwise, look for tag information in the incoming intent
				Bundle extras = getIntent().getExtras();
				if (extras != null) savedtags = extras.getString(KEY_TAGS).trim();
			}
			if (savedtags != null) {
				mCurrentTags = new ArrayList<String>(Arrays.asList(savedtags.split("\\s+")));
				mCurrentTagString = TextUtils.join(" ", mCurrentTags);
			}
		}

		// Set confirm button behaviour
		Button confirm = (Button) findViewById(R.id.confirm);
		confirm.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (landscape) {
					String[] newtagstrings = mTagsEdit.getText().toString().trim().split("\\s+");
					mCurrentTags = new ArrayList<String>(Arrays.asList(newtagstrings));
					mCurrentTagString = TextUtils.join(" ", mCurrentTags);
				}
				finish();
			}
		});
	}

	/*
	 * This method sets the initial title and list of tags. Called from
	 * onResume()
	 */
	private void populateFields() {
		if (mRowId >= 0) {
			if (LOCAL_LOGV) Log.v(TAG, "populateFields: Editing ping in DB");

			Cursor ping = mPingsDB.fetchPing(mRowId);
			startManagingCursor(ping);
			try {
				// set ping time title
				mPingUTC = ping.getLong(ping.getColumnIndexOrThrow(PingsDbAdapter.KEY_PING));
				SimpleDateFormat SDF = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault());
				mPingTitle.setText(SDF.format(new Date(mPingUTC * 1000)));

				// get tags from the database
				mCurrentTags = mPingsDB.fetchTagsForPing(mRowId);
				mCurrentTagString = TextUtils.join(" ", mCurrentTags);
			} catch (Exception e) {
				Log.i(TAG, "caught an exception in populateFields():");
				Log.i(TAG, "    " + e.getLocalizedMessage());
				Log.i(TAG, "    " + e.getMessage());
			}
		} else {
			if (LOCAL_LOGV) Log.v(TAG, "populateFields: ROWID was null, only selecting tags");

			mPingTitle.setText(getText(R.string.editping_selecttags));
			if (mCurrentTags == null) mCurrentTags = new ArrayList<String>();
			mCurrentTagString = TextUtils.join(" ", mCurrentTags);
		}

		if (!landscape) {
			refreshTags();
		} else {
			if (mCurrentTags != null) mTagsEdit.setText(TextUtils.join(" ", mCurrentTags));
		}
	}

	/**
	 * This method refreshes the list of tag buttons based on the contents of
	 * the tag database.
	 */
	private void refreshTags() {
		tagParent.removeAllViews();
		mTagsCursor.moveToFirst();

		LinearLayout ll = new LinearLayout(this);
		ll.setId(FIXTAGS);
		ll.setOrientation(1);
		ll.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
		while (!mTagsCursor.isAfterLast()) {
			String tag = mTagsCursor.getString(mTagsCursor.getColumnIndex(PingsDbAdapter.KEY_TAG));
			long id = mTagsCursor.getLong(mTagsCursor.getColumnIndex(PingsDbAdapter.KEY_ROWID));
			boolean on = false;
			if (mCurrentTags != null) on = mCurrentTags.contains(tag);
			TagToggle tog = new TagToggle(this, tag, id, on);
			tog.setOnClickListener(mTogListener);
			ll.addView(tog);

			mTagsCursor.moveToNext();
		}
		tagParent.addView(ll);
		mVto.addOnPreDrawListener(mDrawListener);
	}

	/**
	 * This method fixes the layout of the tag buttons to make them fit into the
	 * screen width
	 */
	private void fixTags() {
		LinearLayout ll = (LinearLayout) tagParent.getChildAt(0);
		tagParent.removeAllViews();
		int pwidth = tagParent.getWidth() - 15;
		int twidth = 0;

		LinearLayout tagrow = new LinearLayout(this);
		tagrow.setOrientation(0);
		tagrow.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		while (ll.getChildCount() > 0) {
			View tog = ll.getChildAt(0);
			ll.removeViewAt(0);
			if ((twidth + tog.getWidth()) > pwidth) {
				tagParent.addView(tagrow);
				tagrow = new LinearLayout(this);// .removeAllViews();
				tagrow.setOrientation(0);
				tagrow.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
				twidth = 0;
			}
			tagrow.addView(tog);
			twidth += tog.getWidth();
		}
		tagParent.addView(tagrow);
	}

	@Override
	protected void onPause() {
		if (LOCAL_LOGV) Log.i(TAG, "onPause()");
		super.onPause();
		saveState();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (findViewById(R.id.tags_editText) == null) {
			landscape = false;
			tagParent = (LinearLayout) findViewById(R.id.lintags);
			mVto = tagParent.getViewTreeObserver();
			if (LOCAL_LOGV) Log.v(TAG, "onResume: PORTRAIT");
		} else {
			landscape = true;
			if (LOCAL_LOGV) Log.v(TAG, "onResume: LANDSCAPE");
		}
		populateFields();
	}

	/** Called from onPause(), this method saves the current tag selection into the database, 
	 * or updates the outgoing intent to include current tag selection
	 */
	private void saveState() {
		if (LOCAL_LOGV) Log.v(TAG, "saveState()");
		
		if (landscape) {
			String[] newtagstrings = mTagsEdit.getText().toString().trim().split("\\s+");
			mCurrentTags = new ArrayList<String>(Arrays.asList(newtagstrings));
			mCurrentTagString = TextUtils.join(" ", mCurrentTags);
			if (mRowId >= 0) mPingsDB.updateTaggings(mRowId, Arrays.asList(newtagstrings));
			else {
				for (String t : mCurrentTags) {
					if (t.trim().length() == 0) continue;

					if (LOCAL_LOGV) Log.v(TAG, "saveState: Storing tag \"" + t + "\"");
					mPingsDB.getOrMakeNewTID(t);
				}
			}
		} else {
			if (mRowId >= 0) {
				mPingsDB.updateTaggings(mRowId, mCurrentTags);
			} else {
				// Nothing, onSaveInstanceState will take care of saving the
				// current tags for orientation change
			}
		}
	}

	@Override
	public void finish() {
		if (LOCAL_LOGV) Log.i(TAG, "finish()");
		// Update result intent
		Intent resultIntent = new Intent();
		resultIntent.putExtra(KEY_TAGS, mCurrentTagString);
		setResult(RESULT_OK, resultIntent);
		
		// Submit datapoint associated with the ping
		if (mRowId >= 0 ){
			Intent intent = new Intent(this, BeeminderService.class);
			intent.putExtra(BeeminderDbAdapter.KEY_ROWID, mRowId);
			this.startService(intent);
		}
		
		super.finish();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mCurrentTags != null && mRowId < 0) {
			mCurrentTagString = TextUtils.join(" ", mCurrentTags);
			outState.putString("editping_tagsave", mCurrentTagString);
		}
	}

	@Override
	protected void onDestroy() {

		mPingsDB.close();
		super.onDestroy();
	}

	private OnPreDrawListener mDrawListener = new OnPreDrawListener() {
		public boolean onPreDraw() {
			fixTags();
			ViewTreeObserver vto = tagParent.getViewTreeObserver();
			vto.removeOnPreDrawListener(mDrawListener);
			return true;
		}
	};

	private OnClickListener mTogListener = new OnClickListener() {
		public void onClick(View v) {
			TagToggle tog = (TagToggle) v;
			String tag = tog.getText().toString();
			if (tog.isSelected()) {
				if (LOCAL_LOGV) Log.v(TAG, "OnClickListener: Toggling " + tag);
				mCurrentTags.add(tag);
			} else {
				mCurrentTags.remove(tag);
			}
			mCurrentTagString = TextUtils.join(" ", mCurrentTags);
		}

	};

	@Override
	public void onStop(){
		if (LOCAL_LOGV) Log.v(TAG, "onStop()");
		super.onStop();
	}
}
