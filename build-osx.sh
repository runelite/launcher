#!/bin/bash

set -e

JDK_VER="11.0.4"
JDK_BUILD="11"
PACKR_VERSION="runelite-1.0"

SIGNING_IDENTITY="Developer ID Application"
ALTOOL_USER="user@icloud.com"
ALTOOL_PASS="@keychain:altool-password"

if ! [ -f OpenJDK11U-jre_x64_mac_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz ] ; then
    curl -Lo OpenJDK11U-jre_x64_mac_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz \
        https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-${JDK_VER}%2B${JDK_BUILD}/OpenJDK11U-jre_x64_mac_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz
fi

echo "1647fded28d25e562811f7bce2092eb9c21d30608843b04250c023b40604ff26  OpenJDK11U-jre_x64_mac_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz" | shasum -c

# packr requires a "jdk" and pulls the jre from it - so we have to place it inside
# the jdk folder at jre/
if ! [ -d osx-jdk ] ; then
    tar zxf OpenJDK11U-jre_x64_mac_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz
    mkdir osx-jdk
    mv jdk-11.0.4+11-jre osx-jdk/jre

    # Move JRE out of Contents/Home/
    pushd osx-jdk/jre
    cp -r Contents/Home/* .
    popd
fi

if ! [ -f packr_${PACKR_VERSION}.jar ] ; then
    curl -Lo packr_${PACKR_VERSION}.jar \
        https://github.com/runelite/packr/releases/download/${PACKR_VERSION}/packr.jar
fi

echo "18b7cbaab4c3f9ea556f621ca42fbd0dc745a4d11e2a08f496e2c3196580cd53  packr_${PACKR_VERSION}.jar" | shasum -c

java -jar packr_${PACKR_VERSION}.jar \
    --platform \
    mac \
    --icon \
    packr/runelite.icns \
    --jdk \
    osx-jdk \
    --executable \
    RuneLite \
    --classpath \
    target/RuneLite.jar \
    --mainclass \
    net.runelite.launcher.Launcher \
    --vmargs \
    Drunelite.launcher.nojvm=true \
    Xmx512m \
    Xss2m \
    XX:CompileThreshold=1500 \
    Djna.nosys=true \
    --output \
    native-osx/RuneLite.app

cp target/filtered-resources/Info.plist native-osx/RuneLite.app/Contents

echo Setting world execute permissions on RuneLite
pushd native-osx/RuneLite.app
chmod g+x,o+x Contents/MacOS/RuneLite
popd

codesign -f -s "${SIGNING_IDENTITY}" --entitlements osx/signing.entitlements --options runtime native-osx/RuneLite.app || true

# create-dmg exits with an error code due to no code signing, but is still okay
# note we use Adam-/create-dmg as upstream does not support UDBZ
create-dmg --format UDBZ native-osx/RuneLite.app native-osx/ || true

mv native-osx/RuneLite\ *.dmg native-osx/RuneLite.dmg

xcrun altool --notarize-app --username "${ALTOOL_USER}" --password "${ALTOOL_PASS}" --primary-bundle-id runelite --file native-osx/RuneLite.dmg || true