# WaEnhancer Local — WhatsApp 2.26.42.74

Historical record of the local compatibility baseline before WaTrobos branding.
Package names and build identity described below belong to that earlier build.
Use README.md for the current WaTrobos identity and build instructions.

Based on Dev4Mod/WaEnhancer commit 36c22aeda030327704b5ee0b099f060fd42c1120.
The existing GPL-3.0 license and attribution are retained.

## Scope

This is a local experimental adaptation of the original module, not WaEnhancerX.
All 67 entries in the original FeatureRegistry and their implementations are retained.
Keeping feature code does not establish that every feature works on the new host.
The initial target is WhatsApp Messenger com.whatsapp 2.26.42.74 on Android 16,
Vector 2.2 (3080). Other new Messenger versions and new Business versions are not admitted.

## Changes

- Resolve the host's HomeLegacyBottomNavigationView before the older
  BottomNavigationView suffix search. The observed new class inherits
  getMaxItemCount from X.1Cn in the installed target APK.
- Do not install SeparateGroup hooks when the feature is disabled.
- Add an exact experimental version entry for 2.26.42.74, with a shared matcher
  for the manager and host. Existing .xx rules still match their own branches.
- When built with -PlocalBuild=true, install the Messenger flavor as
  com.wmods.wppenhacer.local with label WaEnhancer Local. The original module
  remains installed. Messenger/Business selection uses the build flavor.
- A source archive without Git metadata uses UNKNOWN for the displayed revision
  instead of failing while taking a substring from an empty git result.

## Build

Use JDK 21 and Android SDK with Platform 37, NDK 28.2.13676358, CMake 3.22.1.
The Gradle wrapper downloads Gradle 9.6.1. Native submodules must be present:

```
git submodule update --init --recursive
gradlew.bat :app:assembleWhatsappDebug :app:testWhatsappDebugUnitTest -PlocalBuild=true
```

The debug APK uses the developer machine's Android debug signing key. Do not
uninstall the official module to install this build; the package IDs differ.
Enable only one WaEnhancer variant for com.whatsapp during testing.

## Acceptance

Device acceptance and any remaining issues are recorded in the separately
delivered Indonesian test report. Build success and hook-registration timing
are not substitutes for functional tests of messaging/privacy/media behavior.

Both Debug and optimized Release passed the device tab test on the target:
Chats/Groups each showed nine visible rows, no visible-title overlap, stable
Chats rows after returning, no error dialogs, and no process restart while
switching tabs. Relaunching WhatsApp retained the tabs. All nine unit tests
passed. These checks do not establish acceptance of every other feature.
