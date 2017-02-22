package siftscience.android;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
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
import java.util.concurrent.TimeUnit;

/**
 * Created by gary on 1/30/17.
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
    private LocationManager locationManager;

    private Location lastLocation;

    public AppStateCollector(Sift sift, Context context) {
        this.sift = sift;
        this.context = context;

        this.googleApiClient = new GoogleApiClient.Builder(this.context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        this.googleApiClient.connect();
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

        if (this.googleApiClient.isConnected()) {
            this.googleApiClient.disconnect();
        }
    }

    public void reconnectLocationServices() {
        Log.d(TAG, "Reconnect location services");

        this.googleApiClient.connect();
    }

    private AndroidAppStateJson get() {
        if (this.lastLocation != null) {
            return AndroidAppStateJson.newBuilder()
                    .withActivityClassName(this.context.getClass().getSimpleName())
                    .withLocation(this.getLocation())
                    .build();
        }

        return AndroidAppStateJson.newBuilder()
                .withActivityClassName(this.context.getClass().getSimpleName())
                .build();
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
