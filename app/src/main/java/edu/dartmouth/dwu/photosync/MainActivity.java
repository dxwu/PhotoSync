package edu.dartmouth.dwu.photosync;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.OpenFileActivityBuilder;

import com.google.android.gms.drive.DriveApi.DriveContentsResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "MainActivity";

    protected GoogleApiClient mGoogleApiClient;

    protected static final int REQUEST_CODE_RESOLUTION = 1;
    private static final int REQUEST_CODE_OPENER = 2;
    private static final int REQUEST_CODE_CREATOR = 3;

    protected static final int RESOLVE_CONNECTION_REQUEST_CODE = 23;

    private static final String MIME_TYPE_IMAGE = "image/jpeg";

    // my "PhotoSync" folder in Google Drive
    private static DriveId sFolderId = DriveId.decodeFromString("DriveId:CAESHDBCNDdYelhpNkNIbHlXWFpUWjFoWVdsOUJkMFUYsiwg0picrdZUKAE=");

    private DriveId mFolderDriveId;

    // Drive ID of the currently opened Drive file.
    private DriveId mCurrentDriveId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .setAccountName("davidxiaohanwu@gmail.com")
                .build();

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, RESOLVE_CONNECTION_REQUEST_CODE);
            } catch (IntentSender.SendIntentException e) {
                Log.d(TAG, "could not connect: " + connectionResult.getErrorCode());
            }
        } else {
            Log.d(TAG, "could not connect: " + connectionResult.getErrorCode());
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case RESOLVE_CONNECTION_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    mGoogleApiClient.connect();
                }
                break;
            case REQUEST_CODE_OPENER:
                if (resultCode == RESULT_OK) {
                    mCurrentDriveId = (DriveId) data.getParcelableExtra(
                            OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);
                    get();
                }
                break;
            case REQUEST_CODE_CREATOR:
                if (resultCode == RESULT_OK) {
                    mCurrentDriveId = (DriveId) data.getParcelableExtra(
                            OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);
                }
                break;
            default:
                Log.d(TAG, "bad activity result");
        }
    }

    /**
     * Retrieves the currently selected Drive file's meta data and contents.
     */
    private void get() {
        Log.d(TAG, "Retrieving...");
        DriveFile file = mCurrentDriveId.asDriveFile();
        //DriveFolder file = mCurrentDriveId.asDriveFolder();

        View v = findViewById(R.id.textView1);
        ((TextView) v).setText(file.getDriveId().encodeToString());

        Log.d(TAG, "... " + file.getDriveId().encodeToString());
    }

    public void onConnectionSuspended(int cause) {}

    public void onConnected(Bundle connectionHint) {
        Drive.DriveApi.fetchDriveId(mGoogleApiClient, sFolderId.getResourceId())
                .setResultCallback(idCallback);
    }

    final private ResultCallback<DriveApi.DriveIdResult> idCallback = new ResultCallback<DriveApi.DriveIdResult>() {
        @Override
        public void onResult(DriveApi.DriveIdResult result) {
            if (!result.getStatus().isSuccess()) {
                Log.d(TAG,"Cannot find DriveId. Are you authorized to view this file?");
                return;
            }

            // get files
            String path = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera";
            File dir = new File(path);
            File files[] = dir.listFiles();

            for (final File f : files) {
                if (f.getName().endsWith(".jpg")) {
                    mFolderDriveId = result.getDriveId();
                    Drive.DriveApi.newDriveContents(mGoogleApiClient).setResultCallback(
                        new ResultCallback<DriveContentsResult>() {
                            @Override
                            public void onResult(DriveContentsResult result) {
                                if (!result.getStatus().isSuccess()) {
                                    Log.d(TAG, "Error while trying to create new file contents");
                                    return;
                                }

                                final DriveContents driveContents = result.getDriveContents();
                                OutputStream outputStream = driveContents.getOutputStream();


                                byte[] buf = new byte[8192];
                                InputStream is = null;
                                try {
                                    is = new FileInputStream(f);
                                } catch (FileNotFoundException e) {
                                    Log.d(TAG, "File not found! " + e);
                                }

                                // write content to DriveContents
                                try {
                                    int c = 0;
                                    while ((c = is.read(buf, 0, buf.length)) > 0) {
                                        outputStream.write(buf, 0, c);
                                        outputStream.flush();
                                    }
                                } catch (IOException e) {
                                    Log.e(TAG, e.getMessage());
                                } finally {
                                    try {
                                        outputStream.close();
                                    } catch (Exception e) {

                                    }
                                }

                                DriveFolder folder = sFolderId.asDriveFolder();
                                MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                        .setTitle(f.getName())
                                        .setMimeType("image/jpg")
                                        .setStarred(true).build();
                                folder.createFile(mGoogleApiClient, changeSet, driveContents)
                                        .setResultCallback(fileCallback);
                            }
                        }
                    );
                }
            }
        }
    };

    final private ResultCallback<DriveFolder.DriveFileResult> fileCallback = new
            ResultCallback<DriveFolder.DriveFileResult>() {
                @Override
                public void onResult(DriveFolder.DriveFileResult result) {
                    if (!result.getStatus().isSuccess()) {
                        Log.d(TAG, "File create failed.");
                        return;
                    }
                    Log.d(TAG, "File create success.");
                }
            };

    public void readFile(View view) {
        mGoogleApiClient.connect();
        IntentSender i = Drive.DriveApi
                .newOpenFileActivityBuilder()
                .setMimeType(new String[] { MIME_TYPE_IMAGE })
                .build(mGoogleApiClient);
        try {
            startIntentSenderForResult(i, REQUEST_CODE_OPENER, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
            Log.w(TAG, "Unable to send intent", e);
        }
    }

    public void writeFile(View view) {
        ResultCallback<DriveApi.DriveContentsResult> onContentsCallback =
            new ResultCallback<DriveApi.DriveContentsResult>() {
                @Override
                public void onResult(DriveApi.DriveContentsResult result) {
                    if (!result.getStatus().isSuccess()) {
                        Log.d(TAG, "File create failed.");
                        return;
                    }

                    MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
                            .setMimeType(MIME_TYPE_IMAGE).build();
                    IntentSender createIntentSender = Drive.DriveApi
                            .newCreateFileActivityBuilder()
                            .setInitialMetadata(metadataChangeSet)
                            .setInitialDriveContents(result.getDriveContents())
                            .build(mGoogleApiClient);
                    try {
                        startIntentSenderForResult(createIntentSender, REQUEST_CODE_CREATOR, null,
                                0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        Log.w(TAG, "Unable to send intent", e);
                    }
                }
            };
    }


}
