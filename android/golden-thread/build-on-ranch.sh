#!/bin/bash
# Build the Golden Thread debug APK user-space on a ranch node.
set -e
W=~/wavefront-build; mkdir -p $W; cd $W
[ -d jdk ] || { curl -sL -o jdk.tgz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" && mkdir jdk && tar xzf jdk.tgz -C jdk --strip-components=1; }
export JAVA_HOME=$W/jdk PATH=$W/jdk/bin:$PATH
[ -d sdk/cmdline-tools ] || { curl -sL -o tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" && mkdir -p sdk/cmdline-tools && unzip -q tools.zip -d sdk/cmdline-tools && mv sdk/cmdline-tools/cmdline-tools sdk/cmdline-tools/latest; }
export ANDROID_HOME=$W/sdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null 2>&1 || true
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools" > /dev/null
[ -d gradle ] || { curl -sL -o gradle.zip "https://services.gradle.org/distributions/gradle-8.7-bin.zip" && unzip -q gradle.zip && mv gradle-8.7 gradle; }
cp -r ~/wavefront-repo/android/golden-thread app-src
cp ~/wavefront-repo/shared/data/wavefront.json app-src/app/src/main/assets/wavefront.json
cd app-src && $W/gradle/bin/gradle assembleDebug --no-daemon -q
cp app/build/outputs/apk/debug/app-debug.apk $W/golden-thread.apk
echo "APK BUILT: $W/golden-thread.apk"
