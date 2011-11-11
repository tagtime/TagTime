package bsoule.timepie;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Log;

public class PingsDbAdapter {

	public static final String KEY_PING = "ping";
	public static final String KEY_NOTES = "notes";
	public static final String KEY_TAG = "tag";
	public static final String KEY_USED_CACHE = "used_cache";
	public static final String KEY_TIME_CACHE = "time_cache";
	public static final String KEY_TAGPING = "tag_ping";
	public static final String KEY_ROWID = "_id";
	public static final String KEY_PID = "ping_id";
	public static final String KEY_TID = "tag_id";

	private static final String TAG = "PingsDbAdapter";
	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;

	/** Database creation sql statement */

	// a ping is a timestamp with optional notes
	private static final String CREATE_PINGS =
		"create table pings (_id integer primary key autoincrement, "
		+ "ping long not null, notes text, UNIQUE(ping));";	
	// a tag is just a string (no spaces)
	private static final String CREATE_TAGS = 
		"create table tags (_id integer primary key autoincrement, "
		+ "tag text not null, used_cache integer, UNIQUE (tag));";
	// a tagging is a ping and a tag
	private static final String CREATE_TAGPINGS = 
		"create table tag_ping (_id integer primary key autoincrement, "
		+ "ping_id integer not null, tag_id integer not null," 
		+ "UNIQUE (ping_id, tag_id));";

