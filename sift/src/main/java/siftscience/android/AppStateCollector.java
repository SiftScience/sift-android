package siftscience.android;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
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
import java.util.concurrent.TimeUnit;

/**
 * Collectors for AndroidAppState
 */
public class AppStateCollector implements LocationListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = AppStateCollector.class.getSimpleName();
    private static final String SDK_VERSION = "0.0";
    private final Sift sift;
    private final Context context;

    private GoogleApiClient googleApiClient;
    private LocationRequest locationRequest;

    private Location lastLocation;

    public AppStateCollector(Sift sift, Context context) {
        this.sift = sift;
        this.context = context;

        Log.d(TAG, "Connect location services");
        try {
            this.googleApiClient = new GoogleApiClient.Builder(this.context)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            this.googleApiClient.connect();
        } catch(Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    public void collect() {
        String installationId = Settings.Secure.getString(this.context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        this.sift.getQueue(Sift.APP_STATE_QUEUE_IDENTIFIER).append(
                MobileEventJson.newBuilder()
                        .withAndroidAppState(this.get())
                        .withInstallationId(installationId)
                        .withTime(Time.now())
                        .build());
    }

    public void disconnectLocationServices() {
        Log.d(TAG, "Disconnect location services");

        try {
            if (this.googleApiClient.isConnected()) {
                this.googleApiClient.disconnect();
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    public void reconnectLocationServices() {
        Log.d(TAG, "Reconnect location services");

        try {
            this.googleApiClient.connect();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private AndroidAppStateJson get() {
        Intent batteryStatus = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        double batteryLevel = -1;
        if (level != -1 && scale != -1) {
            batteryLevel = (double) level / scale;
        }

        // unknown=1, charging=2, discharging=3, not charging=4, full=5
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        // ac=1, usb=2, wireless=4
        int plugState = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        // unknown=1, good=2, overheat=3, dead=4, over voltage=5, unspecified failure=6, cold=7
        int health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);

        List<String> ipAddresses = getIpAddresses();

        AndroidAppStateJson.Builder builder = AndroidAppStateJson.newBuilder()
                .withActivityClassName(this.context.getClass().getSimpleName())
                .withBatteryLevel(batteryLevel)
                .withBatteryState((long) status)
                .withBatteryHealth((long) health)
                .withPlugState((long) plugState)
                .withNetworkAddresses(ipAddresses);

        if (this.lastLocation != null) {
            return builder.withLocation(this.getLocation()).build();
        }

        return builder.build();
    }

    private List<String> getIpAddresses() {
        List<String> addresses = new ArrayList<>();
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                    en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
                     enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        String address = inetAddress.getHostAddress().toLowerCase();
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

    private AndroidDeviceLocationJson getLocation() {
        return AndroidDeviceLocationJson.newBuilder()
                .withTime(this.lastLocation.getTime())
                .withLatitude(this.lastLocation.getLatitude())
                .withLongitude(this.lastLocation.getLongitude())
                .withAccuracy(new BigDecimal(this.lastLocation.getAccuracy()).doubleValue())
                .build();
    }

    private void requestLocation() {
        Log.d(TAG, "Requested location");

        this.locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setInterval(TimeUnit.MINUTES.toMillis(1))
                .setFastestInterval(TimeUnit.SECONDS.toMillis(10));

        if (ActivityCompat.checkSelfPermission(this.context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this.context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(this.googleApiClient,
                    this.locationRequest, this);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "Location changed");

        this.lastLocation = location;
        LocationServices.FusedLocationApi.removeLocationUpdates(this.googleApiClient, this);

        this.collect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "Connected to Google API Client");

        if (ActivityCompat.checkSelfPermission(this.context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this.context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
            Location location = LocationServices.FusedLocationApi.getLastLocation(this.googleApiClient);
            if (location != null) {
                Log.d(TAG, "Acquired last location");
                this.lastLocation = location;
            } else {
                this.requestLocation();
            }
        }

        this.collect();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        this.collect();
    }
}
