package bsoule.tagtime;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

/* 
 * the Ping service is in charge of maintaining random number
 * generator state on disk, sending ping notifications, and
 * setting ping alarms.
 * 
 */

public class PingService extends Service {

	private static final String TAG = "PingService";
	private static final boolean LOCAL_LOGV = true && !TagTime.DISABLE_LOGV;

	private SharedPreferences mPrefs;
	private PingsDbAdapter pingsDB;
	private static PingService sInstance = null;

	// this gives a layout id which is a unique id
	private static int PING_NOTES = R.layout.tagtime_editping;

	public static final String KEY_NEXT = "nextping";
	public static final String KEY_SEED = "RNG_seed";
	private boolean mNotify;
	private int mGap;

	// seed is a variable that is really the state of the RNG.
	private static long SEED;
	private static long NEXT;

	private static final long RETROTHRESH = 60;

	public static PingService getInstance() {
		return sInstance;
	}

	// ////////////////////////////////////////////////////////////////////
	@Override
	public void onCreate() {
		if (LOCAL_LOGV) Log.v(TAG, "onCreate()");
		sInstance = this;
		PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
		PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pingservice");
		wl.acquire();

		Date launch = new Date();
		long launchTime = launch.getTime() / 1000;

		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		mNotify = mPrefs.getBoolean(TPController.KEY_RUNNING, true);

		NEXT = mPrefs.getLong(KEY_NEXT, -1);
		SEED = mPrefs.getLong(KEY_SEED, -1);

		try {
			mGap = Integer.parseInt(mPrefs.getString("pingGap", "45"));
		} catch (NumberFormatException e) {
			Log.w(TAG, "onCreate: Invalid gap: " + mPrefs.getString("pingGap", "not set"));
			mGap = 45;
		}

		// First do a quick check to see if next ping is still in the future...
		if (NEXT > launchTime) {
			// note: if we already set an alarm for this ping, it's
			// no big deal because this set will cancel the old one
			// ie the system enforces only one alarm at a time per setter
			setAlarm(NEXT);
			wl.release();
			this.stopSelf();
			return;
		}

		// If we make it here then it's time to do something
		// ---------------------
		if (NEXT == -1 || SEED == -1) { // then need to recalc from beg.
			NEXT = nextping(prevping(launchTime, mGap), mGap);
		}

		pingsDB = PingsDbAdapter.getInstance();
		pingsDB.openDatabase();

		// First, if we missed any pings by more than $retrothresh seconds for
		// no
		// apparent reason, then assume the computer was off and auto-log them.
		while (NEXT < launchTime - RETROTHRESH) {
			logPing(NEXT, "", Arrays.asList(new String[] { "OFF" }));
			NEXT = nextping(NEXT, mGap);
		}
		// Next, ping for any pings in the last retrothresh seconds.
		do {
			while (NEXT <= now()) {
				if (NEXT < now() - RETROTHRESH) {
					logPing(NEXT, "", Arrays.asList(new String[] { "OFF" }));
				} else {
					String tag = (mNotify) ? "" : "OFF";
					long rowID = logPing(NEXT, "", Arrays.asList(new String[] { tag }));
					sendNote(NEXT, rowID);
				}
				NEXT = nextping(NEXT, mGap);
			}
		} while (NEXT <= now());

		SharedPreferences.Editor editor = mPrefs.edit();
		editor.putLong(KEY_NEXT, NEXT);
		editor.putLong(KEY_SEED, SEED);
		editor.commit();

		setAlarm(NEXT);
		pingsDB.closeDatabase();
		wl.release();
		this.stopSelf();
	}

	private long logPing(long time, String notes, List<String> tags) {
		if (LOCAL_LOGV) Log.v(TAG, "logPing(" + tags + ")");
		return pingsDB.createPing(time, notes, tags, mGap);
	}

	// ////////////////////////////////////////////////////////////////////
	// just cuz original timepie uses unixtime, which is in seconds,
	// not universal time, which is in milliseconds
	private static long now() {
		long time = System.currentTimeMillis() / 1000;
		return time;
	}

	public void sendNote(long pingtime, long rowID) {

		if (!mNotify) return;

		NotificationManager NM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		SimpleDateFormat SDF = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault());

		Date ping = new Date(pingtime * 1000);
		CharSequence text = getText(R.string.status_bar_notes_ping_msg);

		// Set the icon, scrolling text, and timestamp.
		Notification note = new Notification(R.drawable.stat_ping, text, ping.getTime());

