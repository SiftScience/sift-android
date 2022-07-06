# Change Log

## [1.1.1] - 2022-07-06

### Removed
- Removed deprecated time and accuracy fields of the android device location object.

## [1.1.0] - 2021-08-02

### Added
- Added a new public upload() function.
- Updated README.md to include details about how to submit an issue.

## [1.0.0] - 2021-04-20

### Added
- Executor used for trigger collect method of DeviceProperties and AppState terminated on sift.cancel().
- Images used in DESIGNDOC.md are added to the repo to avoid URL issues.
- Created Changelog
- Prevent SDK from sending empty configuration properties

## [0.11.1] - 2020-11-20

### Added
- Fix for Null Pointer Exception on getting device battery status

### Changed
- Migrated to AndroidX
- Upgrade to API version 30 (Android 11)
- AsyncTask replaced with Executor, since AsyncTask was deprecated in API level 30
- Supporting dependencies are upgraded

## [0.10.5] - 2019-05-07

### Added
- Fix for potential Null Pointer Exception in `AppStateCollector.getIpAddresses()`

## [0.10.4] - 2018-07-30

### Changed
- Switches to upstream jsonSchema2Pojo 

### Removed
- Removes a few unused dependencies  
- Removes a few outdated ProGuard entries

## [0.10.3] - 2018-05-31

### Added
- Adds functionality for calling `Sift.open` with a new config

## [0.10.2] - 2018-04-26

### Added
- Allows for setUserId outside of activity lifecycle  
- Fixes for Null Pointer Exception on Google API Client

## [0.10.1] - 2018-04-14

### Added
- Adds hook for passing custom `activityName` to `Sift.resume`
- Does not null out instance state  

### Changed
- Updates README file and HelloSift sample app

## [0.10.0] - 2018-04-06

This is a significant rewrite that we expect to greatly improve the performance and stability of the Android SDK. Please refer to the change log below as well as the updated README.md.

### Breaking changes

- Removed access to the Sift instance and renamed a few hooks. Everything should be invoked via the static call to the singleton class.
    - `Sift.get().save()`  is now  `Sift.pause()`
    - `Sift.get().setUserId(…)`  is now  `Sift.setUserId(…)`
    - `Sift.get().unsetUserId(…)`  is now  `Sift.unsetUserId(…)`
    - Removed support for  `Sift.get.upload(…)`
- Removed the optional location permissions from sift SDK AndroidManifest.xml. As such, you will need to add  `ACCESS_FINE_LOCATION`  and  `ACCESS_COARSE_LOCATION`  to your own application’s AndroidManifest.xml if you would like to enable location collection

### Added
- Added a new hook into the onResume lifecycle callback:  `Sift.resume()`

### Removed
- Removed OkHttp from the dependencies

## [0.9.13] - 2018-02-28

### Changed
- Fixes the deadlock casued by synchronized calls in Queue and Uploader  `archive()`  functions which are invoked by the Sift singleton  `save()`
- Cuts the androidAppState batch size down to 8 from 32 due to OOM errors while trying to GSON serialize the string on old devices

## [0.9.12] - 2018-01-20

### Added
- Fix for RejectedExecutionException in AppStateCollector

## [0.9.11] - 2018-01-10

### Added
- Strips out logging in ProGuard release build  
- Fix minor shutdown race condition in Uploader

### Changed
- Moves unarchiving to background task  
- Various performance improvements

## [0.9.10] - 2017-12-15

### Added
- Adds back defensive FieldNamingPolicy with GSON

## [0.9.9] - 2017-12-12

### Added
- GSON and proguard rules
- Proguard rule for lang3
- Better retry logic on errors
- Performance improvements 

### Removed
- Jackson and proguard rules
- Synchronize from unarchives
 
## [0.9.8] - 2017-11-11

### Changed
- Fixes leaked Activity context

## [0.9.7] - 2017-10-27

### Added
- More complete proguard configs for Jackson  
- Adds `@Nullable` annotation to `Sift.get()` method

### Changed
- Updated location collection mechanics  

### Removed
- Removed collecting information of installed apps on device

## [0.9.6] - 2017-09-14

### Changed
- Uses yaml schema instead of inner class for ListRequest

## [0.9.5] - 2017-08-26

### Added
- Collects device Build details for emulator detection
- Collects list of installed apps with package name 

## [0.9.4] - 2017-07-04

### Added
- Fix for RejectedExecutionException on uploader
- Collects SDK_VERSION in AppState events to be able to track event counts per version

## [0.9.3] - 2017-06-20

### Changed
- Updated encoding provider

### Removed
- Removes Guava dependency

## [0.9.2] - 2017-06-09

### Added
- Fix for Null Pointer Exceotion on location changed

### Changed
- Google API Client and gradle fixes
- Make SDK logs better

## [0.9.1] - 2017-06-07

### Added
- Adds Proguard rules to support Jackson 
- Fixes configuration for DeserializationFeature to Ignore unknown properties with Jackson

## [0.9.0] - 2017-05-24

### Added
- Implement the core part of the SDK (event batching and uploading)
- Collects device properties like OS version, root detection etc... 
- Collects app state properties like battery, network address, etc...
- Add demo app HelloSift
