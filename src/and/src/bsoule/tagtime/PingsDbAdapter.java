package bsoule.tagtime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class PingsDbAdapter {
	private static final String TAG = "PingsDbAdapter";
	private static final boolean LOCAL_LOGV = false && !TagTime.DISABLE_LOGV;

	// Private members to handle the Singleton pattern
    private static PingsDbAdapter instance;
	private static DatabaseHelper mDbHelper = null;
	protected PingsDbAdapter() {}

    public static synchronized void initializeInstance(Context ctx) {
        if (instance == null) {
            instance = new PingsDbAdapter();
    		if (mDbHelper == null) mDbHelper = new DatabaseHelper(ctx);
        }
    }

    public static synchronized PingsDbAdapter getInstance() {
        if (instance == null) {
            throw new IllegalStateException(PingsDbAdapter.class.getSimpleName() +
                    " is not initialized, call initializeInstance(..) method first.");
        }
        return instance;
    }

	// Private members to handle the database reference counter
	private SQLiteDatabase mDb = null;
	private AtomicInteger mOpenCounter = new AtomicInteger();
	public synchronized SQLiteDatabase openDatabase() throws SQLException {
        if(mOpenCounter.incrementAndGet() == 1) {
    		mDb = mDbHelper.getWritableDatabase();
        }
		return mDb;
	}

	public void closeDatabase() {
        if(mOpenCounter.decrementAndGet() == 0) {
            mDbHelper.close();
            mDb = null;
        }
	}
    
	// Column name for ID fields 
	public static final String KEY_ROWID = "_id";
	// Table for pings
	public static final String KEY_PING = "ping";
	public static final String KEY_NOTES = "notes";
	public static final String KEY_PERIOD = "period";
	// Table for tags
	public static final String KEY_TAG = "tag";
	public static final String KEY_USED_CACHE = "used_cache";
	public static final String KEY_TIME_CACHE = "time_cache";
	// Table for ping tag pairs
	public static final String KEY_TAGPING = "tag_ping";
	public static final String KEY_PID = "ping_id";
	public static final String KEY_TID = "tag_id";

	/* ****** SQL statements for database creation. ****** */

	// a ping is a timestamp with optional notes
	private static final String CREATE_PINGS = "create table pings (_id integer primary key autoincrement, "
			+ "ping long not null, notes text, period integer not null, UNIQUE(ping));";
	// a tag is just a string (no spaces)
	private static final String CREATE_TAGS = "create table tags (_id integer primary key autoincrement, "
			+ "tag text not null, used_cache integer, UNIQUE (tag));";
	// a tagging is a ping and a tag
	private static final String CREATE_TAGPINGS = "create table tag_ping (_id integer primary key autoincrement, "
			+ "ping_id integer not null, tag_id integer not null," + "UNIQUE (ping_id, tag_id));";

	/* ****** Database and table names ****** */
	
	private static final String DATABASE_NAME = "timepiedata";
	private static final String PINGS_TABLE = "pings";
	private static final String TAGS_TABLE = "tags";
	private static final String TAG_PING_TABLE = "tag_ping";
	private static final int DATABASE_VERSION = 6;

	/** Database helper class for the Pings database. Handles creation, upgrade operations. */
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
			if (oldVersion < 2) {
				Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion
						+ ", which will destroy all old data");
				db.execSQL("DROP TABLE IF EXISTS pings");
				db.execSQL("DROP TABLE IF EXISTS tags");
				db.execSQL("DROP TABLE IF EXISTS tag_ping");
				onCreate(db);
			} else {
				if (oldVersion < 5 && newVersion >= 5) {
					Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion
							+ " calculating caches..");
					db.execSQL("ALTER TABLE tags ADD COLUMN used_cache integer");
					db.beginTransaction();
					try {
						// Calculate the first cache.
						Cursor all_tags = db.query(TAGS_TABLE, new String[] { KEY_ROWID, KEY_TAG }, null, null, null,
								null, null);
						Integer current_tag_id = 0;
						ContentValues uses_values = new ContentValues();

						all_tags.moveToFirst();
						while (!all_tags.isAfterLast()) {
							current_tag_id = all_tags.getInt(0);
							Cursor count_cr = db.rawQuery("SELECT COUNT(_id) FROM tag_ping WHERE tag_id = ?",
									new String[] { current_tag_id.toString() });
							count_cr.moveToFirst();
							uses_values.put(KEY_USED_CACHE, count_cr.getInt(0));

							if (LOCAL_LOGV) Log.v(TAG, "upgrading the tag entry cache - " + all_tags.getString(1)
									+ " has " + uses_values.getAsString(KEY_USED_CACHE));
							db.update(TAGS_TABLE, uses_values, "_id = ?", new String[] { current_tag_id.toString() });
							all_tags.moveToNext();
						}
						db.setTransactionSuccessful();
					} finally {
						db.endTransaction();
					}
				}

				if (oldVersion < 6 && newVersion >= 6) {
					Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion
							+ " adding missing ping durations (45 mins)...");
					db.execSQL("ALTER TABLE pings ADD COLUMN period integer");
					db.beginTransaction();
					try {
						// Calculate the first cache.
						Cursor all_pings = db.query(PINGS_TABLE, new String[] { KEY_ROWID }, null, null, null, null,
								null);
						Integer current_ping_id = 0;
						ContentValues period_values = new ContentValues();
						period_values.put(KEY_PERIOD, 45);

						all_pings.moveToFirst();
						while (!all_pings.isAfterLast()) {
							current_ping_id = all_pings.getInt(0);

							db.update(PINGS_TABLE, period_values, "_id = ?",
									new String[] { current_ping_id.toString() });
							all_pings.moveToNext();
						}
						db.setTransactionSuccessful();
					} finally {
						db.endTransaction();
					}
				}
			}
		}
	}

	protected void deleteAllData() {
		mDbHelper.onUpgrade(mDb, 1, DATABASE_VERSION);
	}

	// =============== Methods for the Pings table =====================
	/**
	 * Creates a ping with the supplied time and notes. Also creates ping/tag
	 * pairs corresponding to the given tag list.
	 */
	public long createPing(long pingtime, String notes, List<String> tags, int period) {
		if (LOCAL_LOGV) Log.v(TAG, "createPing()");
		long pid = newPing(pingtime, notes, period);
		if (!updateTaggings(pid, tags)) {
			Log.e(TAG, "createPing: error creating the tag-ping entries");
		}
		return pid;
	}

	/** Internal function to insert a new ping into the pings table */
	private long newPing(long pingtime, String pingnotes, int period) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_PING, pingtime);
		initialValues.put(KEY_NOTES, pingnotes);
		initialValues.put(KEY_PERIOD, period);
		return mDb.insert(PINGS_TABLE, null, initialValues);
	}

	/**
	 * Removes the ping with the given id from the pings database. Warning: Does
	 * not update ping/tag pairs.
	 */
	public boolean deletePing(long pingid) {
		return mDb.delete(PINGS_TABLE, KEY_ROWID + "=" + pingid, null) > 0;
	}

	/**
	 * Queries the database for all pings, returns the resulting Cursor for
	 * access.
	 * 
	 * @param reverse
	 *            Returns pings in reverse order of their ping times.
	 */
	public Cursor fetchAllPings(boolean reverse) {
		if (reverse) {
			return mDb.query(PINGS_TABLE, new String[] { KEY_ROWID, KEY_PING, KEY_NOTES, KEY_PERIOD }, null, null,
					null, null, KEY_PING + " DESC");
		} else {
			return mDb.query(PINGS_TABLE, new String[] { KEY_ROWID, KEY_PING, KEY_NOTES, KEY_PERIOD }, null, null,
					null, null, KEY_PING + " ASC");
		}
	}

	/**
	 * Update the indicated ping using the details provided.
	 * 
	 * @param pingid
	 *            id of ping to update
	 * @param pingnotes
	 *            new note to associate with the ping
	 * @return true if the note was successfully updated, false otherwise
	 */
	public boolean updatePing(long pingid, String pingnotes) {
		ContentValues args = new ContentValues();
		args.put(KEY_NOTES, pingnotes);
		return mDb.update(PINGS_TABLE, args, KEY_ROWID + "=" + pingid, null) > 0;
	}

	// =============== Methods for the Tags table =====================
	/**
	 * Attempts to create a new tag, throwing an exception if a tag with the
	 * same String already exists
	 */
	public long newTag(String tag) throws SQLException {
		if (LOCAL_LOGV) Log.v(TAG, "newTag(" + tag + ")");
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_TAG, tag);
		initialValues.put(KEY_USED_CACHE, 0);
		return mDb.insertOrThrow(TAGS_TABLE, null, initialValues);
	}

	/**
	 * Attempts to retrieve the id for the given tag String. If not found,
	 * creates a new one in the tags table and returns its ID.
	 */
	public long getOrMakeNewTID(String tag) {
		long tid;
		try {
			tid = newTag(tag);
		} catch (SQLException e) {
			tid = getTID(tag);
		}
		return tid;
	}

	/**
	 * Queries the tag database and returns the String name of the tag with the
	 * given id.
	 */
	public String getTagName(long tid) {
		String ret = "";
		Cursor c = mDb.query(TAGS_TABLE, new String[] { KEY_TAG }, KEY_ROWID + "=" + tid, null, null, null, null);
		if (c.getCount() > 0) {
			c.moveToFirst();
			ret = c.getString(c.getColumnIndex(KEY_TAG));
		}
		c.close();
		return ret;
	}

	/**
	 * Finds the id associated with the supplied tag string. Returns -1 if tag
	 * string is not found.
	 * 
	 * @param tag
	 * @return tag_id of tag or -1 if tag does not exist
	 */
	public long getTID(String tag) {
		if (LOCAL_LOGV) Log.v(TAG, "getTID(" + tag + ")");
		// return -1 if not found
		long tid = -1;
		Cursor cursor = mDb.query(TAGS_TABLE, new String[] { KEY_ROWID, KEY_TAG }, KEY_TAG + "='" + tag + "'", null,
				null, null, null, null);
		if (LOCAL_LOGV) Log.v(TAG, "getTID: queried for tag=" + tag);
		if (cursor.getCount() > 0) {
			cursor.moveToFirst();
			tid = cursor.getLong(cursor.getColumnIndex(KEY_ROWID));
		}
		cursor.close();
		return tid;
	}

	/**
	 * Returns a Cursor for all tags in the database, sorted by their use
	 * frequency
	 */
	public Cursor fetchAllTags() {
		return fetchAllTags("FREQ");
	}

	/**
	 * Returns a Cursor for all tags in the database, sorted by the indicated
	 * method. "FREQ" sorts by occurence frequency, "ALPHA" chooses alphabetic
	 * sort and "ROWID" sorts by the tag ID.
	 */
	public Cursor fetchAllTags(String ordering) {
		String sort_key = KEY_TAG + " COLLATE NOCASE";
		if (ordering.equals("FREQ")) {
			sort_key = KEY_USED_CACHE + " DESC";
		} else if (ordering.equals("ALPHA")) {
			sort_key = KEY_TAG + " COLLATE NOCASE";
		} else if (ordering.equals("ROWID")) {
			sort_key = KEY_ROWID;
		}
		return mDb.query(TAGS_TABLE, new String[] { KEY_ROWID, KEY_TAG }, null, null, null, null, sort_key);
	}

	/**
	 * Renames a tag.
	 * 
	 * @param Old
	 *            tag string to be renamed
	 * @param New
	 *            tag string
	 * @return true if update is successful.
	 */
	public boolean updateTag(String oldtag, String newtag) throws Exception {
		long tagid = getTID(oldtag);
		long tnewid = getTID(newtag);
		if (tnewid != -1) {
			Exception e = new Exception("newtag exists already");
			throw e;
		}
		if (tagid == -1) {
			Exception e = new Exception("oldtag is not an existing tag");
			throw e;
		}
		ContentValues args = new ContentValues();
		args.put(KEY_TAG, newtag);
		return mDb.update(TAGS_TABLE, args, KEY_ROWID + "=" + tagid, null) > 0;
	}

	/**
	 * Get an array of all the tag IDs
	 * 
	 * @return an array of tag IDs
	 */
	public long[] getAllTagIds() {
		Cursor all_tids = mDb.query(TAGS_TABLE, new String[] { "_id" }, null, null, null, null, null);
		long[] res = new long[all_tids.getCount()];
		int index = 0;
		all_tids.moveToFirst();
		while (!all_tids.isAfterLast()) {
			res[index++] = all_tids.getLong(0);
			all_tids.moveToNext();
		}
		all_tids.close();
		return res;
	}

	// =============== Methods for the Ping/Tag pair table =====================

	/**
	 * Attempts to create a new ping/tag pair, throwing an exception if an
	 * identical one already exists
	 */
	public long newTagPing(long pingid, long tagid) throws Exception {
		ContentValues init = new ContentValues();
		init.put(KEY_PID, pingid);
		init.put(KEY_TID, tagid);
		return mDb.insertOrThrow(TAG_PING_TABLE, null, init);
	}

	/**
	 * Queries whether the supplies ping/tag pair is present in the
	 * corresponding table
	 */
	public boolean isTagPing(long pingid, long tagid) {
		Cursor c = mDb.query(TAG_PING_TABLE, new String[] { KEY_ROWID }, KEY_PID + "=" + pingid + " AND " + KEY_TID
				+ "=" + tagid, null, null, null, null);
		boolean ret = c.getCount() > 0;
		c.close();
		return ret;
	}

	/**
	 * Removes the pair with the supplied ping id and tag string from the
	 * database.
	 */
	public boolean deleteTagPing(long pingid, String tag) {
		return deleteTagPing(pingid, getTID(tag));
	}

	/** Removes the pair with the supplied ping and tag ids from the database. */
	public boolean deleteTagPing(long pingid, long tagid) {
		String query = KEY_PID + "=" + pingid + " AND " + KEY_TID + "=" + tagid;
		return mDb.delete(TAG_PING_TABLE, query, null) > 0;
	}

	/**
	 * Finds and returns taggings that match a desired pattern.
	 * 
	 * @param id
	 *            Ping or tag id to compare against
	 * @param col_key
	 *            Column key to indicate whether the supplied ID is a ping id or
	 *            a tag id.
	 */
	public Cursor fetchTaggings(long id, String col_key) {
		return mDb.query(true, TAG_PING_TABLE, new String[] { KEY_PID, KEY_TID }, col_key + " = " + id, null, null,
				null, null, null);
	}

	/**
	 * Return a Cursor positioned at the note that matches the given rowId
	 * 
	 * @param pingid
	 *            id of note to retrieve
	 * @return Cursor positioned to matching note, if found
	 * @throws SQLException
	 *             if note could not be found/retrieved
	 */
	public Cursor fetchPing(long pingid) throws SQLException {
		Cursor pCursor = mDb.query(true, PINGS_TABLE, new String[] { KEY_ROWID, KEY_PING, KEY_NOTES, KEY_PERIOD },
				KEY_ROWID + "=" + pingid, null, null, null, null, null);
		if (pCursor != null) {
			pCursor.moveToFirst();
		}
		return pCursor;

	}

	// ===================== Compound methods using multiple tables ============

	/**
	 * Fetches a string with space separated list of tags for a ping with the
	 * supplied id
	 */
	public String fetchTagString(long pingid) throws Exception {
		Cursor c = mDb.query(TAG_PING_TABLE, new String[] { KEY_PID, KEY_TID }, KEY_PID + "=" + pingid, null, null,
				null, null);
		String s = "";
		try {
			c.moveToFirst();
			int idx = c.getColumnIndex(KEY_TID);
			while (!c.isAfterLast()) {
				long tid = c.getLong(idx);
				String t = getTagName(tid);
				if (t.equals("")) {
					Exception e = new Exception("Could not find tag with id=" + tid);
					throw e;
				}
				s += t + " ";
				c.moveToNext();
			}
		} finally {
			c.close();
		}
		return s;
	}

	/**
	 * Fetch a list of the tag names as a list of strings. This should be faster
	 * than manipulating the string all the time.
	 * 
	 * @param pingid
	 *            the ID of the ping
	 * @return a list of strings including tag names for the indicated ping
	 */
	public List<String> fetchTagNamesForPing(long pingid) throws Exception {
		Cursor c = fetchTaggings(pingid, KEY_PID);
		List<String> ret = new ArrayList<String>();
		c.moveToFirst();
		int idx = c.getColumnIndex(KEY_TID);
		while (!c.isAfterLast()) {
			long tid = c.getLong(idx);
			String t = getTagName(tid);
			if (t.equals("")) {
				Exception e = new Exception("Could not find tag with id=" + tid);
				throw e;
			}
			ret.add(t);
			c.moveToNext();
		}
		c.close();
		return ret;
	}

	/**
	 * Fetch a list of the tag ids as a list of longs.
	 * 
	 * @param pingid
	 *            the ID of the ping
	 * @return a list of longs including tag ids for the indicated ping
	 */
	public List<Long> fetchTagsForPing(long pingid) throws Exception {
		Cursor c = fetchTaggings(pingid, KEY_PID);
		List<Long> ret = new ArrayList<Long>();
		c.moveToFirst();
		int idx = c.getColumnIndex(KEY_TID);
		while (!c.isAfterLast()) {
			ret.add(c.getLong(idx));
			c.moveToNext();
		}
		c.close();
		return ret;
	}

	/** Updates usage counts for a specific tag in the database */
	public void updateTagCache(long tid) {
		Cursor count_cr = mDb.rawQuery("SELECT COUNT(_id) FROM tag_ping WHERE tag_id = ?",
				new String[] { Long.toString(tid) });
		count_cr.moveToFirst();
		ContentValues uses_values = new ContentValues();
		uses_values.put(KEY_USED_CACHE, count_cr.getInt(0));
		count_cr.close();
		mDb.update(TAGS_TABLE, uses_values, "_id = ?", new String[] { Long.toString(tid) });
	}

	/**
	 * Update usage counts for all tages in the database.
	 */
	public void updateTagCaches() {
		long[] tagIds = getAllTagIds();
		for (long tid : tagIds) {
			updateTagCache(tid);
		}
	}

	/**
	 * Updates the taggings of the ping pingid to be equal to newTags.
	 */
	public boolean updateTaggings(long pingid, List<String> newTags) {
		if (LOCAL_LOGV) Log.v(TAG, "updateTaggings(" + pingid + ")");

		// Remove all the old tags.
		mDb.delete(TAG_PING_TABLE, KEY_PID + "=" + pingid, null);

		// Construct a list of tags for which usage count should be updated
		List<String> cacheUpdates = new ArrayList<String>();
		try {
			cacheUpdates = fetchTagNamesForPing(pingid);
		} catch (Exception e) {
			Log.w(TAG, "updateTaggings: Some problem getting tags for this ping!");
			e.printStackTrace();
		}
		cacheUpdates.addAll(newTags);

		// Now, insert new taggings
		for (String t : newTags) {
			if (t.trim().length() == 0) continue;
			long tid = getOrMakeNewTID(t);
			if (tid == -1) Log.e(TAG, "updateTaggings: ERROR: about to insert tid -1");
			try {
				newTagPing(pingid, tid);
			} catch (Exception e) {
				Log.w(TAG, "updateTaggings: error inserting newTagPing(" + pingid + "," + tid + ") in updateTaggings()");
			}
		}

		// Update usage counts for all tags affected
		for (String tag : cacheUpdates) {
			updateTagCache(getTID(tag));
		}
		return true;
	}

	/** Cleans up the tags database, removing all unused tags */
	public void cleanupUnusedTags() {
		BeeminderDbAdapter bdb = BeeminderDbAdapter.getInstance();
		bdb.openDatabase();

		Cursor c = fetchAllTags();
		c.moveToFirst();
		int idx = c.getColumnIndex(KEY_ROWID);
		int tagIdx = c.getColumnIndex(KEY_TAG);
		while (!c.isAfterLast()) {
			long tagid = c.getLong(idx);
			// Check ping tag pairs
			Cursor tids = mDb.query(TAG_PING_TABLE, new String[] { KEY_ROWID }, KEY_TID + "=" + tagid, null, null,
					null, null);
			int usecount = tids.getCount();
			tids.close();

			// Check goal tag pairs
			Cursor gtids = bdb.fetchGoalTags(tagid, BeeminderDbAdapter.KEY_TID);
			usecount += gtids.getCount();
			gtids.close();

			if (LOCAL_LOGV) Log.v(TAG, "cleanupUnusedTags: tag " + c.getString(tagIdx) + " is used " + usecount
					+ " times");
			if (usecount == 0) {
				if (LOCAL_LOGV) Log.i(TAG, "cleanupUnusedTags: removing tag " + c.getString(tagIdx)
						+ " noone is using it.");
				mDb.delete(TAGS_TABLE, KEY_ROWID + "=" + tagid, null);
			}
			c.moveToNext();
		}
		c.close();
		bdb.closeDatabase();
	}
}
