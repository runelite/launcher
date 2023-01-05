#!/bin/bash

set -e

JDK_VER="11.0.16.1"
JDK_BUILD="1"
JDK_BUILD_SHORT="1"
JDK_HASH="85ca20dae21fb5949045df02cc47e71495a1903f77a97a93085596e5e96d0714"
PACKR_VERSION="runelite-1.7"
PACKR_HASH="f61c7faeaa364b6fa91eb606ce10bd0e80f9adbce630d2bae719aef78d45da61"

if ! [ -f OpenJDK11U-jre_x64_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip ] ; then
    curl -Lo OpenJDK11U-jre_x64_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip \
        https://github.com/adoptium/temurin11-binaries/releases/download/jdk-${JDK_VER}%2B${JDK_BUILD}/OpenJDK11U-jre_x64_windows_hotspot_${JDK_VER}_${JDK_BUILD_SHORT}.zip
fi

echo "${JDK_HASH} OpenJDK11U-jre_x64_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip" | sha256sum -c

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

echo "${PACKR_HASH}  packr_${PACKR_VERSION}.jar" | sha256sum -c

java -jar packr_${PACKR_VERSION}.jar \
    packr/win-x64-config.json

tools/rcedit-x64 native-win64/RuneLite.exe \
  --application-manifest packr/runelite.manifest \
  --set-icon runelite.ico

echo RuneLite.exe 64bit sha256sum
sha256sum native-win64/RuneLite.exe

# We use the filtered iss file
iscc target/filtered-resources/runelite.iss