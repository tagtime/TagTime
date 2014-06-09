package bsoule.tagtime;

import android.util.Log;

public class DataBackupRestore {
	private static final String TAG = "DataBackupRestore";
	private static final boolean LOCAL_LOGV = false && !TagTime.DISABLE_LOGV;

	public static void saveBackup( String filename ) {
		if (LOCAL_LOGV) Log.v(TAG, "saveBackup(): "+filename);
	}
	public static void restoreBackup( String filename ) {
		if (LOCAL_LOGV) Log.v(TAG, "restoreBackup(): "+filename);
		
	}
}
