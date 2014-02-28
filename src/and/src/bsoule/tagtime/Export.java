package bsoule.tagtime;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;

public class Export extends SherlockActivity {
	public static final String TAG = "TPExport";
	private PingsDbAdapter mDb;
	private BeeminderDbAdapter mBeeDb;

	//private static final int DIALOG_PROGRESS = 0;
	private static final int DIALOG_NOMOUNT = 1;
	private static final int DIALOG_CANTSENDMAIL = 2;
	private static final int DIALOG_CANTWRITEFILE = 3;
	private static final int DIALOG_DONE = 4;
	private static final int DIALOG_DELETE_DATA = 5;
	private static final int DIALOG_REALLY = 6;
	private static final int DIALOG_CLEANUP_TAGS = 7;
	private static final String FNAME = "timepie.log";

	ActionBar mAction;
	
	SharedPreferences mPrefs;
	ProgressDialog mProgress;
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tagtime_export);

		mAction = getSupportActionBar();
		mAction.setHomeButtonEnabled(true);
		mAction.setIcon(R.drawable.tagtime_03);
		
		mDb = PingsDbAdapter.getInstance();
		mDb.openDatabase();
		mBeeDb = BeeminderDbAdapter.getInstance();
		mBeeDb.openDatabase();
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		Button doSD = (Button) findViewById(R.id.export_sd);
		doSD.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
					//showDialog(DIALOG_PROGRESS);
					writeFile(Environment.getExternalStorageDirectory(), FNAME, getLogData().toString());
					showDialog(DIALOG_DONE);
				} else {
					showDialog(DIALOG_NOMOUNT);
				}
			}
		});
		Button doEmail = (Button) findViewById(R.id.export_eml);
		doEmail.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				startEmail();
			}
		});
		Button doDeleteLog = (Button) findViewById(R.id.delete_logs);
		doDeleteLog.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				deleteLogs();
				showDialog(DIALOG_DONE);
			}
		});
		Button doDeleteData = (Button) findViewById(R.id.delete_data);
		doDeleteData.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				showDialog(DIALOG_DELETE_DATA);
			}
		});
		Button doCleanupTags = (Button) findViewById(R.id.cleanup_tags);
		doCleanupTags.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				showDialog(DIALOG_CLEANUP_TAGS);
			}
		});

		Button doPingNow = (Button) findViewById(R.id.ping_now);
		doPingNow.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				PingService x = PingService.getInstance();
				if (x != null) {
					long timex = System.currentTimeMillis()/1000;
					x.sendNote(timex, mDb.createPing(timex, "", Arrays.asList(new String[]{""}), 0));
				}
			}
		});
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		String title = "";
		String body = "";
		switch (id) {
		case DIALOG_NOMOUNT:
			title = "No SD card mounted!";
			break;
		case DIALOG_CANTWRITEFILE:
			title = "Error writing file.";
			body = "You may have run out of space on your device. Sorry!";
			break;
		case DIALOG_CANTSENDMAIL:
			title = "Can't start email.";
			body = "I'm having trouble creating an email. Please check that you have an email account set up on this device.";
			break;
		case DIALOG_DONE:
			title = "Done!";
			break;
		case DIALOG_DELETE_DATA:
			return new AlertDialog.Builder(Export.this)
			.setTitle("WAIT!")
			.setMessage("You are about to delete ALL of your timepie data. This cannot be undone! Are you really really really sure?")
			.setPositiveButton("Yes Really!", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					showDialog(DIALOG_REALLY);
				}
			})
			.setNegativeButton("Eeek! No!", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {}
			}).create();
		case DIALOG_REALLY:
			return new AlertDialog.Builder(Export.this)
			.setTitle("Really?")
			.setPositiveButton("Goodness me no.",new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {}
			})
			.setNegativeButton("Yes, GEEZ!",new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					deleteData();
					showDialog(DIALOG_DONE);
				}
			}).create();
		case DIALOG_CLEANUP_TAGS:
			return new AlertDialog.Builder(Export.this)
			.setTitle("Clean up unused tags?")
			.setPositiveButton("Clean", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					cleanupUnusedTags();
					showDialog(DIALOG_DONE);
				}
			}).create();
		}
		AlertDialog.Builder adb = new AlertDialog.Builder(Export.this);
		adb.setTitle(title);
		if (!body.equals("")) adb.setMessage(body);
		return adb.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {}
		}).create();

	}

	private void deleteLogs() {
		File log;
		// delete log file from SD card provided it is mounted and file exists
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			log = new File(Environment.getExternalStorageDirectory(),FNAME);
			Log.i(TAG,"Absolute path SD: "+log.getAbsolutePath());
			if (log.exists()) {
				if (log.delete()) 
					Log.i(TAG,"deleted SD/log");
				else 
					Log.i(TAG,"failed to delete SD/log");
			}
		}
		// delete local(data) log if exists
		log = new File("data/data/bsoule.tagtime/files/"+FNAME);
		if (log.exists()) {
			if (log.delete())
				Log.i(TAG,"deleted data/log");
			else
				Log.i(TAG,"failed to delete data/log");
		}

	}

	private void deleteData() {
		mDb.deleteAllData();
		mBeeDb.deleteAllData();
		SharedPreferences.Editor ed = mPrefs.edit();
		ed.remove(PingService.KEY_NEXT);
		ed.remove(PingService.KEY_SEED);
		ed.commit();
	}

	private void cleanupUnusedTags() {
		mDb.cleanupUnusedTags();
	}

	private Dialog progressDialog() {
		mProgress = new ProgressDialog(Export.this);
		mProgress.setIcon(R.drawable.alert_dialog_icon);
		mProgress.setTitle(R.string.saving_log);
		mProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		mProgress.setButton("Hide", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				// TODO Just make dialog go away (Do I need to do anything?)
			}
		});
		mProgress.setButton2("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				// TODO Cancel the save	
			}
		});
		return mProgress;
	}

	private void startEmail() {
		writeFileTemp(FNAME, getLogData().toString());
		//writeFile(Environment.getExternalStorageDirectory(), FNAME, getLogData().toString());
		
		Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
		//emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "TESTING EMAIL");
		emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Timepie: your timepie log");
		//emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, "This is a test "+new Date().getTime());
		//emailIntent.putExtra(android.content.Intent.EXTRA_STREAM, Uri.parse("file:///sdcard/"+FNAME));
		emailIntent.putExtra(android.content.Intent.EXTRA_STREAM, Uri.parse("file:///data/data/bsoule.tagtime/files/"+FNAME));
		//emailIntent.setType("text/plain");
		emailIntent.setType("application/octet-stream");		
		
		try {
			startActivity(emailIntent);
		} catch (Exception e) {
			Log.e(TAG,e.getMessage());
			Log.e(TAG,e.toString());
			showDialog(DIALOG_CANTSENDMAIL);
		}
	}

	private StringBuilder getLogData() {
		SimpleDateFormat SDF = new SimpleDateFormat("[yyyy.MM.dd HH:mm:ss EEE]", Locale.getDefault());
		StringBuilder log = new StringBuilder();

		Cursor pings = mDb.fetchAllPings(false);
		startManagingCursor(pings);
		pings.moveToFirst();
		if (pings.isAfterLast()) { return log.append(this.getString(R.string.nodata)); }
		while (!pings.isAfterLast()) {
			try {
				long pt = pings.getLong(pings.getColumnIndexOrThrow(PingsDbAdapter.KEY_PING));
				String tags = mDb.fetchTagString(pings.getLong(pings.getColumnIndexOrThrow(PingsDbAdapter.KEY_ROWID)));
				log.append(pt+" "+tags+" "+SDF.format(new Date(pt*1000))+"\n");
			} catch (Exception e) {
				Log.e(TAG, "&&&&&&&&&&&& getLogString: "+e.getMessage());
			}
			pings.moveToNext();
		}
		return log;
	}

	private void writeFileTemp(String fname, String filedata) {
		try {
			FileOutputStream fos = this.openFileOutput(fname, MODE_WORLD_READABLE);
			OutputStreamWriter out_stream = new OutputStreamWriter(new 
					BufferedOutputStream(fos));
			out_stream.write(filedata);
			out_stream.close();
			fos.close();
		} catch (Exception e) {
			showDialog(DIALOG_CANTWRITEFILE);
		}
	}

	private void writeFile(File fpath, String fname, String filedata) {
		try {
			File log = new File(fpath, fname);
			log.createNewFile();
			FileOutputStream file_out = new FileOutputStream(log);
			OutputStreamWriter out_stream = new OutputStreamWriter(new 
					BufferedOutputStream(file_out));
			try {
				out_stream.write(filedata);
			} catch (Exception e) {
				Log.e(TAG,"&&&&&&&&&&&& writeFile() write: "+e.getMessage());
				showDialog(DIALOG_CANTWRITEFILE);
			}
			out_stream.close();
			file_out.close();
		} catch (Exception e) {
			Log.e(TAG,"&&&&&&&&&&&& writeFile() open: "+e.getMessage());
			showDialog(DIALOG_CANTWRITEFILE);
		}
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		mDb.closeDatabase();
		mBeeDb.closeDatabase();
	}

	/** Handles menu item selections */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case android.R.id.home:
			// app icon in action bar clicked; go home
			Intent intent = new Intent(this, TPController.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
}
