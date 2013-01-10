package com.geoloqi.android.volta.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import com.geoloqi.android.volta.R;
import com.geoloqi.android.volta.receiver.SampleReceiver;
import com.geoloqi.android.sdk.LQSharedPreferences;
import com.geoloqi.android.sdk.LQTracker;
import com.geoloqi.android.sdk.LQTracker.LQTrackerProfile;
import com.geoloqi.android.sdk.provider.LQDatabaseHelper;
import com.geoloqi.android.sdk.receiver.LQBroadcastReceiver;
import com.geoloqi.android.sdk.service.LQService;
import com.geoloqi.android.sdk.service.LQService.LQBinder;
import com.geoloqi.android.volta.VoltaService;
import com.geoloqi.android.volta.VoltaService.VoltaBinder;

/**
 * <p>This is the main {@link Activity} for the Geoloqi Sample Android
 * app. It starts up and binds to the {@link LQService} tracker. It also
 * registers to receive broadcasts from the tracker using the
 * interfaces defined on the {@link SampleReceiver}.</p>
 * 
 * @author Tristan Waddington
 */
public class LauncherActivity extends Activity implements SampleReceiver.OnLocationChangedListener,
        SampleReceiver.OnTrackerProfileChangedListener, SampleReceiver.OnLocationUploadedListener,
        AdapterView.OnItemSelectedListener {
    public static final String TAG = "LauncherActivity";

    private LQService mLqService;
    private boolean mLqServiceBound;
    private SampleReceiver mLocationReceiver = new SampleReceiver();

    private VoltaService mVoltaService;
    private boolean mVoltaServiceBound;

    private boolean mTestInProgress = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Set up profile spinner
        Spinner spinner = (Spinner) findViewById(R.id.profile_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.tracker_profile_entries, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);

        // Set up start/stop button
        Button button = (Button) findViewById(R.id.start_stop_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleTest();
            }
        });
        
        // Start the tracking service
        Intent lqIntent = new Intent(this, LQService.class);
        startService(lqIntent);

        LQSharedPreferences.disablePushNotificationHandling(this);

        // Start the Volta service
        Intent vIntent = new Intent(this, VoltaService.class);
        startService(vIntent);
    }

    @Override
    public void onResume() {
        super.onResume();
        
        // Bind to the tracking service so we can call public methods on it
        Intent lqIntent = new Intent(this, LQService.class);
        bindService(lqIntent, mLQServiceConnection, 0);

        // Bind to the tracking service so we can call public methods on it
        Intent vIntent = new Intent(this, VoltaService.class);
        bindService(vIntent, mVoltaServiceConnection, 0);

        // Wire up the sample location receiver
        registerReceiver(mLocationReceiver,
                LQBroadcastReceiver.getDefaultIntentFilter());
    }

    @Override
    public void onPause() {
        super.onPause();
        
        // Unbind from LQService
        if (mLqServiceBound) {
            unbindService(mLQServiceConnection);
            mLqServiceBound = false;
        }

        // Unbind from VoltaService
        if (mVoltaServiceBound) {
            unbindService(mVoltaServiceConnection);
            mVoltaServiceBound = false;
        }
        
        // Unregister our location receiver
        unregisterReceiver(mLocationReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case R.id.settings:
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return false;
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        LQTrackerProfile profile = LQTrackerProfile.values()[i];
        Log.d(getString(R.string.app_name),
                "Profile selected: '" + profile.name() + "'" );
        mLqService.getTracker().setProfile(profile);
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        // Pass
    }

    public void toggleTest() {
        if (mVoltaServiceBound) {
            if (!mTestInProgress) {
                // Start
                mVoltaService.startTest(123456, 2);
            } else {
                // Stop
                mVoltaService.stopTest();
            }
            mTestInProgress = !mTestInProgress;
            Button button = (Button) findViewById(R.id.start_stop_button);
            button.setText(mTestInProgress ? "Stop Test" : "Start Test");
        } else {
           Toast.makeText(this, "VoltaService not bound!", Toast.LENGTH_LONG).show();
        }
    }
    /**
     * Display the number of batched location fixes waiting to be sent.
     */
    private void showBatchedLocationCount() {
        TextView updates = (TextView) findViewById(R.id.batched_updates);
        if (updates != null) {
            final LQTracker tracker = mLqService.getTracker();
            final LQDatabaseHelper helper = new LQDatabaseHelper(this);
            final SQLiteDatabase db = helper.getWritableDatabase();
            final Cursor c = tracker.getBatchedLocationFixes(db);
            updates.setText(String.format("%d batched updates",
                            c.getCount()));
            c.close();
            db.close();
        }
    }

    /**
     * Display the values from the last recorded location fix.
     * @param location
     */
    private void showCurrentLocation(Location location) {
        TextView latitudeView = (TextView) findViewById(R.id.location_lat);
        if (latitudeView != null) {
            latitudeView.setText(Double.toString(location.getLatitude()));
        }
        
        TextView longitudeView = (TextView) findViewById(R.id.location_long);
        if (longitudeView != null) {
            longitudeView.setText(Double.toString(location.getLongitude()));
        }
        
        TextView accuracyView = (TextView) findViewById(R.id.location_accuracy);
        if (accuracyView != null) {
            accuracyView.setText(String.valueOf(location.getAccuracy()));
        }
        
        TextView speedView = (TextView) findViewById(R.id.location_speed);
        if (speedView != null) {
            speedView.setText(String.format("%.2f km/h", (location.getSpeed() * 3.6)));
        }
        
        TextView providerView = (TextView) findViewById(R.id.location_provider);
        if (providerView != null) {
            providerView.setText(location.getProvider());
        }
    }

    /** Defines callbacks for LQService binding, passed to bindService() */
    private ServiceConnection mLQServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                LQBinder binder = (LQBinder) service;
                mLqService = binder.getService();
                mLqServiceBound = true;

                // Display the current tracker profile
                Spinner spinner = (Spinner) findViewById(R.id.profile_spinner);
                if (spinner != null) {
                    spinner.setSelection(mLqService.getTracker().getProfile().ordinal());
                }
            } catch (ClassCastException e) {
                // Pass
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mLqServiceBound = false;
        }
    };

    /** Defines callbacks for VoltaService binding, passed to bindService() */
    private ServiceConnection mVoltaServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                // We've bound to LocalService, cast the IBinder and get LocalService instance.
                VoltaBinder binder = (VoltaBinder) service;
                mVoltaService = binder.getService();
                mVoltaServiceBound = true;
            } catch (ClassCastException e) {
                // Pass
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mVoltaServiceBound = false;
        }
    };

    @Override
    public void onTrackerProfileChanged(LQTrackerProfile oldProfile,
                    LQTrackerProfile newProfile) {
        // Display the current tracker profile
        Spinner spinner = (Spinner) findViewById(R.id.profile_spinner);
        if (spinner != null) {
            spinner.setSelection(newProfile.ordinal());
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        showBatchedLocationCount();
        showCurrentLocation(location);
    }

    @Override
    public void onLocationUploaded(int count) {
        showBatchedLocationCount();
    }
}