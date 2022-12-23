#!/bin/bash

set -e

JDK_VER="11.0.16.1"
JDK_BUILD="1"
JDK_BUILD_SHORT="1"
JDK_HASH="a2c666055519c344017bd111dc59a881567850ed32a49d2752ce2812c3a38912"
PACKR_VERSION="runelite-1.6"
PACKR_HASH="df6e816b75753bac6f6436611f5dcf1874b52f77da1c07296dbacf14852f8677"

if ! [ -f OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip ] ; then
    curl -Lo OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip \
        https://github.com/adoptium/temurin11-binaries/releases/download/jdk-${JDK_VER}%2B${JDK_BUILD}/OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD_SHORT}.zip
fi

echo "${JDK_HASH} OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip" | sha256sum -c

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

echo RuneLite.exe 32bit sha256sum
sha256sum native-win32/RuneLite.exe

# We use the filtered iss file
iscc target/filtered-resources/runelite32.iss