// Copyright (c) 2018 Sift Science. All rights reserved.

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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Collects Device Properties events.
 */
public class DevicePropertiesCollector {
    private static final String TAG = DevicePropertiesCollector.class.getName();
    private final SiftImpl sift;
    private final Context context;

    // Constants used to determine whether a device is rooted
    private static final String[] SU_PATHS = {
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"};
    private static final String[] KNOWN_ROOT_APPS_PACKAGES = {
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su"};
    private static final String[] KNOWN_DANGEROUS_APPS_PACKAGES = {
            "com.koushikdutta.rommanager",
            "com.dimonvideo.luckypatcher",
            "com.chelpus.lackypatch",
            "com.ramdroid.appquarantine"};
    private static final String[] KNOWN_ROOT_CLOAKING_PACKAGES = {
            "com.devadvance.rootcloak",
            "de.robv.android.xposed.installer",
            "com.saurik.substrate",
            "com.devadvance.rootcloakplus",
            "com.zachspong.temprootremovejb",
            "com.amphoras.hidemyroot",
            "com.formyhm.hideroot"};
    private static final String[] PATHS_THAT_SHOULD_NOT_BE_WRITABLE = {
            "/system",
            "/system/bin",
            "/system/sbin",
            "/system/xbin",
            "/vendor/bin",
            "/sbin",
            "/etc"};
    private static final Map<String, String> DANGEROUS_PROPERTIES = new HashMap<String, String>(){{
            put("[ro.debuggable]", "[1]");
            put("[ro.secure]", "[0]");
    }};


    public DevicePropertiesCollector(SiftImpl sift, Context context) {
        this.sift = sift;
        this.context = context.getApplicationContext();
    }

    public void collect() {
        AndroidDevicePropertiesJson deviceProperties = this.get();
        this.sift.appendDevicePropertiesEvent(
                new MobileEventJson()
                        .withAndroidDeviceProperties(deviceProperties)
                        .withInstallationId(deviceProperties.androidId)
                        .withTime(Time.now()));
    }

    private AndroidDevicePropertiesJson get() {
        // Package properties
        PackageManager packageManager = this.context.getPackageManager();
        String appName = null;
        String appVersion = null;

        try {
            ApplicationInfo applicationInfo = packageManager != null ?
                    packageManager.getApplicationInfo(this.context.getPackageName(), 0) : null;
            appName = packageManager != null ?
                    (String) packageManager.getApplicationLabel(applicationInfo) : null;
            appVersion = packageManager != null ?
                    packageManager.getPackageInfo(this.context.getPackageName(), 0).versionName : null;
        } catch (final PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Encountered NameNotFoundException in get", e);
        }

        // Telephony properties
        TelephonyManager telephonyManager = ((TelephonyManager)
                this.context.getSystemService(Context.TELEPHONY_SERVICE));

        String androidId;
        String mobileCarrierName;
        String mobileCarrierIsoCountryCode;
        String systemVersion;

        mobileCarrierName = telephonyManager != null ? telephonyManager.getNetworkOperatorName() : null;
        mobileCarrierIsoCountryCode = telephonyManager != null ? telephonyManager.getSimCountryIso() : null;
        androidId = Settings.Secure.getString(this.context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        systemVersion = Build.VERSION.RELEASE;

        // The following are different methods to detect whether the device is rooted.
        // If Build.TAGS contains "test-keys", then it's rooted.
        // If any of the other evidence* methods return a non-empty list, then it's rooted.
        List<String> evidenceFiles = existingRootFiles();
        List<String> evidencePackages = existingRootPackages();
        List<String> evidenceProperties = existingDangerousProperties();
        List<String> evidenceRWPaths = existingRWPaths();

        return new AndroidDevicePropertiesJson()
                .withAppName(appName)
                .withAppVersion(appVersion)
                .withSdkVersion(Sift.SDK_VERSION)
                .withAndroidId(androidId)
                .withDeviceManufacturer(Build.MANUFACTURER)
                .withDeviceModel(Build.MODEL)
                .withMobileCarrierName(mobileCarrierName)
                .withMobileIsoCountryCode(mobileCarrierIsoCountryCode)
                .withDeviceSystemVersion(systemVersion)
                .withBuildBrand(Build.BRAND)
                .withBuildDevice(Build.DEVICE)
                .withBuildFingerprint(Build.FINGERPRINT)
                .withBuildHardware(Build.HARDWARE)
                .withBuildProduct(Build.PRODUCT)
                .withBuildTags(Build.TAGS)
                .withEvidenceFilesPresent(evidenceFiles)
                .withEvidencePackagesPresent(evidencePackages)
                .withEvidenceProperties(evidenceProperties)
                .withEvidenceDirectoriesWritable(evidenceRWPaths);
    }

    /**
     * Checks for files that are known to indicate root.
     * @return - list of such files found
     */
    private List<String> existingRootFiles() {
        List<String> filesFound = new ArrayList<>();
        for (String path : SU_PATHS) {
            if (new File(path).exists()) {
                filesFound.add(path);
            }
        }
        return filesFound;
    }

    /**
     * Checks for packages that are known to indicate root.
     * @return - list of such packages found
     */
    private List<String> existingRootPackages() {
        ArrayList<String> packages = new ArrayList<>();
        packages.addAll(Arrays.asList(KNOWN_ROOT_APPS_PACKAGES));
        packages.addAll(Arrays.asList(KNOWN_DANGEROUS_APPS_PACKAGES));
        packages.addAll(Arrays.asList(KNOWN_ROOT_CLOAKING_PACKAGES));

        PackageManager pm = context.getPackageManager();
        List<String> packagesFound = new ArrayList<>();

        if (pm != null) {
            for (String packageName : packages) {
                try {
                    // Root app detected
                        pm.getPackageInfo(packageName, 0);
                        packagesFound.add(packageName);
                } catch (PackageManager.NameNotFoundException e) {
                    // Exception thrown, package is not installed into the system
                }
            }
        }
        return packagesFound;
    }

    /**
     * Checks system properties for any dangerous properties that indicate root.
     * @return - list of dangerous properties that indicate root
     */
    private List<String> existingDangerousProperties() {
        String[] lines = propertiesReader();
        List<String> propertiesFound = new ArrayList<>();
        for (String line : lines) {
            for (String key : DANGEROUS_PROPERTIES.keySet()) {
                if (line.contains(key) && line.contains(DANGEROUS_PROPERTIES.get(key))) {
                    propertiesFound.add(line);
                }
            }
        }
        return propertiesFound;
    }

    /**
     * When you're root you can change the write permissions on common system directories.
     * This method checks if any of the paths in PATHS_THAT_SHOULD_NOT_BE_WRITABLE are writable.
     * @return all paths that are writable
     */
    private List<String> existingRWPaths() {
        String[] lines = mountReader();
        List<String> pathsFound = new ArrayList<>();
        for (String line : lines) {
            // Split lines into parts
            String[] args = line.split(" ");
            if (args.length < 4){
                // If we don't have enough options per line, skip this and log an error
                Log.e(TAG, String.format("Error formatting mount: %s", line));
                continue;
            }
            String mountPoint = args[1];
            String mountOptions = args[3];

            for (String pathToCheck : PATHS_THAT_SHOULD_NOT_BE_WRITABLE) {
                if (mountPoint.equalsIgnoreCase(pathToCheck)) {
                    // Split options out and compare against "rw" to avoid false positives
                    for (String option : mountOptions.split(",")){
                        if (option.equalsIgnoreCase("rw")){
                            pathsFound.add(pathToCheck);
                            break;
                        }
                    }
                }
            }
        }
        return pathsFound;
    }

    /**
     * Used for existingDangerousProperties().
     * @return - list of system properties
     */
    private String[] propertiesReader() {
        InputStream inputstream = null;
        try {
            inputstream = Runtime.getRuntime().exec("getprop").getInputStream();
        } catch (IOException e) {
            Log.e(TAG, "Error reading properties", e);
        }
        if (inputstream == null) {
            return new String[0];
        }

        String allProperties = "";
        try {
            allProperties = new Scanner(inputstream).useDelimiter("\\A").next();
        } catch (NoSuchElementException e) {
            Log.e(TAG, "Error reading properties", e);
        }
        return allProperties.split("\n");
    }

    /**
     * Used for existingRWPaths().
     * @return - list of directories and their properties
     */
    private String[] mountReader() {
        InputStream inputstream = null;
        try {
            inputstream = Runtime.getRuntime().exec("mount").getInputStream();
        } catch (IOException e) {
            Log.e(TAG, "Error reading mount", e);
        }
        if (inputstream == null) {
            return new String[0];
        }

        String allPaths = "";
        try {
            allPaths = new Scanner(inputstream).useDelimiter("\\A").next();
        } catch (NoSuchElementException e) {
            Log.e(TAG, "Error reading mount", e);
        }
        return allPaths.split("\n");
    }
}
