package siftscience.android;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;

import com.sift.api.representations.AndroidDevicePropertiesJson;
import com.sift.api.representations.MobileEventJson;

/**
 * Created by gary on 1/18/17.
 */
public class DevicePropertiesCollector {
    private static final String TAG = Sift.class.getName();
    private static final String SDK_VERSION = "0.0";

    public static void collect(Sift sift, Context context) {
        sift.getQueue().append(
                MobileEventJson.newBuilder()
                    .withAndroidDeviceProperties(get(context))
                    .build());
    }

    public static AndroidDevicePropertiesJson get(Context context) {
        PackageManager packageManager = context.getPackageManager();
        ApplicationInfo applicationInfo = null;

        String appName = null;
        String appVersion = null;
        String androidId = null;

        try {
            applicationInfo = packageManager.getApplicationInfo(context.getPackageName(), 0);
            appName = (String) packageManager.getApplicationLabel(applicationInfo);
            appVersion = packageManager.getPackageInfo(context.getPackageName(), 0).versionName;
            androidId = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
        } catch (final PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return AndroidDevicePropertiesJson.newBuilder()
                .withAppName(appName)
                .withAppVersion(appVersion)
                .withSdkVersion(SDK_VERSION)
                .withAndroidId(androidId)
                .build();
    }
}
