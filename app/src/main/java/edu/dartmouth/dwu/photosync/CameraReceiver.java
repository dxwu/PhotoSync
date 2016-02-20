package edu.dartmouth.dwu.photosync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;

// broadcast receiver is started in AndroidManifest

public class CameraReceiver extends BroadcastReceiver {
    private final String TAG = "CameraReceiver";
    public static final String PHOTOSYNC_INTENT_FILTER = "PHOTOSYNC_PICTURE_TAKEN";
    public static final String PHOTOSYNC_FILENAME = "PHOTOSYNC_FILENAME";


    public CameraReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        //Log.d(TAG, "new picture taken");


        Uri uri = intent.getData();
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        cursor.moveToFirst();
        int i = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
        String path = cursor.getString(i);

        //Log.d(TAG, "path: " + path);

        String[] split = path.split("/");
        String filename = split[split.length-1];

        //Log.d(TAG, "filename: " + filename);

        // send back to main activty
        Intent uploadIntent = new Intent(PHOTOSYNC_INTENT_FILTER);
        uploadIntent.putExtra(PHOTOSYNC_FILENAME, filename);
        context.sendBroadcast(uploadIntent);

        cursor.close();
    }
}
