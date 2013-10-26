package bsoule.tagtime;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class BeeminderDbAdapter {

	public static final String KEY_ROWID = "_id";

	// Table for goal registrations
	public static final String KEY_USERNAME = "user";
	public static final String KEY_SLUG = "slug";
	public static final String KEY_TOKEN = "token";
	// Table for goal tags
	public static final String KEY_GID = "goal_id";
	public static final String KEY_TID = "tag_id";
	// Table for datapoint submissions
	public static final String KEY_REQID = "req_id";
	public static final String KEY_VALUE = "value";
	public static final String KEY_TIMESTAMP = "time";
	public static final String KEY_COMMENT = "comment";
	// Uses KEY_GID
	public static final String KEY_PID = "ping_id";

	private static final String DATABASE_NAME = "timepie_beeminder";
	private static final String GOALS_TABLE = "goals";
	private static final String GOALTAGS_TABLE = "goaltags";
	private static final String POINTS_TABLE = "points";
	private static final int DATABASE_VERSION = 1;

	private static final String TAG = "BeeminderDbAdapter";
	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;

	/** Database creation sql statement */

	// a goal is a user/slug with a Beeminder token for submission
	private static final String CREATE_GOALS = "create table goals (_id integer primary key autoincrement, "
			+ "user text not null, slug text not null, token text not null);";

	// a tag is just a string (no spaces)
	private static final String CREATE_GOALTAGS = "create table goaltags (_id integer primary key autoincrement, "
			+ "goal_id integer not null, tag_id integer not null," + "UNIQUE (goal_id, tag_id));";
	// a tagging is a ping and a tag
	private static final String CREATE_POINTS = "create table points (_id integer primary key autoincrement, "
			+ "request_id text not null, value real not null, time integer not null, comment textnot null, goal_id integer ot null, ping_id integer not null,"
			+ "UNIQUE (goal_id, ping_id));";

	private final Context mCtx;

	private static class DatabaseHelper extends SQLiteOpenHelper {

		DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(CREATE_GOALS);
			db.execSQL(CREATE_GOALTAGS);
			db.execSQL(CREATE_POINTS);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion
					+ ", which will destroy all old data");
			db.execSQL("DROP TABLE IF EXISTS goals");
			db.execSQL("DROP TABLE IF EXISTS goaltags");
			db.execSQL("DROP TABLE IF EXISTS points");
			onCreate(db);
		}
	}

	public BeeminderDbAdapter(Context ctx) {
		this.mCtx = ctx;
	}

	protected void deleteAllData() {
		mDbHelper.onUpgrade(mDb, 1, DATABASE_VERSION);
	}

	/**
	 * Open the database. If it cannot be opened, try to create a new instance
	 * of the database. If it cannot be created, throw an exception to signal
	 * the failure
	 * 
	 * @return this (self reference, allowing this to be chained in an
	 *         initialization call)
	 * @throws SQLException
	 *             if the database could be neither opened or created
	 */
	public BeeminderDbAdapter open() throws SQLException {
		mDbHelper = new DatabaseHelper(mCtx);
		mDb = mDbHelper.getWritableDatabase();
		return this;
	}

	public void close() {
		mDbHelper.close();
	}

	public long createGoal(String user, String slug, String token, List<String> tags) {
		Log.i(TAG, "createGoal()");
		long gid = newGoal(user, slug, token);
		if (!updateGoalTags(gid, tags)) {
			Log.e(TAG, "error creating the goal-tag entries");
		}
		return gid;
	}

	private long newGoal(String user, String slug, String token) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_USERNAME, user);
		initialValues.put(KEY_SLUG, slug);
		initialValues.put(KEY_TOKEN, token);
		return mDb.insert(GOALS_TABLE, null, initialValues);
	}

	public long newGoalTag(long goal_id, long tag_id) throws Exception {
		ContentValues init = new ContentValues();
		init.put(KEY_GID, goal_id);
		init.put(KEY_TID, tag_id);
		return mDb.insertOrThrow(GOALTAGS_TABLE, null, init);
	}

	// round d to 5 sig digits
	public static double round5(double d) {
		return (java.lang.Math.round(d * 100000)) / 100000.0;
	}

	public boolean deleteGoal(long rowId) {
		return mDb.delete(GOALS_TABLE, KEY_ROWID + "=" + rowId, null) > 0;
	}

	public boolean isGoalTag(long gid, long tid) {
		Cursor c = mDb.query(GOALTAGS_TABLE, new String[] { KEY_ROWID }, KEY_GID + "=" + gid + " AND " + KEY_TID + "="
				+ tid, null, null, null, null);
		boolean ret = c.getCount() > 0;
		c.close();
		return ret;
	}

	public boolean deleteGoalTag(long goalId, long tagId) {
		String query = KEY_GID + "=" + goalId + " AND " + KEY_TID + "=" + tagId;
		return mDb.delete(GOALTAGS_TABLE, query, null) > 0;
	}

	public Cursor fetchAllGoals() {
		return mDb.query(GOALS_TABLE, new String[] { KEY_ROWID, KEY_USERNAME, KEY_SLUG, KEY_TOKEN }, null, null, null,
				null, null);
	}

	public Cursor fetchGoalTags(long id, String col_key, String order) {
		return mDb.query(true, GOALTAGS_TABLE, new String[] { KEY_GID, KEY_TID }, col_key + " = " + id, null, null,
				null, KEY_PID + " DESC", null);
	}

	public Cursor fetchGoalTags(long id, String col_key) {
		return mDb.query(true, GOALTAGS_TABLE, new String[] { KEY_GID, KEY_TID }, col_key + " = " + id, null, null,
				null, null, null);
	}

	public Cursor fetchGoal(long rowId) throws SQLException {
		Cursor pCursor = mDb.query(true, GOALS_TABLE, new String[] { KEY_ROWID, KEY_USERNAME, KEY_SLUG, KEY_TOKEN }, KEY_ROWID + "="
				+ rowId, null, null, null, null, null);
		if (pCursor != null) {
			pCursor.moveToFirst();
		}
		return pCursor;

	}

	public String fetchTagString(long goal_id) throws Exception {
		Cursor c = mDb.query(GOALTAGS_TABLE, new String[] {KEY_GID, KEY_TID},
				KEY_GID + "=" + goal_id, null, null, null, null);
		String s = "";
		PingsDbAdapter db = new PingsDbAdapter(mCtx);
		try {
			db.open();
			c.moveToFirst();
			int idx = c.getColumnIndex(KEY_TID);
			while (!c.isAfterLast()) {
				long tid = c.getLong(idx);
				String t = db.getTagName(tid);
				if (t.equals("")) {
					Exception e = new Exception("Could not find tag with id="+tid);
					throw e;
				}
				s += t+" ";
				c.moveToNext();
			}
		} finally {
			c.close();
			db.close();
		}
		return s;
	}

	public List<String> fetchTagsForGoal(long goal_id) throws Exception {
		Cursor c = fetchGoalTags(goal_id, KEY_GID);
		List<String> ret = new ArrayList<String>();
		c.moveToFirst();
		int idx = c.getColumnIndex(KEY_TID);
		PingsDbAdapter db = new PingsDbAdapter(mCtx);
		db.open();
		while (!c.isAfterLast()) {
			long tid = c.getLong(idx);
			String t = db.getTagName(tid);
			if (t.equals("")) {
				db.close();
				Exception e = new Exception("Could not find tag with id=" + tid);
				throw e;
			}
			ret.add(t);
			c.moveToNext();
		}
		c.close();
		db.close();
		return ret;
	}

	public boolean updateGoalTags(long goalId, List<String> newTags) {
		Log.i(TAG, "updateGoalTags()");
		// Remove all the old tags.
		List<String> cacheUpdates = new ArrayList<String>();
		try {
			cacheUpdates = fetchTagsForGoal(goalId);
		} catch (Exception e) {
			Log.i(TAG, "Some problem getting tags for this ping!");
			e.printStackTrace();
		}
		cacheUpdates.addAll(newTags);
		mDb.delete(GOALTAGS_TABLE, KEY_GID + "=" + goalId, null);
		PingsDbAdapter db = new PingsDbAdapter(mCtx);
		db.open();
		for (String t : newTags) {
			if (t.trim() == "") {
				continue;
			}
			long tid = db.getOrMakeNewTID(t);
			if (tid == -1) {
				Log.e(TAG, "ERROR: about to insert tid -1");
			}
			try {
				newGoalTag(goalId, tid);
			} catch (Exception e) {
				Log.i(TAG, "error inserting newGoalTag(" + goalId + "," + tid + ") in updateTaggings()");
			}
		}
		for (String tag : cacheUpdates) {
			db.updateTagCache(db.getTID(tag));
		}
		db.close();
		return true;
	}

}