		// The PendingIntent to launch our activity if the user selects this
		// notification
		Intent editIntent = new Intent(this, EditPing.class);

		editIntent.putExtra(PingsDbAdapter.KEY_ROWID, rowID);
		editIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, editIntent, PendingIntent.FLAG_CANCEL_CURRENT);

		// Set the info for the views that show in the notification panel.
		// note.setLatestEventInfo(context, contentTitle, contentText,
		// contentIntent)
		note.setLatestEventInfo(this, "Ping!", SDF.format(ping), contentIntent);

		boolean suppress_noises = false;
		if (mPrefs.getBoolean("pingQuietCharging", false)) {
			IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
			Intent batteryStatus = registerReceiver(null, ifilter);
			suppress_noises = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
		}

		if (!suppress_noises) {
			if (mPrefs.getBoolean("pingVibrate", true)) {
				note.vibrate = new long[] { 0, 200, 50, 100, 50, 200, 50, 200, 50, 100 };
			}
			String sound_uri = mPrefs
					.getString("pingRingtonePref", Settings.System.DEFAULT_NOTIFICATION_URI.toString());
			if (!sound_uri.equals("")) {
				note.sound = Uri.parse(sound_uri);
			} else {
				// "Silent" choice returns uri="", so no defaults
				note.defaults = 0;
				// note.defaults |= Notification.DEFAULT_SOUND;
			}
		}

		if (mPrefs.getBoolean("pingLedFlash", false)) {
			note.ledARGB = 0xff0033ff;
			note.ledOffMS = 1000;
			note.ledOnMS = 200;
			note.flags |= Notification.FLAG_SHOW_LIGHTS;
		}

		note.flags |= Notification.FLAG_AUTO_CANCEL;

		// And finally, send the notification. The PING_NOTES const is a unique
		// id
		// that gets assigned to the notification (we happen to pull it from a
		// layout id
		// so that we can cancel the notification later on).
		NM.notify(PING_NOTES, note);

	}

	// TODO: RTC_WAKEUP and appropriate perms into manifest
	private void setAlarm(long PING) {
		AlarmManager alarum = (AlarmManager) getSystemService(ALARM_SERVICE);
		Intent alit = new Intent(this, TPStartUp.class);
		alit.putExtra("ThisIntentIsTPStartUpClass", true);
		alarum.set(AlarmManager.RTC_WAKEUP, PING * 1000, PendingIntent.getBroadcast(this, 0, alit, 0));
	}

	private static final long IA = 16807;
	private static final long IM = 2147483647;
	private static final long INITSEED = 666;

	/* *********************** *
	 * Random number generator * ***********************
	 */

	// Returns a random integer in [1,$IM-1]; changes $seed, ie, RNG state.
	// (This is ran0 from Numerical Recipes and has a period of ~2 billion.)
	private static long ran0() {
		SEED = IA * SEED % IM;
		return SEED;
	}

	// Returns a U(0,1) random number.
	private static double ran01() {
		return ran0() / (IM * 1.0);
	}

	// Returns a random number drawn from an exponential
	// distribution with mean gap. Gap is in minutes, we
	// want seconds, so multiply by 60.
	public static double exprand(int gap) {
		return -1 * gap * 60 * Math.log(ran01());
	}

	// Takes previous ping time, returns random next ping time (unix time).
	// NB: this has the side effect of changing the RNG state ($seed)
	// and so should only be called once per next ping to calculate,
	// after calling prevping.
	public static long nextping(long prev, int gap) {
		if (TPController.DEBUG) return now() + 60;
		return Math.max(prev + 1, Math.round(prev + exprand(gap)));
	}

	// Computes the last scheduled ping time before time t.
	public static long prevping(long t, int gap) {
		SEED = INITSEED;
		// Starting at the beginning of time, walk forward computing next pings
		// until the next ping is >= t.
		final int TUES = 1261198800; // some random time more recent than that..
		final int BOT = 1184083200; // start at the birth of timepie!
		long nxt = TPController.DEBUG ? TUES : BOT;
		long lst = nxt;
		long lstseed = SEED;
		while (nxt < t) {
			lst = nxt;
			lstseed = SEED;
			nxt = nextping(nxt, gap);
		}
		SEED = lstseed;
		return lst;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	private final IBinder mBinder = new Binder() {
		@Override
		protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
			return super.onTransact(code, data, reply, flags);
		}
	};
}