	private static final String DATABASE_NAME = "timepiedata";
	private static final String PINGS_TABLE = "pings";
	private static final String TAGS_TABLE = "tags";
	private static final String TAG_PING_TABLE = "tag_ping";
	private static final int DATABASE_VERSION = 5;

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
			if (oldVersion < 4) {
				Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
						+ newVersion + ", which will destroy all old data");
				db.execSQL("DROP TABLE IF EXISTS pings");
				db.execSQL("DROP TABLE IF EXISTS tags");
				db.execSQL("DROP TABLE IF EXISTS tag_ping");
				onCreate(db);
			}
			if (oldVersion < 5 && newVersion >= 5) {
				Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
						+ newVersion + " calculating caches..");
				db.execSQL("ALTER TABLE tags ADD COLUMN used_cache integer");
				db.beginTransaction();
				try {
					// Calculate the first cache.
					Cursor all_tags = db.query(TAGS_TABLE, new String[] {KEY_ROWID,KEY_TAG},
												null, null, null, null, null);
					Integer current_tag_id = 0;
					ContentValues uses_values = new ContentValues();

					all_tags.moveToFirst();
					while (!all_tags.isAfterLast()) {
						current_tag_id = all_tags.getInt(0);
						Cursor count_cr = db.rawQuery("SELECT COUNT(_id) FROM tag_ping WHERE tag_id = ?", new String[]{current_tag_id.toString()});
						count_cr.moveToFirst();
						uses_values.put(KEY_USED_CACHE, count_cr.getInt(0));
						Log.i(TAG, "upgrading the tag entry cache - " + all_tags.getString(1) + " has "
								+ uses_values.getAsString(KEY_USED_CACHE));
						db.update(TAGS_TABLE, uses_values, "_id = ?", new String[]{current_tag_id.toString()});
						all_tags.moveToNext();
					}
					db.setTransactionSuccessful();
				} finally {
					db.endTransaction();
				}
			}
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
		mDbHelper.onUpgrade(mDb, 1, DATABASE_VERSION);
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

	public long createPing(long pingtime, String notes, List<String> tags) {
		Log.i(TAG,"createPing()");
		long pid = newPing(pingtime, notes);
		if (!updateTaggings(pid, tags)) {
			Log.e(TAG, "error creating the tag-ping entries");
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
		initialValues.put(KEY_USED_CACHE, 0);
		return mDb.insertOrThrow(TAGS_TABLE, null, initialValues);
	}

	public long newTagPing(long ping_id, long tag_id) throws Exception {
		ContentValues init = new ContentValues();
		init.put(KEY_PID, ping_id);
		init.put(KEY_TID, tag_id);
		return mDb.insertOrThrow(TAG_PING_TABLE, null, init);
	}

	public String getTagName(long tid) {
		String ret = "";
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
		return deleteTagPing(pingId, getTID(tag));
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
		return fetchAllTags("FREQ");
	}
	
	public Cursor fetchAllTags(String ordering) {
		String sort_key = KEY_TAG + " COLLATE NOCASE";
		if (ordering.equals("FREQ")) {
		  sort_key = KEY_USED_CACHE + " DESC";
		} else if (ordering.equals("ALPHA")) {
		  sort_key = KEY_TAG + " COLLATE NOCASE";
		}
		return mDb.query(TAGS_TABLE, new String[] {KEY_ROWID, KEY_TAG}, null, null, null, null, sort_key);
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
		try {
			c.moveToFirst();
			int idx = c.getColumnIndex(KEY_TID);
			while (!c.isAfterLast()) {
				long tid = c.getLong(idx);
				String t = getTagName(tid);
				if (t.equals("")) {
					Exception e = new Exception("Could not find tag with id="+tid);
					throw e;
				}
				s += t+" ";
				c.moveToNext();
			}
		} finally {
			c.close();
		}
		return s;
	}

	/**
	 * Fetch a list of the tags as strings, because it should be faster than
	 * manipulating the string all the time.
	 *
	 * @param ping_id the ID of the ping
	 * @return a list of the tags
	 */
	public List<String> fetchTagsForPing(long ping_id) throws Exception {
		Cursor c = fetchTaggings(ping_id, KEY_PID);
		List<String> ret = new ArrayList<String>();
		c.moveToFirst();
		int idx = c.getColumnIndex(KEY_TID);
		while (!c.isAfterLast()) {
			long tid = c.getLong(idx);
			String t = getTagName(tid);
			if (t.equals("")) {
				Exception e = new Exception("Could not find tag with id="+tid);
				throw e;
			}
			ret.add(t);
			c.moveToNext();
		}
		c.close();
		return ret;
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

	/**
	 * Get an array of all the tag IDs
	 * @return an array of tag IDs
	 */
	public long[] getAllTagIds() {
		Cursor all_tids = mDb.query(TAGS_TABLE, new String[]{"_id"}, null, null, null, null, null);
		long[] res = new long[all_tids.getCount()];
		int index = 0;
		all_tids.moveToFirst();
		while(!all_tids.isAfterLast()) {
			res[index++] = all_tids.getLong(0);
			all_tids.moveToNext();
		}
		all_tids.close();
		return res;
	}

	public void updateTagCache(long tid) {
		Cursor count_cr = mDb.rawQuery("SELECT COUNT(_id) FROM tag_ping WHERE tag_id = ?",
                                       new String[]{Long.toString(tid)});
		count_cr.moveToFirst();
		ContentValues uses_values = new ContentValues();
		uses_values.put(KEY_USED_CACHE, count_cr.getInt(0));
		count_cr.close();
		mDb.update(TAGS_TABLE, uses_values, "_id = ?", new String[]{Long.toString(tid)});
	}

	/**
	 * Update all the uses_caches of the tags.
	 * @return nothing
	 */
	public void updateTagCaches() {
		long[] tagIds = getAllTagIds();
		for (long tid : tagIds) {
			updateTagCache(tid);
		}
	}

	/**
	 * Updates the taggings of the ping pingId to be equal to newTags.
	 * @param pingId
	 * @param newTags
	 * @return true
	 */
	public boolean updateTaggings(long pingId, List<String> newTags) {
		Log.i(TAG, "updateTaggings() improved");
		// Remove all the old tags.
		List<String> cacheUpdates = new ArrayList<String>();
		try {
			cacheUpdates = fetchTagsForPing(pingId);
		} catch (Exception e) {
			Log.i(TAG, "Some problem getting tags for this ping!");
			e.printStackTrace();
		}
		cacheUpdates.addAll(newTags);
		mDb.delete(TAG_PING_TABLE, KEY_PID + "=" + pingId, null);
		for (String t : newTags) {
			if (t.trim() == "") {
				continue;
			}
			long tid = getOrMakeNewTID(t);
			if (tid == -1) {
				Log.e(TAG, "ERROR: about to insert tid -1");
			}
			try {
				newTagPing(pingId, tid);
			} catch (Exception e) {
				Log.i(TAG,"error inserting newTagPing("+pingId+","+tid+") in updateTaggings()");
			}
		}
		for (String tag : cacheUpdates) {
			updateTagCache(getTID(tag));
		}
		return true;
	}
	public void cleanupUnusedTags() {
		Cursor c = fetchAllTags();
		c.moveToFirst();
		int idx = c.getColumnIndex(KEY_ROWID);
		int tagIdx = c.getColumnIndex(KEY_TAG);
		while (!c.isAfterLast()) {
			long tid = c.getLong(idx);
			Cursor tids = mDb.query(TAG_PING_TABLE, new String[] {KEY_ROWID}, KEY_TID + "=" + tid, null, null, null, null);
			int usecount = tids.getCount();
			tids.close();
			Log.i(TAG, "tag " + c.getString(tagIdx) + " is used " + usecount + " times");
			if (usecount == 0) {
				Log.i(TAG, "removing tag " + c.getString(tagIdx) + " noone is using it.");
				mDb.delete(TAGS_TABLE, KEY_ROWID + "=" + tid, null);
			}
			c.moveToNext();
		}
		c.close();
	}
}
