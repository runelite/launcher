#!/bin/bash

set -e

JDK_VER="11.0.16"
JDK_BUILD="8"
JDK_BUILD_SHORT="8"
PACKR_VERSION="runelite-1.3"

if ! [ -f OpenJDK11U-jre_x64_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip ] ; then
    curl -Lo OpenJDK11U-jre_x64_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip \
        https://github.com/adoptium/temurin11-binaries/releases/download/jdk-${JDK_VER}%2B${JDK_BUILD}/OpenJDK11U-jre_x64_windows_hotspot_${JDK_VER}_${JDK_BUILD_SHORT}.zip
fi

echo "47ed5341463829e1c74f3a05e5de3edf6278b3fa9ad5b45e0882f87aea3d83c5 OpenJDK11U-jre_x64_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip" | sha256sum -c

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

tools/rcedit-x64 native-win64/RuneLite.exe \
  --application-manifest packr/runelite.manifest \
  --set-icon runelite.ico

# We use the filtered iss file
iscc target/filtered-resources/runelite.iss