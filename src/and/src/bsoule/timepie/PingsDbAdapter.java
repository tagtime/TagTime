package bsoule.timepie;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.util.Log;

public class PingsDbAdapter {

	public static final String KEY_PING = "ping";
	public static final String KEY_NOTES = "notes";
	public static final String KEY_TAG = "tag";
	public static final String KEY_TAGPING = "tag_ping";
	public static final String KEY_ROWID = "_id";
	public static final String KEY_PID = "ping_id";
	public static final String KEY_TID = "tag_id";

	private static final String TAG = "PingsDbAdapter";
	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;

	/**
	 * Database creation sql statement
	 */

	// a ping is a timestamp with optional notes
	private static final String CREATE_PINGS =
		"create table pings (_id integer primary key autoincrement, "
		+ "ping long not null, notes text, UNIQUE(ping));";	
	// a tag is just a string (no spaces)
	private static final String CREATE_TAGS = 
		"create table tags (_id integer primary key autoincrement, "
		+ "tag text not null, UNIQUE (tag));";
	// a tagging is a ping and a tag
	private static final String CREATE_TAGPINGS = 
		"create table tag_ping (_id integer primary key autoincrement, "
		+ "ping_id integer not null, tag_id integer not null," 
		+ "UNIQUE (ping_id, tag_id));";


	private static final String DATABASE_NAME = "timepiedata";
	private static final String PINGS_TABLE = "pings";
	private static final String TAGS_TABLE = "tags";
	private static final String TAG_PING_TABLE = "tag_ping";
	private static final int DATABASE_VERSION = 4;

	private final Context mCtx;

	private static class DatabaseHelper extends SQLiteOpenHelper {

		DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(CREATE_PINGS);
			db.execSQL(CREATE_TAGS);
			db.execSQL(CREATE_TAGPINGS);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
					+ newVersion + ", which will destroy all old data");
			db.execSQL("DROP TABLE IF EXISTS pings");
			db.execSQL("DROP TABLE IF EXISTS tags");
			db.execSQL("DROP TABLE IF EXISTS tag_ping");
			onCreate(db);
		}
	}

	/**
	 * Constructor - takes the context to allow the database to be
	 * opened/created
	 * 
	 * @param ctx the Context within which to work
	 */
	public PingsDbAdapter(Context ctx) {
		this.mCtx = ctx;
	}
	protected void deleteAllData() {
		mDbHelper.onUpgrade(mDb, DATABASE_VERSION, DATABASE_VERSION);
	}

	/**
	 * Open the database. If it cannot be opened, try to create a new
	 * instance of the database. If it cannot be created, throw an exception to
	 * signal the failure
	 * 
	 * @return this (self reference, allowing this to be chained in an
	 *         initialization call)
	 * @throws SQLException if the database could be neither opened or created
	 */
	public PingsDbAdapter open() throws SQLException {
		mDbHelper = new DatabaseHelper(mCtx);
		mDb = mDbHelper.getWritableDatabase();
		return this;
	}

	public void close() {
		mDbHelper.close();
	}

	public long createPing(long pingtime, String notes, String tags) {
		Log.i(TAG,"createPing()");
		long pid = newPing(pingtime,notes);
		//Log.i(TAG,"got pingId="+pid);
		if (!updateTaggings(pid, "", tags)) {
			Log.e(TAG, "error creating the tag-ping entries");
		} else {
			//	Log.i(TAG,"updated tag-ping entries: "+tags+" "+loctag);
		}
		return pid;
	}

	private long newPing(long pingtime, String pingnotes) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_PING, pingtime);
		initialValues.put(KEY_NOTES, pingnotes);
		return mDb.insert(PINGS_TABLE, null, initialValues);
	}

	public long newTag(String tag) throws Exception {
		Log.i(TAG,"newTag("+tag+")");
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_TAG, tag);
		return mDb.insertOrThrow(TAGS_TABLE, null, initialValues);
	}
	public long newTagPing(long ping_id, long tag_id) throws Exception {
		ContentValues init = new ContentValues();
		init.put(KEY_PID, ping_id);
		init.put(KEY_TID, tag_id);
		return mDb.insertOrThrow(TAG_PING_TABLE, null, init);
	}

	public String getTag(long tid) {
		String ret = "";
		//TODO: will this work? to return only the tag column when I'm matching
		//      against a different (ID) column?
		Cursor c = mDb.query(TAGS_TABLE, new String[] {KEY_TAG}, 
				KEY_ROWID+"="+tid, null, null, null, null);
		if (c.getCount()>0) {
			c.moveToFirst();
			ret = c.getString(c.getColumnIndex(KEY_TAG));
		}
		c.close();
		return ret;
	}
	// round d to 5 sig digits
	public static double round5(double d) {
		return (java.lang.Math.round(d*100000))/100000.0;
	}
	private long getOrMakeNewTID(String tag) {
		long tid;
		try {
			tid = newTag(tag);
		} catch (Exception e) {
			tid = getTID(tag);
		}
		return tid;
	}


	/**
	 * Delete the note with the given rowId
	 * 
	 * @param rowId id of note to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deletePing(long rowId) {
		return mDb.delete(PINGS_TABLE, KEY_ROWID + "=" + rowId, null) > 0;
	}

	/**
	 * 
	 * @param tag
	 * @return tag_id of tag or -1 if tag does not exist
	 */
	public long getTID(String tag) {
		Log.i(TAG,"getTID("+tag+")");
		// return -1 if not found
		long tid = -1;
		Cursor cursor = 
			mDb.query(TAGS_TABLE, new String[] {KEY_ROWID, KEY_TAG}, 
					KEY_TAG + "='" + tag + "'", null,
					null, null, null, null);
		Log.i(TAG,"queried for tag="+tag);
		if (cursor.getCount()>0) {
			cursor.moveToFirst();
			tid = cursor.getLong(cursor.getColumnIndex(KEY_ROWID));
		}
		cursor.close();
		return tid;
	}

	public boolean isTagPing(long pid, long tid) {
		Cursor c = mDb.query(TAG_PING_TABLE, new String[] {KEY_ROWID}, 
				KEY_PID+"="+pid+" AND "+KEY_TID+"="+tid, 
				null, null, null, null);
		boolean ret = c.getCount()>0;
		c.close();
		return ret;
	}

	public boolean deleteTagPing(long pingId, String tag) {
		String query = KEY_PID + "=" + pingId + " AND "+ KEY_TID 
		+ "=" + getTID(tag);
		return mDb.delete(TAG_PING_TABLE, query, null) > 0;
	}
	public boolean deleteTagPing(long pingId, long tagId) {
		String query = KEY_PID + "=" + pingId + " AND " +
		KEY_TID + "=" + tagId;
		return mDb.delete(TAG_PING_TABLE, query, null) > 0;
	}

	/**
	 * Return a Cursor over the list of all notes in the database
	 * 
	 * @return Cursor over all notes
	 */
	public Cursor fetchAllPings(boolean reverse) {
		if (reverse) { // return notes in reverse order
			//mDb.query(table, columns, selection, selectionArgs, groupBy, having, orderBy)
			return mDb.query(PINGS_TABLE, new String[] {KEY_ROWID,KEY_PING,KEY_NOTES}, 
					null, null, null, null, KEY_PING+" DESC");
		} else {
			return mDb.query(PINGS_TABLE, new String[] {KEY_ROWID, KEY_PING,
					KEY_NOTES}, null, null, null, null, KEY_PING+" ASC");
		}
	}

	public Cursor fetchTaggings(long id, String col_key, String order) {
		return mDb.query(true, TAG_PING_TABLE, new String[] {KEY_PID, KEY_TID}, 
				col_key+" = "+id, null, null, null, KEY_PID+" DESC", null);		
	}
	public Cursor fetchTaggings(long id, String col_key) {
		return mDb.query(true, TAG_PING_TABLE, new String[] {KEY_PID, KEY_TID}, 
				col_key+" = "+id, null, null, null, null, null);
	}

	public Cursor fetchAllTags() {
		return mDb.query(TAGS_TABLE, new String[] {KEY_ROWID, KEY_TAG}, 
				null, null, null, null, null);
	}

	/**
	 * Return a Cursor positioned at the note that matches the given rowId
	 * 
	 * @param rowId id of note to retrieve
	 * @return Cursor positioned to matching note, if found
	 * @throws SQLException if note could not be found/retrieved
	 */
	public Cursor fetchPing(long rowId) throws SQLException {

		Cursor pCursor =
			mDb.query(true, PINGS_TABLE, new String[] {KEY_ROWID,
					KEY_PING, KEY_NOTES}, KEY_ROWID + "=" + rowId, null,
					null, null, null, null);
		if (pCursor != null) {
			pCursor.moveToFirst();
		}
		return pCursor;

	}

	public String fetchTagString(long ping_id) throws Exception {
		Cursor c = mDb.query(TAG_PING_TABLE, new String[] {KEY_PID, KEY_TID}, 
				KEY_PID + "=" + ping_id, null, null, null, null);

		String s = "";
		c.moveToFirst();
		int idx = c.getColumnIndex(KEY_TID);
		while (!c.isAfterLast()) {
			long tid = c.getLong(idx);
			String t = getTag(tid);
			if (t.equals("")) {
				Exception e = new Exception("Could not find tag with id="+tid);
				throw e;
			}
			s += t+" ";
			c.moveToNext();
		}
		c.close();
		return s;
	}



	/**
	 * Update the note using the details provided. The note to be updated is
	 * specified using the rowId, and it is altered to use the title and body
	 * values passed in
	 * 
	 * @param rowId id of note to update
	 * @param title value to set note title to
	 * @param body value to set note body to
	 * @return true if the note was successfully updated, false otherwise
	 */
	public boolean updatePing(long rowId, String pingnotes) {
		ContentValues args = new ContentValues();
		args.put(KEY_NOTES, pingnotes);
		return mDb.update(PINGS_TABLE, args, KEY_ROWID + "=" + rowId, null) > 0;
	}

	public boolean updateTag(String oldtag, String newtag) throws Exception {
		long tid = getTID(oldtag);
		long tnewid = getTID(newtag);
		if (tnewid != -1) {
			Exception e = new Exception("newtag exists already");
			throw e;
		}
		if (tid == -1) {
			Exception e = new Exception("oldtag is not an existing tag");
			throw e;
		}
		ContentValues args = new ContentValues();
		args.put(KEY_TAG, newtag);
		return mDb.update(TAGS_TABLE, args, KEY_ROWID + "=" + tid, null) > 0;
	}

	public boolean updateTaggings(long pingId, String origTags, String editTags) {
		Log.i(TAG, "updateTaggings()");
		String[] tags = editTags.split(" ");
		for (String t : tags) {
			if (!t.equals("")) {
				// first need to compare against original tag string:
				Pattern pt = Pattern.compile("(^|\\s)"+t+"(\\s|$)");
				Matcher mm = pt.matcher(origTags);
				// if t is not in origTags need to get TID and add a tagping for it
				if (!mm.find()) { // t is not in origTags
					Log.i(TAG,"!mm.find()");
					// first get tag_id
					long tid = getOrMakeNewTID(t);
					if (tid == -1) {
						Log.e(TAG,"ERROR: about to insert tid -1");
					}
					try {
						newTagPing(pingId, tid);
					} catch (Exception e) {
						Log.i(TAG,"error inserting newTagPing("+pingId+","+tid+") in updateTaggings()");
					}
				}
				origTags = origTags.replace(t,""); // remove this tag from original string
			}
		}
		// that took care of all the tags in the editTag string, now the remaining origTags
		// should be any tags that were removed on edit, so need to go through and remove their
		// tag-ping entry.
		// x in orig not in edit..
		//Log.i(TAG,"FINISHED adding tagpings");
		String[] dels = origTags.split(" ");
		for (String dtag : dels) {
			if (!dtag.equals("")) {
				deleteTagPing(pingId,dtag);
			}
		}
		return true;
	}
}
