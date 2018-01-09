# Sift Science Android SDK

- [Introduction](#introduction)
- [Installation](#installation)
- [Integration](#integration)
  - [Application Integration](#application)
  - [Custom Integration](#custom)

<a name="introduction"></a>
## Introduction

The Sift Android SDK collects and sends Android device information and application interaction events to Sift Science for use in improving fraud detection accuracy.

<a name="installation"></a>
## Installing Sift

Add Sift to your application’s build.gradle file:

```
dependencies {
  ...
  compile 'com.siftscience:sift-android:0.9.11'
  ...
}
```

You may also need to add the following `packagingOptions` to the main android block:

```
android {
  ...
  packagingOptions {
    exclude 'META-INF/DEPENDENCIES.txt'
    exclude 'META-INF/LICENSE.txt'
    exclude 'META-INF/NOTICE.txt'
    exclude 'META-INF/NOTICE'
    exclude 'META-INF/LICENSE'
    exclude 'META-INF/DEPENDENCIES'
    exclude 'META-INF/notice.txt'
    exclude 'META-INF/license.txt'
    exclude 'META-INF/dependencies.txt'
    exclude 'META-INF/LGPL2.1'
  }
  ...
}
```

<a name="integration"></a>
## Integrating Sift

There are two different integration paths to take for incorporating Sift into your application.
The first one will be detailed below in the [Application Integration](#application) section.
Follow these instructions if your application flow is primarily based on
Activities. If your application flow is based on a combination of Activities
and Fragments, please refer to the the [Custom Integration](#custom) section.

<a name="application"></a>
### Application Integration
#### Add Sift to your Application file
Create an Application file if you haven’t already. Create an internal class that implements the `ActivityLifecycleCallbacks` interface and register Sift as shown below:

```
import siftscience.android.Sift;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacksHandler());
    }

    private static final class ActivityLifecycleCallbacksHandler
            implements ActivityLifecycleCallbacks {
        public void onActivityCreated(Activity activity, Bundle bundle) {
            Sift.open(activity, new Sift.Config.Builder()
                .withAccountId("YOUR_ACCOUNT_ID")
                .withBeaconKey("YOUR_BEACON_KEY")
                // Uncomment to disallow location collection
                // .withDisallowLocationCollection(true)
                .build());
            Sift.collect();
        }
        public void onActivityPaused(Activity activity) {
            Sift.get().save();
        }
        public void onActivityDestroyed(Activity activity) {
            Sift.close();
        }
    }
}
```

#### Set the user id

As soon as your application is aware of the user id, set it on the Sift instance using the code below. All subsequent events will include the user id.

```
Sift.get().setUserId("SOME_USER_ID");
```

If the user logs out of your application, you should unset the user id:

```
Sift.get().unsetUserId();
```

<a name="custom"></a>
### Custom Integration
#### Initialize Sift in your main Activity

Configure the Sift object in the `onCreate` method of your application's main Activity (the one that begins the application). If the user id is known at this point, you can set it here. Otherwise, you should set it as soon as it is known. In the main Activity, also override `onDestroy` and `onPause` as shown:

```
import siftscience.android.Sift;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hello_sift);
        Sift.open(this, new Sift.Config.Builder()
            .withAccountId("YOUR_ACCOUNT_ID")
            .withBeaconKey("YOUR_BEACON_KEY")
            // Uncomment to disallow location collection
            // .withDisallowLocationCollection(true)
            .build());
        Sift.collect();
    }
    @Override
    protected void onPause() {
        super.onPause();
        Sift.get().save();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Sift.close();
    }
}
```

#### Add Sift to your application flow

For each Activity or Fragment that represents a unique page in your application flow, override `onStart`, `onPause`, and `onStop`:

```
public class OtherActivity extends AppCompatActivity {
    @Override
    protected void onStart(Bundle savedInstanceState) {
        super.onStart();
        Sift.open(this);
        // For Fragments, use Sift.open(this.getActivity()) instead
        Sift.collect();
    }
    @Override
    protected void onPause() {
        super.onPause();
        Sift.get().save();
    }
    @Override
    protected void onStop() {
        super.onStop();
        Sift.close();
    }
}
```

#### Set the user id

As soon as your application is aware of the user id, set it on the Sift instance using the code below. All subsequent events will include the user id.

```
Sift.get().setUserId("SOME_USER_ID");
```

If the user logs out of your application, you should unset the user id:

```
Sift.get().unsetUserId();
```
