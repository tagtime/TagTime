package bsoule.timepie;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;


public class EditPing extends Activity {

	public static final String KEY_EDIT = "editor";
	public static final String KEY_PTIME = "pingtime";

	private PingsDbAdapter mPingsDB;
	//private EditText mNotesEdit;
	private EditText mTagsEdit = null;
	private TextView mPingTitle;
	private Long mRowId;
	private Long mPingUTC;
	private LinearLayout tagParent;
	private Cursor mTagsCursor;
	private Cursor mTaggings;
	private int FIXTAGS = R.layout.timepie_editping;
	private ViewTreeObserver vto;

	private boolean landscape;
	private String mTagstring;
	private static final String TAG = "***************EditPing:";

	public static int LAUNCH_VIEW = 0;
	public static int LAUNCH_NOTE = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.timepie_editping);
		//mNotesEdit = (EditText) findViewById(R.id.notes);
		
		View v = findViewById(R.id.tags_editText);
		if (v == null) {
			landscape = false;
			tagParent = (LinearLayout) findViewById(R.id.lintags);
			vto = tagParent.getViewTreeObserver();
		} else {
			landscape = true;
			mTagsEdit = (EditText) v;
		}

		// cancel the notification
		// TODO: only cancel note if it is for same ping as we are editing
		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.cancel(R.layout.timepie_editping);

		// set up editor:
		mPingTitle = (TextView) findViewById(R.id.pingtime);
		mRowId = getIntent().getLongExtra(PingsDbAdapter.KEY_ROWID, -1);

		mPingsDB = new PingsDbAdapter(this);
		mPingsDB.open();
		mTagsCursor = mPingsDB.fetchAllTags();
		startManagingCursor(mTagsCursor);
		mTaggings = mPingsDB.fetchTaggings(mRowId,PingsDbAdapter.KEY_PID);
		startManagingCursor(mTaggings);

		//Set confirm button behaviour:
		Button confirm = (Button) findViewById(R.id.confirm);
		confirm.setOnClickListener(	new OnClickListener() {
			public void onClick(View v) {
				setResult(RESULT_OK);
				finish();
			}
		});
	}

	private void populateFields() {
		//Log.i(TAG,"populateFields()");
		if (mRowId != null) {

			Cursor note = mPingsDB.fetchPing(mRowId);
			startManagingCursor(note);
			try {
				// set ping time title
				mPingUTC = note.getLong(note.getColumnIndexOrThrow(PingsDbAdapter.KEY_PING));
				SimpleDateFormat SDF = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault());		
				mPingTitle.setText(SDF.format(new Date(mPingUTC*1000)));
				// get tags
				mTagstring = mPingsDB.fetchTagString(mRowId); 
					//note.getString(note.getColumnIndexOrThrow(PingsDbAdapter.KEY_TAG_STRING));
				if (!landscape) {
					loadTags();
//					mNotesText.setText(note.getString(
//							note.getColumnIndexOrThrow(PingsDbAdapter.KEY_NOTES)));
				} else {
					mTagsEdit.setText(mTagstring);
				}
				//mNotesEdit.setText(note.getString(
				//		note.getColumnIndexOrThrow(PingsDbAdapter.KEY_NOTES)));

			} catch (Exception e) {
				Log.i(TAG, "caught an exception in populateFields():");
				Log.i(TAG, "    "+e.getLocalizedMessage());
				Log.i(TAG, "    "+e.getMessage());
			}
		} else {
			Log.i(TAG,"ROWID was NULL");
		}
	}

	private void loadTags() {
		tagParent.removeAllViews();
		mTagsCursor.moveToFirst();

		LinearLayout ll = new LinearLayout(this);
		ll.setId(FIXTAGS);
		ll.setOrientation(1);
		ll.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT,LayoutParams.WRAP_CONTENT));
		while (!mTagsCursor.isAfterLast()) {
			String tag = mTagsCursor.getString(
					mTagsCursor.getColumnIndex(PingsDbAdapter.KEY_TAG));
			long id = mTagsCursor.getLong(
					mTagsCursor.getColumnIndex(PingsDbAdapter.KEY_ROWID));
			Pattern ps = Pattern.compile("(^|\\s+)"+tag+"(\\s+|$)");
			Matcher ms = ps.matcher(mTagstring);
			boolean on = ms.find();
			TagToggle tog = new TagToggle(this, tag, id, on);
			tog.setOnClickListener(mTogListener);
			ll.addView(tog);

			mTagsCursor.moveToNext();
		}
		tagParent.addView(ll);
		vto.addOnPreDrawListener(mDrawListener);
	}

	private void fixTags() {
		LinearLayout ll = (LinearLayout) tagParent.getChildAt(0);
		tagParent.removeAllViews();
		int pwidth = tagParent.getWidth() - 15;
		int twidth = 0;

		LinearLayout tagrow = new LinearLayout(this);
		tagrow.setOrientation(0);
		tagrow.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
		while (ll.getChildCount()>0) {
			View tog = ll.getChildAt(0);
			ll.removeViewAt(0);
			if ( (twidth+tog.getWidth()) > pwidth ) {
				tagParent.addView(tagrow);
				tagrow = new LinearLayout(this);//.removeAllViews();
				tagrow.setOrientation(0);
				tagrow.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
				twidth = 0;
			} 
			tagrow.addView(tog);
			twidth+=tog.getWidth();
		}
		tagParent.addView(tagrow);
	}

	@Override
	protected void onPause() {
		super.onPause();
		saveState();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if ( findViewById(R.id.tags_editText) == null ) {
			landscape = false;
			Log.i(TAG, "PORTRAIT");
		} else {
			landscape = true;
			Log.i(TAG, "LANDSCAPE");
		}
		populateFields();
	}

	private void saveState() {
		//String pingnotes = mNotesEdit.getText().toString();
		if (landscape) {
			String newtagstring = mTagsEdit.getText().toString();
			//mPingsDB.updatePing(mRowId, pingnotes);
			if (!mTagstring.equals(newtagstring)) {
				mPingsDB.updateTaggings(mRowId,mTagstring,newtagstring);
				mTagstring = newtagstring;
			}
		} else {
			//String pingnotes = mNotesText.getText().toString();
			//mPingsDB.updatePing(mRowId,pingnotes);
		}
	}

	public void startView() {
		startActivity(new Intent(this, ViewLog.class));
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
			if (tog.isSelected()) {
				Log.i(TAG,"toggle selected");
				try {
					mPingsDB.newTagPing(mRowId,tog.getTId());
					mTagstring += " " + tog.getText().toString();
				} catch (Exception e) {
					Log.e(TAG,"error inserting newTagPing()");
				}
			} else {
				mPingsDB.deleteTagPing(mRowId,tog.getTId());
				mTagstring = mTagstring.replace(tog.getText(), "");
				mTagstring = mTagstring.replaceAll("\\s{2,}", " ");
				mTagstring = mTagstring.replaceAll("(\\s+$)|(^\\s+)", "");
			}
		}
		
	};

}
