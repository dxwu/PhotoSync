package edu.dartmouth.dwu.photosync;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
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
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.OpenFileActivityBuilder;

import com.google.android.gms.drive.DriveApi.DriveContentsResult;
import com.google.android.gms.drive.query.Filters;

import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;
import com.google.android.gms.drive.widget.DataBufferAdapter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;


public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "MainActivity";

    protected GoogleApiClient mGoogleApiClient;

    protected static final int RESOLVE_CONNECTION_REQUEST_CODE = 23;

    private static final String MIME_TYPE_IMAGE = "image/jpeg";

    // my "PhotoSync" folder in Google Drive
    private static DriveId sFolderId = DriveId.decodeFromString("DriveId:CAESHDBCNDdYelhpNkNIbHlXWFpUWjFoWVdsOUJkMFUYsiwg0picrdZUKAE=");

    private static final int REQUEST_CODE_OPENER = 2;

    private DriveId mFolderDriveId;

    // Drive ID of the currently opened Drive file.
    private DriveId mCurrentDriveId;

    public static final String DCIM_PATH = "/DCIM/Camera";

    private final String logFilename = "/uploadedPictures_photoSync.txt";

    // log file for uploaded pictures
    private File logFile;

    CameraReceiver mReceiver;

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

        // register receiver
        Intent intent = new Intent("com.android.camera.NEW_PICTURE");
        CameraReceiver mReceiver = new CameraReceiver();
        sendBroadcast(intent);

        // register receiver listener
        registerReceiver(PhotoSyncResponseReceiver, new IntentFilter(CameraReceiver.PHOTOSYNC_INTENT_FILTER));
    }

    BroadcastReceiver PhotoSyncResponseReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String filename = intent.getStringExtra(CameraReceiver.PHOTOSYNC_FILENAME);
            String filepath = Environment.getExternalStorageDirectory().toString() + MainActivity.DCIM_PATH + "/" + filename;
            File f = new File(filepath);
            uploadToDrive(f);
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();

        // init log file if necessary
        PackageManager m = getPackageManager();
        String path = getPackageName();

        PackageInfo p = null;
        try {
            p = m.getPackageInfo(path, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Error: package not found");
        }
        path = p.applicationInfo.dataDir;

        logFile = new File(path + logFilename);

        if (logFile.exists()) {
            Log.d(TAG, "log file exists");

        } else {
            Log.d(TAG, "writing new log file");
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // broadcast receiver is started in AndroidManifest
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(PhotoSyncResponseReceiver);
        unregisterReceiver(mReceiver);
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

            ArrayList<String> justUploadedFiles = new ArrayList<String>();

            // get files
            String path = Environment.getExternalStorageDirectory().toString() + DCIM_PATH;
            File imagesDirectory = new File(path);
            File files[] = imagesDirectory.listFiles();

            // get new files to upload
            HashSet<String> uploadedFiles = new  HashSet<String>();
            try {
                BufferedReader br = new BufferedReader(new FileReader(logFile));
                String line;

                while ((line = br.readLine()) != null) {
                    uploadedFiles.add(line);
                }
                br.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }

            for (final File f : files) {
                if (f.getName().endsWith(".jpg")) {
                    if (uploadedFiles.contains(f.getName())) {      // only write files we haven't written before
                        continue;
                    }

                    justUploadedFiles.add(f.getName());

                    mFolderDriveId = result.getDriveId();
                    uploadToDrive(f);
                }
            } // end for (file : files)

            for (String sf : justUploadedFiles) {
              writeToLogFile(sf);
            }

        }
    };

    public void writeToLogFile(String filename) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(logFile, true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            fos.write(filename.getBytes());
            fos.write('\n');
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void uploadToDrive(final File f) {
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

                        Log.d(TAG, "uploaded file " + f.getName());
                    }
                }
        );

        writeToLogFile(f.getName());
    }

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

    public void writeFile(View view) {
    }

    public void applyDeletions(View view) {
        // check for matches between Drive trash and phone storage

//        Query query = new Query.Builder()
//                .addFilter(Filters.and(
//                Filters.contains(SearchableField.TITLE,"IMG")
//                //Filters.eq(SearchableField.MIME_TYPE, "image/jpg")
//                //,Filters.eq(SearchableField.TRASHED, true)
//                )).build();

        final Query query = new Query.Builder().addFilter(Filters.contains(SearchableField.TITLE, "1")).build();

        // we'll probably have to call the REST api
        new Thread() {
            @Override
            public void run() {
                Drive.DriveApi.requestSync(mGoogleApiClient);
                MetadataBuffer mds = Drive.DriveApi.query(mGoogleApiClient, query).await().getMetadataBuffer();

                Iterator<Metadata> itr = mds.iterator();
                while (itr.hasNext()) {
                    Metadata m = itr.next();
                    Log.d(TAG, "title: " + m.getTitle() + ", trashed: " + m.isTrashed() + ", mime type: " + m.getMimeType());
                    DriveFile df = m.getDriveId().asDriveFile();
                }

                mds.release();
            }
        }.start();


        // prompt user

        // write toast
    }

}
