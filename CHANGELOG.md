# Change Log

## [0.11.1] - 2020-11-20

- Upgrade to API version 30 (Android 11)
- Migrated to AndroidX
- AsyncTask replaced with ExecutorService.
- Null check added for battery status.
- Dependencies upgraded

## [0.10.5] - 2019-05-07

- Fixes potential NPE in `AppStateCollector.getIpAddresses()`

## [0.10.4] - 2018-07-30

- Switches to upstream jsonSchema2Pojo 
- Removes a few more dependencies  
- Removes a few outdated ProGuard entries

## [0.10.3] - 2018-05-31

- Adds functionality for calling `Sift.open` with a new config

## [0.10.2] - 2018-04-26

- Allows for setUserId outside of activity lifecycle  
- Fixes Null Pointer Exception on Google API Client

## [0.10.1] - 2018-04-14

- Updates README file and HelloSift sample app
- Does not null out instance state  
- Adds hook for passing custom `activityName` to `Sift.resume`

## [0.10.0] - 2018-04-06

This is a significant rewrite that we expect to greatly improve the performance and stability of the Android SDK. Please refer to the change log below as well as the updated README.md.

### Breaking changes

- Removed access to the Sift instance and renamed a few hooks. Everything should be invoked via the static call to the singleton class.
    - `Sift.get().save()`  is now  `Sift.pause()`
    - `Sift.get().setUserId(…)`  is now  `Sift.setUserId(…)`
    - `Sift.get().unsetUserId(…)`  is now  `Sift.unsetUserId(…)`
    - Removed support for  `Sift.get.upload(…)`
- Removed the optional location permissions from sift SDK AndroidManifest.xml. As such, you will need to add  `ACCESS_FINE_LOCATION`  and  `ACCESS_COARSE_LOCATION`  to your own application’s AndroidManifest.xml if you would like to enable location collection

### New functionality

- Added a new hook into the onResume lifecycle callback:  `Sift.resume()`

### Other important changes

- Removed OkHttp from the dependencies

## [0.9.13] - 2018-02-28

- Fixes the deadlock casued by synchronized calls in Queue and Uploader  `archive()`  functions which are invoked by the Sift singleton  `save()`

- Cuts the androidAppState batch size down to 8 from 32 due to OOM errors while trying to GSON serialize the string on old devices

## [0.9.12] - 2018-01-20

- Fixes RejectedExecutionException in AppStateCollector

## [0.9.11] - 2018-01-10

- Strips out logging in ProGuard release build  
- Moves unarchiving to background task  
- Various performance improvements
- Fix minor shutdown race condition in Uploader

## [0.9.10] - 2017-12-15

- Adds back defensive FieldNamingPolicy with GSON

## [0.9.9] - 2017-12-12

### Removed
- Jackson and proguard rules
- Synchronize from unarchives

### Added
- GSON and proguard rules
- Proguard rule for lang3
- Better retry logic on errors
- Performance improvements  

## [0.9.8] - 2017-11-11

- Fixes leaked Activity context

## [0.9.7] - 2017-10-27

### Changed
- Location collection mechanics  

### Removed
- List of installed apps collection

### Added
- More complete proguard configs for Jackson  
- `@Nullable` to `Sift.get()`

## [0.9.6] - 2017-09-14

- Uses ListRequestJson yaml schema

## [0.9.5] - 2017-08-26

- Collects device Build details for emulator detection
- Collects list of installed apps with package name 

## [0.9.4] - 2017-07-04

- Fixes RejectedExecutionException on uploader
- Collects SDK_VERSION in AppState events to be able to track event counts per version

## [0.9.3] - 2017-06-20

- Removes Guava dependency
- Fix encoding

## [0.9.2] - 2017-06-09

- Google API Client and gradle fixes
- Make SDK logs better

## [0.9.1] - 2017-06-07

- Adds Proguard rules to support Jackson 
- Fixes configuration for DeserializationFeature to Ignore unknown properties with Jackson

## [0.9.0] - 2017-05-24

- Sift SDK Public Beta Release
- Implement the core part of the SDK (event batching and uploading)
- Collects device properties like OS version, root detection etc... 
- Collects app state properties like battery, network address, etc...
- Add demo app HelloSift
