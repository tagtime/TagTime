package bsoule.tagtime;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import java.util.Collections;

public class SavePingReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        long rowId = intent.getLongExtra(PingsDbAdapter.KEY_ROWID, 0);
        String tag = intent.getStringExtra(PingsDbAdapter.KEY_TAG);

        if (rowId != 0 && tag != null) {
            updateTagging(context, rowId, tag);
            beemindTag(context, rowId, tag);
            cancelNotif(context);
        } else {
            String msg = context.getString(R.string.ping_saving_failure, rowId, tag);
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        }
    }

    private void beemindTag(Context context, long rowId, String tag) {
        if (rowId >= 0) {
            Intent intent = new Intent(context, BeeminderService.class);
            intent.setAction(BeeminderService.ACTION_EDITPING);
            intent.putExtra(BeeminderService.KEY_PID, rowId);
            intent.putExtra(BeeminderService.KEY_OLDTAGS, "");
            intent.putExtra(BeeminderService.KEY_NEWTAGS, tag);
            context.startService(intent);
        }
        TagTime.broadcastPingUpdate(false);
    }

    private void cancelNotif(Context context) {
        NotificationManager notifManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assert notifManager != null;
        notifManager.cancel(R.layout.tagtime_editping);
    }

    private void updateTagging(Context context, long rowId, String tag) {
        PingsDbAdapter pingsDb = PingsDbAdapter.getInstance();
        pingsDb.openDatabase();
        pingsDb.updateTaggings(rowId, Collections.singletonList(tag));
        String msg = context.getString(R.string.ping_saving_success, rowId, tag);
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        pingsDb.closeDatabase();
    }
}
