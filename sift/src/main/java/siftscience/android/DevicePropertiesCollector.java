package siftscience.android;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.sift.api.representations.AndroidDevicePropertiesJson;
import com.sift.api.representations.MobileEventJson;

/**
 * Created by gary on 1/18/17.
 */
public class DevicePropertiesCollector {
    private static final String TAG = DevicePropertiesCollector.class.getName();
    private static final String SDK_VERSION = "0.0";
    private final Sift sift;
    private final Context context;

    public DevicePropertiesCollector(Sift sift, Context context) {
        this.sift = sift;
        this.context = context;
    }

    public void collect() {
        AndroidDevicePropertiesJson deviceProperties = this.get();
        this.sift.getQueue(Sift.DEVICE_PROPERTIES_QUEUE_IDENTIFIER).append(
                MobileEventJson.newBuilder()
                        .withAndroidDeviceProperties(deviceProperties)
                        .withInstallationId(deviceProperties.androidId)
                        .withTime(Time.now())
                        .build());
    }

    private AndroidDevicePropertiesJson get() {
        // Package properties
        PackageManager packageManager = this.context.getPackageManager();
        String appName = null;
        String appVersion = null;

        try {
            ApplicationInfo applicationInfo =
                    packageManager.getApplicationInfo(this.context.getPackageName(), 0);
            appName = (String) packageManager.getApplicationLabel(applicationInfo);
            appVersion = packageManager.getPackageInfo(this.context.getPackageName(), 0).versionName;
        } catch (final PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // Telephony properties
        TelephonyManager telephonyManager = ((TelephonyManager)
                this.context.getSystemService(Context.TELEPHONY_SERVICE));

        String androidId;
        String deviceManufacturer;
        String deviceModel;
        String mobileCarrierName;
        String mobileCarrierIsoCountryCode;
        String systemVersion;

        mobileCarrierName = telephonyManager.getNetworkOperatorName();
        mobileCarrierIsoCountryCode = telephonyManager.getNetworkCountryIso();
        deviceManufacturer = Build.MANUFACTURER;
        deviceModel = Build.MODEL;
        androidId = Settings.Secure.getString(this.context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        systemVersion = Build.VERSION.RELEASE;

        return AndroidDevicePropertiesJson.newBuilder()
                .withAppName(appName)
                .withAppVersion(appVersion)
                .withSdkVersion(SDK_VERSION)
                .withAndroidId(androidId)
                .withDeviceManufacturer(deviceManufacturer)
                .withDeviceModel(deviceModel)
                .withMobileCarrierName(mobileCarrierName)
                .withMobileIsoCountryCode(mobileCarrierIsoCountryCode)
                .withSystemVersion(systemVersion)
                .build();
    }
}
