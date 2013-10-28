
package bsoule.tagtime;

import android.app.IntentService;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

public class BeeminderService extends IntentService {

	private static final String TAG = "BeeminderService";
	
	@Override
    protected void onHandleIntent(Intent workIntent) {
        // Gets data from the incoming Intent
        String dataString = workIntent.getDataString();
        Log.v(TAG, "onHandleIntent: New intent.");
        SystemClock.sleep(2000); // 30 seconds
	}

	public BeeminderService() {
		super(TAG);
	}
}
