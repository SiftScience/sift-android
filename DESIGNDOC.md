# **Sift Android SDK**

### Mobile Android SDK Software Design Documentation

##
### Table of Contents

**[1 Overview](#1-overview)**

**[2 High Level Class Diagram](#2-high-level-class-diagram)**

**[3 Data Models](#3-data-models)**
* [3.1 AndroidDeviceLocation](#31-androidDeviceLocation)
* [3.2 AndroidAppState](#32-androidAppState)
* [3.3 AndroidDeviceProperties](#33-androidDeviceProperties)
* [3.4 MobileEvent](#34-mobileEvent)

**[4 Modules](#4-modules)**

* [4.1 SIFT](#41-sIFT)
* [4.2 SIFT IMPL](#42-SIFT-IMPL)
* [4.3 QUEUE](#43-qUEUE) 

**[5 Flow Chart](#5-flow-chart)**

##

## 1 Overview

The [sift-android](https://github.com/SiftScience/sift-android) is an Android SDK developed only for mobiles. Java will be used as the programming language and Android Studio will be used as the IDE. SDK will be supporting a minimum of Android 4.1 (Jelly Bean).

The Android-specific features used are: SharedPreferences, Executors, PackageManager, BatteryManager, Location, NetworkInterface and TelephonyManager. It uses Android&#39;s SharedPreferences to manage configurations of the Sift object andpersisting collected events and config data to disk. TheSDK uses PackageManager, BatteryManager, Location and NetworkInterface for collecting AppState details. The Device properties details are collected with the help of TelephonyManager and PackageManager along with Build details. In particular, event collecting, appending and uploading are handled on a separate thread with the help of Executors.

A high-level block diagram is shown

**![{"theme":"neutral","source":"graph LR\n    A( Sift Android App ) --1--> B(sift-android SDK)\n    B --2--> C(Sift Server)"}](/images/block-diagram.png "mermaid-graph")**


1. Android app loads the SDK with the Sift configurations.
2. The sift SDK will collect and send events to the Sift server when there are events to upload.

This document describes the data models,classes for handling events, and specific flows, respectively. For more information on Android technologies, the Android documentation contains a wealth of information on all Android programming topics and technologies.



## 2 High Level Class Diagram

**![{"theme":"neutral","source":"classDiagram\n\nclass AndroidDeviceLocation {\n    +Long time\n    +Double latitude\n    +Double longitude\n    +Double accuracy\n}\n\nclass AndroidAppState {\n    +String activityClassName\n    +AndroidDeviceLocation location\n    +String sdkVersion\n    +Double batteryLevel\n    +Long batteryState\n    +Long batteryHealth\n    +Long plugState\n    +List~String~ networkAddresses\n}\n\nclass AndroidDeviceProperties {\n    +String appName\n    +String appVersion\n    +String sdkVersion\n    +String mobileCarrierName\n    +String mobileIsoCountryCode\n    +String deviceManufacturer\n    +String deviceModel\n    +String deviceSystemVersion\n    +String androidId\n    +String buildTags\n    +List~String~ evidenceFilesPresent\n    +List~String~ evidencePackagesPresent\n    +List~String~ evidenceProperties\n    +List~String~ evidenceDirectoriesWritable\n    +String buildBrand\n    +String buildDevice\n    +String buildFingerprint\n    +String buildHardware\n    +String buildProduct\n}\n\nclass MobileEvent {\n    +Long time\n    +String userId\n    +String installationId\n    +AndroidDeviceProperties androidDeviceProperties\n    +AndroidAppState androidAppState\n}\n\nAndroidDeviceLocation ..> AndroidAppState\nAndroidAppState ..> MobileEvent\nAndroidDeviceProperties  ..> MobileEvent"}](/images/overall-class-diadram.png "mermaid-graph")**

## 3 Data Models

The data models used in this SDK are auto-generated from &#39;yaml&#39; files. The yaml to POJO conversion is handled by the jsonSchema2Pojo gradle plugin. Which uses &#39;yamlschema&#39; as input and generates POJO for collecting and uploading AppState and DeviceProperties events.

### 3.1 AndroidDeviceLocation

The AndroidDeviceLocation consist of the following information:

- **time** : {type: integer, required: false}
  - It indicates the time at which the location data was collected.
- **latitude** : {type: number, required: false}
  - Which indicates the latitude of the collected location.
- **longitude** : {type: number, required: false}
  - Which indicates the longitude of the collected location.
- **accuracy** : {type: number, required: false}
  - Indicates the accuracy of the collected latitude and longitude.

Class diagram of AndroidDeviceLocation
**![{"theme":"neutral","source":"classDiagram\n\nclass AndroidDeviceLocation{\n    +Long time\n    +Double latitude\n    +Double longitude\n    +Double accuracy\n\n    +withTime(time)\n    +withLatitude(latitude)\n    +withLongitude(longitude)\n    +withAccuracy(accuracy)\n    +toString() string\n    +hashCode() int\n    +equals() boolean\n}"}](/images/android-device-location.png "mermaid-graph")**

### 3.2 AndroidAppState

The AndroidAppState collects the following informations:

- **activity\_class\_name** : {type: string, required: false}
  - The activity class name indicates the current activity/fragment class name from where the data are collected.
- **location** : {type: AndroidDeviceLocation, required: false}
  - The location consists of collective information of latitude, longitude, accuracy and the time at which data was collected as shown in the [section 3.1](#31-androidDeviceLocation). (_Have data only if the sift configuration and permissions are enabled_)
- **sdk\_version** : {type: string, required: false}
  - The sdk version indicates the current Sift SDK version which is used.
- **battery\_level** : {type: number, required: false}
  - The current battery level, from 0 to 1 (_84% indicates 0.84_)
- **battery\_state** : {type: integer, required: false}
  - The current status constant of the battery, from 1 to 5
    - Constant Value: 1 -> BATTERY\_STATUS\_UNKNOWN
    - Constant Value: 2 -> BATTERY\_STATUS\_CHARGING
    - Constant Value: 3 -> BATTERY\_STATUS\_DISCHARGING
    - Constant Value: 4 -> BATTERY\_STATUS\_NOT\_CHARGING
    - Constant Value: 5 -> BATTERY\_STATUS\_FULL
- **battery\_health** : {type: integer, required: false}
  - The battery health indicates the current health constant, from 1 to 7
    - Constant Value: 1 -> BATTERY\_HEALTH\_UNKNOWN
    - Constant Value: 2 -> BATTERY\_HEALTH\_GOOD
    - Constant Value: 3 -> BATTERY\_HEALTH\_OVERHEAT
    - Constant Value: 4 -> BATTERY\_HEALTH\_DEAD
    - Constant Value: 5 -> BATTERY\_HEALTH\_OVER\_VOLTAGE
    - Constant Value: 6 -> BATTERY\_HEALTH\_UNSPECIFIED\_FAILURE
    - Constant Value: 7 -> BATTERY\_HEALTH\_COLD
- **plug\_state** : {type: integer, required: false}
  - The plug state indicates whether the device is plugged in to a power source; 0 means it is on battery, other constants are different types of power sources.
    - Constant Value: 1 -> BATTERY\_PLUGGED\_AC
    - Constant Value: 2 -> BATTERY\_PLUGGED\_USB
    - Constant Value: 4 -> BATTERY\_PLUGGED\_WIRELESS
- **network\_addresses** : {type: array, items: {type: string}, required: false}
  - The network addresses indicate the list of IP addresses of the current device in which the SDK is running.

Class diagram of AndroidAppState
**![{"theme":"neutral","source":"classDiagram\n\nclass AndroidAppState{\n    +String activityClassName\n    +AndroidDeviceLocation location\n    +String sdkVersion\n    +Double batteryLevel\n    +Long batteryState\n    +Long batteryHealth\n    +Long plugState\n    +List~String~ networkAddresses\n\n    +withActivityClassName(activityClassName)\n    +withLocation(location)\n    +withSdkVersion(sdkVersion)\n    +withBatteryLevel(batteryLevel)\n    +withBatteryState(batteryState)\n    +withBatteryHealth(batteryHealth)\n    +withPlugState(plugState)\n    +withNetworkAddresses(networkAddresses)\n    +toString() string\n    +hashCode() int\n    +equals() boolean\n}"}](/images/android-app-state.png "mermaid-graph")**

###

### 3.3 AndroidDeviceProperties

The AndroidDeviceProperties collects the following information:

- **app\_name** : {type: string, required: false}
  - The app name indicates the name of the application in which the sift SDK is used.
- **app\_version** : {type: string, required: false}
  - The app version indicates the current version name of the application in which the sift SDK is used.
- **sdk\_version** : {type: string, required: false}
  - The sdk version indicates the current version of the sift SDK that has been used in the application.
- **mobile\_carrier\_name** : {type: string, required: false}
  - The mobile carrier name indicates the alphabetic name of the current registered network operator.
- **mobile\_iso\_country\_code** : {type: string, required: false}
  - It indicates the ISO-3166-1 alpha-2 country code equivalent for the SIM provider&#39;s country code.
- **device\_manufacturer** : {type: string, required: false}
  - The device manufacturer indicates the manufacturer of the product/hardware.
- **device\_model** : {type: string, required: false}
  - The device model indicates the end-user-visible name for the end product.
- **device\_system\_version** : {type: string, required: false}
  - It indicates the user-visible operating system version string. E.g., &quot;1.0&quot; or &quot;3.2.6&quot;.
- **android\_id** : {type: string, required: false}
  - The Android id indicates the 64-bit number (expressed as a hexadecimal string) unique to each device.

- **build\_tags** : {type: string, required: false}
  - The build tags indicate the comma-separated tags describing the build, like &quot;unsigned,debug&quot;.
  - If Build.TAGS contains &quot;test-keys&quot;, then it is a rooted device.
- **evidence\_files\_present** : {type: array, items: {type: string}, required: false}
  - The evidence file present may contain a list of files that are known to indicate rooted devices.
  - If it is an empty list then the device is not a rooted device.
- **evidence\_packages\_present** : {type: array, items: {type: string}, required: false}
  - The evidence package present may contain a list of packages that are known to indicate rooted devices.
  - If it is an empty list then the device is not a rooted device.
- **evidence\_properties** : {type: array, items: {type: string}, required: false}
  - The evidence property may contain a list of dangerous properties that indicate rooted devices.
  - If it is an empty list then the device is not a rooted device.
- **evidence\_directories\_writable** : {type: array, items: {type: string}, required: false}
  - It may contain a list of path to common system directories which have write permissions that indicate rooted devices.
  - If it is an empty list then the device is not a rooted device.
- **build\_brand** : {type: string, required: false}
  - The build brand indicates the consumer-visible brand with which the product/hardware will be associated, if any.
- **build\_device** : {type: string, required: false}
  - The build device indicates the name of the industrial design.
- **build\_fingerprint** : {type: string, required: false}
  - The build fingerprint indicated a string that uniquely identifies this build.
- **build\_hardware** : {type: string, required: false}
  - The build hardware indicates the name of the hardware (from the kernel command line or /proc).
- **build\_product** : {type: string, required: false}
  - The build product indicates the name of the overall product.

Class diagram of AndroidDeviceProperties
**![{"theme":"neutral","source":"classDiagram\n\nclass AndroidDeviceProperties {\n    +String appName\n    +String appVersion\n    +String sdkVersion\n    +String mobileCarrierName\n    +String mobileIsoCountryCode\n    +String deviceManufacturer\n    +String deviceModel\n    +String deviceSystemVersion\n    +String androidId\n    +String buildTags\n    +List~String~ evidenceFilesPresent\n    +List~String~ evidencePackagesPresent\n    +List~String~ evidenceProperties\n    +List~String~ evidenceDirectoriesWritable\n    +String buildBrand\n    +String buildDevice\n    +String buildFingerprint\n    +String buildHardware\n    +String buildProduct\n\n    +withAppName(appName)\n    +withAppVersion(appVersion)\n    +withSdkVersion(sdkVersion)\n    +withMobileCarrierName(mobileCarrierName)\n    +withMobileIsoCountryCode(mobileIsoCountryCode)\n    +withDeviceManufacturer(deviceManufacturer)\n    +withDeviceModel(deviceModel)\n    +withDeviceSystemVersion(deviceSystemVersion)\n    +withAndroidId(androidId)\n    +withBuildTags(buildTags)\n    +withEvidenceFilesPresent(evidenceFilesPresent)\n    +withEvidencePackagesPresent(evidencePackagesPresent) \n    +withEvidenceProperties(evidenceProperties)\n    +withEvidenceDirectoriesWritable(evidenceDirectoriesWritable)\n    +withBuildBrand(buildBrand)\n    +withBuildDevice(buildDevice)\n    +withBuildFingerprint(buildFingerprint)\n    +withBuildHardware(buildHardware)\n    +withBuildProduct(buildProduct)\n    +toString() string\n    +hashCode() int\n    +equals() boolean\n}"}](/images/android-device-properties.png "mermaid-graph")**


###

### 3.4 MobileEvent

The MobileEvent mainly collects the following information:

- **time** : {type: integer, required: true}
  - It indicates the time (in ms since the unix epoch) that this event occurred.
- **user\_id** : {type: string, required: false}
  - It indicates the time (in ms since the unix epoch) that this event occurred.
- **installation\_id** : {type: string, required: false}
  - The installation id indicates the 64-bit number (expressed as a hexadecimal string) unique to each device.
- **android\_device\_properties** : {type: AndroidDeviceProperties, required: false}
  - The android device property indicates the device related properties as mentioned in [section 3.3](#33-androidDeviceProperties)
- **android\_app\_state** : {type: AndroidAppState, required: false}
  - The android app state indicates the application related datas as mentioned in [section 3.2](#32-androidAppState).

Class diagram of MobileEvent
**![{"theme":"neutral","source":"classDiagram\n\nclass MobileEvent {\n    +Long time\n    +String userId\n    +String installationId\n    +AndroidDeviceProperties androidDeviceProperties\n    +AndroidAppState androidAppState\n\n    +withTime(time)\n    +withUserId(userId)\n    +withInstallationId(installationId)\n    +withAndroidDeviceProperties(androidDeviceProperties)\n    +withAndroidAppState(androidAppState)\n    +toString() string\n    +hashCode() int\n    +equals() boolean\n}"}](/images/mobile-event.png "mermaid-graph")**

## 4 Modules

The SDK also has a number of classes that deal with event collecting, saving and uploading to Sift server.

### 4.1 SIFT

This is a utility class of the sift client library which handles the application-level code for interacting with the framework for collecting, saving and uploading events. This class sets up and holds references to the Gson, SiftImpl and event collectors, including AppStateCollector and DevicePropertiesCollector.

It has a Config class to setup the account details with constructor to initialise the values:

- **Config**(_accountId, beaconKey, serverUrlFormat, disallowLocationCollection_)
  - **accountId** : {type: string}
    - Your account ID; defaults to null.
  - **beaconKey** : {type: string}
    - Your beacon key; defaults to null.
  - **serverUrlFormat** : {type: string}
    - The location of the API endpoint; defaults to https://api3.siftscience.com/v3/accounts/%s/mobile\_events
  - **disallowLocationCollection** : {type: boolean}
    - Whether to allow location collection; defaults to false.

Also the Config class provide a builder class to initialize the configuration data:

- withAccountId(accountId)
- withBeaconKey(beaconKey)
- withServerUrlFormat(serverUrlFormat)
- withDisallowLocationCollection(disallowLocationCollection)
```java
Sift.Config.Builder()
	.withAccountId("YOUR_ACCOUNT_ID")
	.withBeaconKey("YOUR_BEACON_KEY")
	.build()
```
The builder build() method will execute the Config() constructor with provided values.

Following are the static API to interact with SDK:

- **open**(_context, config, activityName_)
  - _@param **context** the Activity context_
  - _@param **config** the Sift.Config object_
  - _@param **activityName** the Activity_
  - Should call in the onCreate() callback of each Activity.
  - It creates the Sift singleton instance and collectors if they do not exist, and passes along the current Activity context.
  - For your application&#39;s main Activity, make sure to provide a Sift.Config object as the second parameter.
  - If you are integrating per-Activity rather than at the Application level, you can specify the name that will be associated with each Activity event (defaults to the class name of the embedding Activity).
  - There are overloaded methods below for your convenience.
    - open(context, activityName)
    - open(context, config)
    - open(context)
- **collect**()
  - Should call Sift.collect() after the Sift.open() call in each Activity.
  - Which executes a runnable task to collect SDK events for Device Properties and Application State.
- **pause**()
  - Should call Sift.pause() in the onPause() callback of each Activity.
  - Which persists the instance state to disk and disconnects location services.
- **resume**(_context, activityName_)
  - Should call Sift.resume() in the onResume() callback of each Activity.
  - It will try to reconnect the location services if configuration and permissions are enabled.
  - If you provide a non-null activity name as a parameter then it will set the current activity class name as the name provided, otherwise it will set the simple name of the underlying class.
  - There is an overloaded method for your convenience.
    - resume(context)
- **close**()
  - Call Sift.close() in the onDestroy() callback of each Activity.
  - It persists the instance state to disk and disconnects location services.
- **setUserId**(_userId_)
  - It will set the provided id as the userId inside the sift instance.
- **unsetUserId**()
  - Which removes any presetted useId to null

### 4.2 SIFT IMPL

This class is the implementation of Sift instance which sets up and holds the references to the Sift.Config, queues, task manager and uploader. Where Sift.Config consist of account related data, queues consist of events related to App State and Device Properties, task manager handles the execution of runnable tasks, and uloader handles the uploading events to the sift server.
 It generates the queue configuration to decide the event acceptance and uploading criteria. Like accepting the same event after 1 hour, upload events when more than 8 and so on.

It provides the implementation of interfaces like _UserIdProvider_ and _UploadRequester_ in Queue and _ConfigProvider_ in Uploader.

This class mainly handles the following task:

- Archive/Save all of the sift instance states to the disk using shared preference, which includes Sift.Config, user Id, app state queue and device properties queue.
- Unarchive/Restore all the sift instance states(Sift.Confi, user Id, and queues) from disk.
- Appends the collected event to the App State queue and Device Properties queue.
- Setting Sift.Config.
- Setting user Id.

These tasks runon a separate executor, so that if any largeamounts of data does not affect the main thread.

To execute those task it provide the following instance API:

- **save**()
  - Which invokes the task manager to execute the archiving task.
- **stop**()
  - Which invokes the task manager to terminate the executor.
- **appendAppStateEvent**(_event_)
  - Which invokes the task manager to execute the append task with App State queue identifier and provided event.
- **appendDevicePropertiesEvent**(_event_)
  - Which invokes the task manager to execute the append task with Device Properties queue identifier and provided event.
- **upload**(_events_)
  - Which invokes the uploader to execute the upload task with a list of collected events.
- **setConfig**(_config_)
  - Sets the configuration for the Sift instance
  - Which invokes the task manager to execute the set config task with provided configuration.
- **getConfig**()
  - Return the configuration for the Sift instance
  - It will provide the non-nul Sift.Config if available, otherwise unarchive the configuration from shared preference.
- **setUserId**(_userId_)
  - Sets the user ID for the Sift instance.
  - Which invokes the task manager to execute the set user Id task with specified userId.
- **getUserId**()
  - Return the user ID for the Sift instance.
- **unsetUserId**()
  - Unsets the user ID for the Sift instance.
  - Which invokes the task manager to execute the set user Id task with null value.

- **createQueue**(_identifier, config_)
  - It will check whether the queue already exists with the identifier, if so it will throw the exception. Otherwise it will create a new queue with provided config, and object of userIdProvider and uploadProvider.
- **getQueue**(_identifier_)
  - It will return the specified queue from the available queues map.

Whenever a new object is created the constructor will invoke the task manager to execute the unarchive task to restore the Sift.Config, User Id, and Queues from disk if any.

The _UploadRequester_ interface overridden method requestUpload(events) will invoke the SiftImpl.upload(events). Similarly _ConfigProvider_ interface overridden method getConfig() will invoke SiftImpl.getConfig()

It holds an enum class ArchinveKey which serves as a key provider for the data persistence on the disk. There are three enums for 3 types of key, one for _Sift.Config_, one for _userId_, and one for _queue_. Also provide two static methods:

**getKeyForQueueIdentifier**(_identifier_): {type: string}

  - Which returns a string with combination of queue key and identifier.

**getQueueIdentifier**(_key_): {type: string}

  - Which returns null if the provided combination is not starting with a queue key, otherwise it will return the identifier string from the combination.

### 4.3 QUEUE

This class is for holding events until they are ready for upload to the sift server. Basically there are two separate queues, one for AppState events and other for DeviceProperties events in order to collect and upload the events independently.

Whenever an event is collected and tries to add either in the App State or Device Properties queue, it will append and upload depending on the queue&#39;s batching policy. The queue&#39;s batching policy is controlled by the queue configuration and state of the queue.

The queue configuration depends on the following factors:

- **acceptSameEventAfter** : {type: long}
  - Time after which an event that is basically the same as the most recently appended event can be appended again.
- **uploadWhenMoreThan** : {type: int}
  - Max queue depth before flush and upload request.
- **uploadWhenOlderThan** : {type: long}
  - Max queue age before flush and upload request.

Which can be initialized through the queue config builder class, which provide the following methods:

- withAcceptSameEventAfter(_acceptSameEventAfter_): {type: long}
- withUploadWhenMoreThan(_uploadWhenMoreThan_): {type: int}
- withUploadWhenOlderThan(_uploadWhenOlderThan_): {type: long}

```java
 Queue.Config.Builder()
	.withUploadWhenMoreThan(8)
	.withUploadWhenOlderThan(TimeUnit.MINUTES.toMillis(1))
	.build();
```
The DeviceProperties queue is configured as:

- _acceptSameEventAfter: **1 hour**_
- _uploadWhenMoreThan: **0**_
- _uploadWhenOlderThan: **1 minute**_

The AppState queue is configured as:

- _uploadWhenMoreThan: **8**_
- _uploadWhenOlderThan: **1 minute**_

This class holds the state of the queue with the following attributes:

- **config** : {type: Queue.Config}
  - The configuration of the queue which decides the batching policy.
- **queue** : {type: List< MobileEvent>}
  - The list of collected events as of now to be uploaded depending on the policy.
- **lastEvent** : {type: MobileEvent}
  - The recent event added to the queue.
- **lastUploadTimestamp:** {type: long}
  - The time at which recent upload was carried on.

This class have the following methods:

- **archive**()
  - Which return json string of the current queue state with the help of Gson.
- **unarchive**(_archive_)
  - Where _archive_ is the json string of queue state.
  - Which return queue state from provided json string.
  - It will return a new object of queue state, If the provided json string is null or any exception in json syntax.
- **getConfig**()
  - Which returns the reference value of current Queue.Config.
- **flush**()
  - Which shifts the queue into a temporary queue and clears the current queue.
  - Finally returns the temporary queue.
- **isReadyForUpload**(_now_)
  - Where _now_ is the current timestamp.
  - The queue uploading policy is based on:

- state.queue.size() > config.uploadWhenMoreThan
- now > state.lastUploadTimestamp + config.uploadWhenOlderThan

If any one of the uploading policies were satisfied, then the queue will be flushed and uploaded.

- **append**(_event_)
  - Where _event_ is the collected event to be appended and uploaded depending on the queue batching policies.
  - The queue appending policy is based on the following criteria:

- _config.acceptSameEventAfter_ > 0
- _state.lastEvent_ != null
- Current time < _state.lastEvent.time_ + _config.acceptSameEventAfter_
- _state.lastEvent_ and current _event_ are basically the same

If all of the above conditions are met, then the queue will drop the collected event as a duplicate event. Otherwise the event will be appended to the queue and the _state.lastEvent_ updated with the new event.

  - After appending it will check whether the event _isReadyForUpload(now),_ if ready then request the uploader to upload the events.

The queue constructor expect the following parameters:

- **archive** : {type: string}
  - The archived state of the queue in json, if it is a non-null value then the state of the newly created queue will be initialized with the saved one. Otherwise the queue will create a new state.
- **userIdProvider** : {type: UserIdProvider}
  - The reference variable of UserIdProvider interface which holds overridden _getUserId_() method.
  - Which is used at the time of appending events, whenever there is a lack of userId in the collected event then it will assign the value from _userIdProvider.getUserId()_
- **uploadRequester** : {type: UploadRequester}
  - The reference variable of UploadRequester interface which holds overridden _requestUpload_(_events_) method.
  - Which is used at the time of uploading events, whenever the collected events are ready for upload then it will request uploader with list of events as _uploadRequester.requestUpload(flush())_
- **config** : {type: Queue.Config}
  - The queue configuration which decides the batching policy.

### 4.4 APP STATE COLLECTOR

This module collects the App State events which consist of battery information, location information and network address. The battery information includes current battery level, battery status, battery health and battery plug state. Similarly the location information includes latitude, longitude, accuracy and time at which the location collected. Also the network address holds the ip addresses of the device.

This class expects the Sift instance and context, depending on the sift instance configuration data it will initialise location provider and location request if disallowLocationCollection is false (_default value_).

It provides the following instance methods:

- **setActivityName**(_activityName_)
  - Which will assign the current activity class name as the provided name.
- **collect**()
  - This will request location updates if Sift.Config _disallowLocationCollection_ is false and location permission is enabled by the user which was requested by your application (_NB: Sift will not request permissions that are not granted by the user from your application_). Otherwise it will collect the rest of app state events without location information.
  - The location collection is managed by FusedLocationProviderClient. The location request is configured as PRIORITY\_HIGH\_ACCURACY with one minute interval and 10 seconds fastest interval.
  - If location permission is granted by the user then first of all it will fetch the last known location from FusedLocationClient and which is saved in _lastLocation_ reference.
  - Once the onLocationResult callback is triggered, it will update the current _location_, remove location updates and collect the remaining app state events.
  - If any of the _lastLocation_ or _location_ have non-null value then at the time of app state event collection the AndroidDeviceLocation model will be updated with either acquired new location or last known location depending on the availability and priority.
  - The battery information is collected from ACTION\_BATTERY\_CHANGED intent and with the help of BatteryManager. The battery level is calculated on the value from BatteryManager.EXTRA\_LEVEL over BatteryManager.EXTRA\_SCALE. The status value is identified based on the integer value from BatteryManager.EXTRA\_STATUS, the plug state integer value from BatteryManager.EXTRA\_PLUGGED and health integer value from BatteryManager.EXTRA\_HEALTH
  - The Ip addresses are irritated from the host address provided by the InetAddress under NetworkInterface.
  - After collecting these app state event information it will invoke the swift instance method appendAppStateEvent(_event_) with MobileEvent which is occupied with AndroidAppState data model.
- **disconnectLocationServices**()
  - This will try to remove ongoing location updates if any.
- **reconnectLocationServices**()
  - It will request location updates if Sift.Config with _disallowLocationCollection_ is false, location permissions are enabled, fused location clients have non-null reference, and there are no ongoing location updates.

### 4.5 DEVICE PROPERTIES COLLECTOR

This module collects device properties events which includes app name, app version, sdk version, android id, device manufacturer, device model, mobile carrier name, mobile iso country name, device system version, build brand, build device, build fingerprint, build hardware, build product, build tag, evidence file present, evidence package present, evidence properties and evidence directories writable.

It provides only one instance method:

- **collect**()
  - It will collect the app name and the app version from PackageManager application info and package info respectively.
  - Using TelephonyManager mobile carrier name is assigned by the network operator name and mobile carrier iso country code is collected from sim country iso.
  - Other build and device informations are collected from android.os.Build provider:
    - The device manufacturer detail is collected from _Build.MANUFACTURER_ which provides the manufacturer of the product/hardware.
    - The device model from _Build.MODEL_ which provides the end-user-visible name for the end product.
    - The build brand from _Build.BRAND_ which provide the consumer-visible brand with which the product/hardware will be associated
    - The build device from _Build.DEVICE_ which provides the name of the industrial design.
    - The build fingerprint from _Build.FINGERPRINT_ which provides a string that uniquely identifies this build. Do not attempt to parse this value.
    - The build hardware from _Build.HARDWARE_ which provides the name of the hardware (from the kernel command line or /proc).
    - The build product from _Build.PRODUCT_ which provides the name of the overall product.
    - The build tag from _Build.TAGS_ which provides comma-separated tags describing the build, like &quot;unsigned,debug&quot;.
  - It will check and collect evidence from the device which indicates whether the device is rooted with the following different approaches:
    - existingRootFiles()
      - Checks for files that are known to indicate root.
      - Will return a list of super user paths, if any.
    - existingRootPackages()
      - Checks for packages that are known to indicate root.
      - Will return a list of package names which are known root apps, dangerous apps and root cloaking, if any.
    - existingDangerousProperties()
      - Checks system properties for any dangerous properties that indicate root.
      - Will return a list of dangerous properties that indicate root, If any.
    - existingRWPaths()
      - This method checks if any of the system paths that should not be writable are writable (_in rooted devices you can change the write permissions on common system directories)_.
      - Will return all restricted system paths that are writable, if any.
  - The following are different methods to detect whether the device is rooted.
    - If _Build.TAGS_ contains &quot;test-keys&quot;, then it&#39;s rooted.
    - If any of the other evidence\* methods return a non-empty list, then it&#39;s rooted.

### 4.6 TASK MANAGER

This module is a wrapper around executor that abstracts exception handling and shutdown logic. It will create a single-threaded executor that can schedule commands to run after a given delay, or requests for immediate execution with zero delay, or to execute periodically.

This class provide three instance methods as follow:

- **submit**(_task_)
  - Where the _task_ is a Runnable task that is to be executed.
  - It will invoke the executor&#39;s submit() method with the provided runnable task for immediate execution.
  - If the task cannot be submitted for execution then it will throw a _RejectedExecutionException._
  - The runnable tasks which are requested for immediate execution are: archiving, unarchiving, appending, setting user Id and setting sift config
- **schedule**(task, delay, unit)
  - Where:
    -  **task**  is a Runnable task that is to be executed.
    -  **delay** is the time from now to delay execution.
    -  **unit**  is the time unit of the delay parameter.
  - It will create and execute a one-shot action that becomes enabled after the given delay by invoking executor&#39;s schedule() method.
  - If the runnable task cannot be scheduled for execution then it will throw a _RejectedExecutionException._
  - All uploading tasks are scheduled execution with an incrementing delay upto there retries.
- **shutdown**()
  - Will invoke executor&#39;s shutdown method to initiate an orderly shutdown in which previously submitted tasks are executed, but no new tasks will be accepted. Invocation has no additional effect if already shut down.
  - This method does not wait for previously submitted tasks to complete execution.


## 5 Flow Chart


**![{"theme":"neutral","source":"graph TD\n\n    A[Sift] --> B(App State Collector)\n    A --> C(Device Property Collector)\n\n    C & B -->|Collected Events| D[Task Manager]\n    \n    D -->|Add Event| E[[Device Property Queue ]] & F[[App State Queue]]\n\n    E & F -->|Request upload| G([Uploader])\n    \n\n    G -.->|Upload Event| H((Sift Server fa:fa-server))"}](/images/flow-chart.png  "mermaid-graph")**
