package bsoule.tagtime;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;

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
public class EditPing extends SherlockActivity {

	private final static String TAG = "EditPing";
	private static final boolean LOCAL_LOGV = true && !TagTime.DISABLE_LOGV;

	public static final String KEY_TAGS = "tags";

	private PingsDbAdapter mPingsDB;
	private Cursor mTagsCursor;

	private Button mModeButton = null;
	private ScrollView mTagScroll = null;
	private LinearLayout mTagParent;
	private EditText mTagsEdit = null;
	private TextView mEditTitle = null;
	private TextView mPingTitle;
	private TextView mPingGap;

	private Long mRowId;
	private int mGap;
	private Long mPingUTC;
	private int FIXTAGS = R.layout.tagtime_editping;
	private ViewTreeObserver mVto;

	private boolean landscape;
	private boolean editmode; // false: tags, true: edittext

	private List<String> mCurrentTags;
	private String mCurrentTagString = "";

	private String mOrdering;

	private void showSoftKeyboard() {
		if (getCurrentFocus() != null && getCurrentFocus() instanceof EditText) {
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm == null || mTagsEdit == null) return;
			imm.showSoftInput(mTagsEdit, 0);
		}
	}

	private void hideSoftKeyboard() {
		if (getCurrentFocus() != null && getCurrentFocus() instanceof EditText) {
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm == null || mTagsEdit == null) return;
			imm.hideSoftInputFromWindow(mTagsEdit.getWindowToken(), 0);
		}
	}

	private void setEditMode() {
		if (landscape) return;

		if (editmode) {
			mTagScroll.setVisibility(View.GONE);
			mTagsEdit.setVisibility(View.VISIBLE);
			if (mModeButton != null) mModeButton.setText(getText(R.string.editping_buttons));
			mEditTitle.setText(getText(R.string.editping_tags_land));
			mTagsEdit.requestFocus();
			// showSoftKeyboard();
		} else {
			hideSoftKeyboard();
			mTagScroll.setVisibility(View.VISIBLE);
			mTagsEdit.setVisibility(View.GONE);
			if (mModeButton != null) mModeButton.setText(getText(R.string.editping_keyboard));
			mEditTitle.setText(getText(R.string.editping_tags_port));
		}
	}

	ActionBar mAction;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if (LOCAL_LOGV) Log.v(TAG, "onCreate()");

		super.onCreate(savedInstanceState);
		setContentView(R.layout.tagtime_editping);

		mAction = getSupportActionBar();
		mAction.setHomeButtonEnabled(true);
		mAction.setDisplayHomeAsUpEnabled(true);
		mAction.setIcon(R.drawable.tagtime_03);

		// If rowId is supplied, that means we are editing a ping
		mRowId = getIntent().getLongExtra(PingsDbAdapter.KEY_ROWID, -1);

		// Hack to figure out whether we are in landscape or portrait mode
		View v = findViewById(R.id.editping_tagedit_landscape);
		if (v == null) {
			landscape = false;
			mTagScroll = (ScrollView) findViewById(R.id.editping_tagselect);
			mTagsEdit = (EditText) findViewById(R.id.editping_tagedit_portrait);

			mTagParent = (LinearLayout) findViewById(R.id.lintags);
			mVto = mTagParent.getViewTreeObserver();
		} else {
			landscape = true;
			mTagsEdit = (EditText) v;
		}

		// cancel the notification
		// TODO: only cancel note if it is for same ping as we are editing
		// TODO: We could make the notification auto-cancelling when clicked.
		// NotificationManager nm = (NotificationManager)
		// getSystemService(NOTIFICATION_SERVICE);
		// nm.cancel(R.layout.tagtime_editping);

		mPingsDB = PingsDbAdapter.getInstance();
		mPingsDB.openDatabase();
		if (mRowId >= 0 && mPingsDB.fetchPing(mRowId).getCount() == 0) {
			Toast.makeText(this, getText(R.string.editping_noping), Toast.LENGTH_SHORT).show();
			finish();
			mPingsDB.closeDatabase();
			return;
		}

		Button prevButton = (Button) findViewById(R.id.prev);
		Button nextButton = (Button) findViewById(R.id.next);
		mPingTitle = (TextView) findViewById(R.id.pingtime);
		mPingGap = (TextView) findViewById(R.id.pinggap);
		mEditTitle = (TextView) findViewById(R.id.editping_title);
		if (mRowId < 0) {
			prevButton.setVisibility(View.GONE);
			nextButton.setVisibility(View.GONE);
			mPingTitle.setVisibility(View.GONE);
			mEditTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
		} else {
			if (mPingsDB.fetchPing(mRowId + 1).getCount() == 0) nextButton.setVisibility(View.INVISIBLE);
			if (mPingsDB.fetchPing(mRowId - 1).getCount() == 0) prevButton.setVisibility(View.INVISIBLE);
		}

		// This is the sort ordering preference for the tag list
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		mOrdering = prefs.getString("sortOrderPref", "FREQ");

		if (LOCAL_LOGV) Log.w(TAG, "Getting Tags with order: " + mOrdering);

		mTagsCursor = mPingsDB.fetchAllTags(mOrdering);
		startManagingCursor(mTagsCursor);

		String savedtags = null;
		if (savedInstanceState != null) {
			// Check for previously saved tag list to handle orientation change
			savedtags = savedInstanceState.getString("editping_tagsave").trim();
			editmode = savedInstanceState.getBoolean("editping_editmode");
		} else {
			// Otherwise, look for tag information in the incoming intent
			Bundle extras = getIntent().getExtras();
			if (extras != null && extras.containsKey(KEY_TAGS)) savedtags = extras.getString(KEY_TAGS).trim();
			editmode = false;
		}
		if (savedtags != null) {
			mCurrentTags = new ArrayList<String>(Arrays.asList(savedtags.split("\\s+")));
			mCurrentTagString = TextUtils.join(" ", mCurrentTags);
		}

		// Set confirm button behaviour
		Button confirm = (Button) findViewById(R.id.confirm);
		confirm.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (landscape || editmode) {
					String[] newtagstrings = mTagsEdit.getText().toString().trim().split("\\s+");
					mCurrentTags = new ArrayList<String>(Arrays.asList(newtagstrings));
					mCurrentTagString = TextUtils.join(" ", mCurrentTags);
				}
				finish();
			}
		});

		// Set switch button behaviour
		mModeButton = (Button) findViewById(R.id.editping_switch);
		if (mModeButton != null) {
			mModeButton.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					saveState();
					mTagsCursor = mPingsDB.fetchAllTags(mOrdering);
					startManagingCursor(mTagsCursor);
					editmode = !editmode;
					setEditMode();
					populateFields();
				}
			});
		}
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
				mGap = ping.getInt(ping.getColumnIndexOrThrow(PingsDbAdapter.KEY_PERIOD));
				SimpleDateFormat SDF = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault());
				mPingTitle.setText(SDF.format(new Date(mPingUTC * 1000)));
				if (mGap != 0) {
					mPingGap.setText(getText(R.string.editping_gap).toString()
							.replaceAll("mmm", Integer.toString(mGap)));
				} else {
					mPingGap.setText(getText(R.string.editping_nogap));
				}
				
				// get tags from the database
				mCurrentTags = mPingsDB.fetchTagNamesForPing(mRowId);
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

		if (!landscape && !editmode) {
			refreshTags();
		}
		if (mCurrentTags != null) {
			mTagsEdit.setText(TextUtils.join(" ", mCurrentTags) + " ");
			mTagsEdit.setSelection(mTagsEdit.length(), mTagsEdit.length());
		}
	}

	/**
	 * This method refreshes the list of tag buttons based on the contents of
	 * the tag database.
	 */
	private void refreshTags() {
		mTagParent.removeAllViews();
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
		mTagParent.addView(ll);
		mVto = mTagParent.getViewTreeObserver();
		mVto.addOnPreDrawListener(mDrawListener);
	}

	/**
	 * This method fixes the layout of the tag buttons to make them fit into the
	 * screen width
	 */
	private void fixTags() {
		LinearLayout ll = (LinearLayout) mTagParent.getChildAt(0);
		mTagParent.removeAllViews();
		int pwidth = mTagParent.getWidth() - 15;
		int twidth = 0;
		LinearLayout tagrow = new LinearLayout(this);
		tagrow.setOrientation(0);
		tagrow.setGravity(Gravity.CENTER_HORIZONTAL);
		tagrow.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		while (ll.getChildCount() > 0) {
			View tog = ll.getChildAt(0);
			ll.removeViewAt(0);
			int leftover = pwidth - twidth;
			if (tog.getWidth() > leftover) {
				mTagParent.addView(tagrow);
				tagrow = new LinearLayout(this);// .removeAllViews();
				tagrow.setOrientation(0);
				tagrow.setGravity(Gravity.CENTER_HORIZONTAL);
				tagrow.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
				twidth = 0;
			}
			tagrow.addView(tog);
			twidth += tog.getWidth();
		}
		mTagParent.addView(tagrow);
	}

	public void handlePrev(View v) {
		Intent i = new Intent(this, EditPing.class);
		i.putExtra(PingsDbAdapter.KEY_ROWID, mRowId - 1);
		startActivity(i);
		finish();
	}

	public void handleNext(View v) {
		Intent i = new Intent(this, EditPing.class);
		i.putExtra(PingsDbAdapter.KEY_ROWID, mRowId + 1);
		startActivity(i);
		finish();
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
		if (findViewById(R.id.editping_tagedit_landscape) == null) {
			landscape = false;
			mTagScroll = (ScrollView) findViewById(R.id.editping_tagselect);
			mTagsEdit = (EditText) findViewById(R.id.editping_tagedit_portrait);

			mTagParent = (LinearLayout) findViewById(R.id.lintags);
			mVto = mTagParent.getViewTreeObserver();
			if (LOCAL_LOGV) Log.v(TAG, "onResume: PORTRAIT");
		} else {
			landscape = true;
			if (LOCAL_LOGV) Log.v(TAG, "onResume: LANDSCAPE");
		}
		setEditMode();
		populateFields();
	}

	/**
	 * Called from onPause(), this method saves the current tag selection into
	 * the database, or updates the outgoing intent to include current tag
	 * selection
	 */
	private void saveState() {
		if (LOCAL_LOGV) Log.v(TAG, "saveState()");

		if (landscape || editmode) {
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

		if (mCurrentTags == null) {
			setResult(RESULT_CANCELED);
		} else {
			// Update result intent
			Intent resultIntent = new Intent();
			resultIntent.putExtra(KEY_TAGS, mCurrentTagString);
			setResult(RESULT_OK, resultIntent);

			// Submit datapoint associated with the ping
			if (mRowId >= 0) {
				Intent intent = new Intent(this, BeeminderService.class);
				intent.setAction(BeeminderService.ACTION_EDITPING);
				intent.putExtra(BeeminderService.KEY_PID, mRowId);
				intent.putExtra(BeeminderService.KEY_OLDTAGS, "");
				intent.putExtra(BeeminderService.KEY_NEWTAGS, mCurrentTagString);
				this.startService(intent);
			}
		}
		super.finish();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mCurrentTags != null) {
			mCurrentTagString = TextUtils.join(" ", mCurrentTags);
			outState.putString("editping_tagsave", mCurrentTagString);
			outState.putBoolean("editping_editmode", editmode);
		}
	}

	@Override
	protected void onDestroy() {

		mPingsDB.closeDatabase();
		super.onDestroy();
	}

	private OnPreDrawListener mDrawListener = new OnPreDrawListener() {
		public boolean onPreDraw() {
			fixTags();
			ViewTreeObserver vto = mTagParent.getViewTreeObserver();
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
	public void onStop() {
		if (LOCAL_LOGV) Log.v(TAG, "onStop()");
		super.onStop();
	}

	/** Handles menu item selections */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case android.R.id.home:
			// app icon in action bar clicked; go home
			Intent intent = new Intent(this, ViewLog.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
}
