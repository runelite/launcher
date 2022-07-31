#!/bin/bash

set -e

JDK_VER="11.0.8"
JDK_BUILD="10"
JDK_BUILD_SHORT="10"
PACKR_VERSION="runelite-1.3"

if ! [ -f OpenJDK11U-jre_x64_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip ] ; then
    curl -Lo OpenJDK11U-jre_x64_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip \
        https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-${JDK_VER}%2B${JDK_BUILD}/OpenJDK11U-jre_x64_windows_hotspot_${JDK_VER}_${JDK_BUILD_SHORT}.zip
fi

echo "fefa05395dbccfe072a8b6fbfebecf797ed81e18cb1aa4ed093c653d316b3f56 OpenJDK11U-jre_x64_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip" | sha256sum -c

# packr requires a "jdk" and pulls the jre from it - so we have to place it inside
# the jdk folder at jre/
if ! [ -d win64-jdk ] ; then
    unzip OpenJDK11U-jre_x64_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip
    mkdir win64-jdk
    mv jdk-$JDK_VER+$JDK_BUILD_SHORT-jre win64-jdk/jre
fi

if ! [ -f packr_${PACKR_VERSION}.jar ] ; then
    curl -Lo packr_${PACKR_VERSION}.jar \
        https://github.com/runelite/packr/releases/download/${PACKR_VERSION}/packr.jar
fi

echo "f200fb7088dbb5e61e0835fe7b0d7fc1310beda192dacd764927567dcd7c4f0f  packr_${PACKR_VERSION}.jar" | sha256sum -c

java -jar packr_${PACKR_VERSION}.jar \
    packr/win-x64-config.json

# modify packr exe manifest to enable Windows dpi scaling
resourcehacker \
    -open native-win64/RuneLite.exe \
    -save native-win64/RuneLite.exe \
    -action addoverwrite \
    -res packr/runelite.manifest \
    -mask MANIFEST,1,

# packr on Windows doesn't support icons, so we use resourcehacker to include it

resourcehacker \
    -open native-win64/RuneLite.exe \
    -save native-win64/RuneLite.exe \
    -action add \
    -res runelite.ico \
    -mask ICONGROUP,MAINICON,

# We use the filtered iss file
iscc target/filtered-resources/runelite.iss