# Sift Science Android SDK

- [Introduction](#introduction)
- [Installation](#installation)
- [Integration](#integration)
  - [Application Integration](#application)
  - [Custom Integration](#custom)

<a name="introduction"></a>
## Introduction

The Sift Android SDK collects and sends device information and application interaction events to Sift Science.

<a name="installation"></a>
## Installing Sift

Add Sift to your application’s build.gradle file:

```
dependencies {
  ...
  compile ’com.siftscience:sift-android-private:0.0.3’
  ...
}
```

You may also need to add the following `packagingOptions` to the main android block:

```
android {
  ...
  packagingOptions {
    exclude ’META-INF/DEPENDENCIES.txt’
    exclude ’META-INF/LICENSE.txt’
    exclude ’META-INF/NOTICE.txt’
    exclude ’META-INF/NOTICE’
    exclude ’META-INF/LICENSE’
    exclude ’META-INF/DEPENDENCIES’
    exclude ’META-INF/notice.txt’
    exclude ’META-INF/license.txt’
    exclude ’META-INF/dependencies.txt’
    exclude ’META-INF/LGPL2.1’
  }
  ...
}
```

<a name="integration"></a>
## Add Sift to your application

There are two different integration paths to take for incorporating Sift into your application.
The first one will be detailed below in the **Application Integration** section.
Follow these instructions if your application flow is primarily based on
Activities. If your application flow is based on a combination of Activities
and Fragments, please refer to the the **Custom Integration** section.

<a name="application"></a>
### Application Integration

<a name="custom"></a>
### Custom Integration
