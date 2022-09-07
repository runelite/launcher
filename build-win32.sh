#!/bin/bash

set -e

JDK_VER="11.0.16"
JDK_BUILD="8"
JDK_BUILD_SHORT="8"
PACKR_VERSION="runelite-1.5"
PACKR_HASH="b38283101e5623f6b3ce2b35052a229c5c2ed842741651ca201f0145fd79f1f9"

if ! [ -f OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip ] ; then
    curl -Lo OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip \
        https://github.com/adoptium/temurin11-binaries/releases/download/jdk-${JDK_VER}%2B${JDK_BUILD}/OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD_SHORT}.zip
fi

echo "4da2313441de81e289b25b5ea36f10ab14f8c0f16085fb33eabcda502b1f6457 OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip" | sha256sum -c

# packr requires a "jdk" and pulls the jre from it - so we have to place it inside
# the jdk folder at jre/
if ! [ -d win32-jdk ] ; then
    unzip OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip
    mkdir win32-jdk
    mv jdk-$JDK_VER+$JDK_BUILD_SHORT-jre win32-jdk/jre
fi

if ! [ -f packr_${PACKR_VERSION}.jar ] ; then
    curl -Lo packr_${PACKR_VERSION}.jar \
        https://github.com/runelite/packr/releases/download/${PACKR_VERSION}/packr.jar
fi

echo "${PACKR_HASH}  packr_${PACKR_VERSION}.jar" | sha256sum -c

java -jar packr_${PACKR_VERSION}.jar \
    packr/win-x86-config.json

tools/rcedit-x64 native-win32/RuneLite.exe \
  --application-manifest packr/runelite.manifest \
  --set-icon runelite.ico

# We use the filtered iss file
iscc target/filtered-resources/runelite32.iss