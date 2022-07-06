// Copyright (c) 2018 Sift Science. All rights reserved.

package siftscience.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.sift.api.representations.AndroidAppStateJson;
import com.sift.api.representations.AndroidDeviceLocationJson;
import com.sift.api.representations.MobileEventJson;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Collects App State events.
 */
public class AppStateCollector {
    private static final String TAG = AppStateCollector.class.getSimpleName();
    private final SiftImpl sift;
    private final Context context;

    private String activityClassName;
    private boolean acquiredNewLocation;
    private LocationRequest locationRequest;
    private FusedLocationProviderClient mFusedLocationClient;
    private SettingsClient mSettingsClient;
    private LocationSettingsRequest mLocationSettingsRequest;
    private LocationCallback mLocationCallback;
    private boolean mRequestingLocationUpdates = false;

    private Location location;
    private Location lastLocation;

    public AppStateCollector(SiftImpl sift, Context context) {
        this.sift = sift;
        this.context = context.getApplicationContext();

        this.acquiredNewLocation = false;
        if (!sift.getConfig().disallowLocationCollection) {
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this.context);
            mSettingsClient = LocationServices.getSettingsClient(this.context);
            createLocationCallback();
            createLocationRequest();
            buildLocationSettingsRequest();
        }
    }

    public void setActivityName(String activityName) {
        this.activityClassName = activityName;
    }

    public void collect() {
        if (!sift.getConfig().disallowLocationCollection &&
                this.mFusedLocationClient != null &&
                !mRequestingLocationUpdates) {
            startLocationUpdates();
        } else {
            this.doCollect();
        }
    }

    private void createLocationRequest() {
        this.locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(TimeUnit.MINUTES.toMillis(1))
                .setFastestInterval(TimeUnit.SECONDS.toMillis(10));
    }

    private void createLocationCallback() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                if (locationResult == null) {
                    return;
                }
                Log.d(TAG, "Location changed");
                acquiredNewLocation = true;
                location = locationResult.getLastLocation();
                doCollect();

                try {
                    if (!sift.getConfig().disallowLocationCollection
                            && mFusedLocationClient != null) {
                        disconnectLocationServices();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Encountered exception in onLocationChanged", e);
                }
            }
        };
    }

    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        mLocationSettingsRequest = builder.build();
    }

    public void disconnectLocationServices() {
        Log.d(TAG, "Disconnect location services");

        try {
            if (!this.sift.getConfig().disallowLocationCollection &&
                    this.mFusedLocationClient != null && mRequestingLocationUpdates) {
                Log.d(TAG, "Removing location updates");
                mFusedLocationClient.removeLocationUpdates(mLocationCallback)
                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                mRequestingLocationUpdates = false;
                            }
                        });
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    public void reconnectLocationServices() {
        Log.d(TAG, "Connect location services");

        try {
            if (!this.sift.getConfig().disallowLocationCollection && this.mFusedLocationClient != null
                    && !mRequestingLocationUpdates) {
                startLocationUpdates();
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private void doCollect() {
        String installationId = Settings.Secure.getString(this.context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        this.sift.appendAppStateEvent(
                new MobileEventJson()
                        .withAndroidAppState(this.get())
                        .withInstallationId(installationId)
                        .withTime(Time.now()));
    }

    private AndroidAppStateJson get() {
        Intent batteryStatus = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        int level = batteryStatus != null ?
                batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
        int scale = batteryStatus != null ?
                batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : -1;
        double batteryLevel = -1;
        if (level != -1 && scale != -1) {
            batteryLevel = (double) level / scale;
        }

        // unknown=1, charging=2, discharging=3, not charging=4, full=5
        int status = batteryStatus != null ?
                batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1) : -1;
        // ac=1, usb=2, wireless=4
        int plugState = batteryStatus != null ?
                batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) : -1;
        // unknown=1, good=2, overheat=3, dead=4, over voltage=5, unspecified failure=6, cold=7
        int health = batteryStatus != null ?
                batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) : -1;

        List<String> ipAddresses = getIpAddresses();

        AndroidAppStateJson androidAppStateJson = new AndroidAppStateJson()
                .withActivityClassName(this.activityClassName)
                .withBatteryLevel(batteryLevel)
                .withBatteryState((long) status)
                .withBatteryHealth((long) health)
                .withPlugState((long) plugState)
                .withNetworkAddresses(ipAddresses)
                .withSdkVersion(Sift.SDK_VERSION);

        if (this.hasLocation()) {
            androidAppStateJson.withLocation(this.getLocation());
        }

        return androidAppStateJson;
    }

    private List<String> getIpAddresses() {
        List<String> addresses = new ArrayList<>();
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en != null && en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
                     enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        String address = inetAddress.getHostAddress().toLowerCase(Locale.US);
                        if (address.indexOf('%') > -1) { // Truncate zone in IPv6 if present
                            address = address.substring(0, address.indexOf('%'));
                        }
                        addresses.add(address);
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, e.toString());
        }
        return addresses;
    }

    private boolean hasLocation() {
        return this.location != null || this.lastLocation != null;
    }

    private AndroidDeviceLocationJson getLocation() {
        Log.d(TAG, "Using " + (this.acquiredNewLocation ?
                "new location" : "last location"));

        Location location = this.acquiredNewLocation ? this.location : this.lastLocation;

        return new AndroidDeviceLocationJson()
                .withLatitude(location.getLatitude())
                .withLongitude(location.getLongitude());
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        if (checkPermissions()) {
            mFusedLocationClient.getLastLocation()
                    .addOnSuccessListener(new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            Log.d(TAG, "Got last known location: " + location);
                            // Got last known location. In some rare situations this can be null.
                            if (location != null) {
                                lastLocation = location;
                            }
                        }
                    });

            mRequestingLocationUpdates = true;
            // Begin by checking if the device has the necessary location settings.
            mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
                    .addOnSuccessListener(new OnSuccessListener<LocationSettingsResponse>() {
                        @SuppressLint("MissingPermission")
                        @Override
                        public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                            Log.i(TAG, "All location settings are satisfied.");
                            mFusedLocationClient.requestLocationUpdates(locationRequest,
                                    mLocationCallback, Looper.myLooper());
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            int statusCode = ((ApiException) e).getStatusCode();
                            switch (statusCode) {
                                case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                    Log.i(TAG, "Location settings are not satisfied. " +
                                            "Try to attempt upgrade location settings");
                                    break;
                                case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                    Log.i(TAG, "Location settings are inadequate, and cannot be " +
                                            "fixed here. Fix in Settings.");
                                    mRequestingLocationUpdates = false;
                            }
                        }
                    });
        } else {
            this.doCollect();
        }
    }

    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(this.context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this.context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
