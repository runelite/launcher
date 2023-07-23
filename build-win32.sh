#!/bin/bash

set -e

PACKR_VERSION="runelite-1.9"
PACKR_HASH="544efb4a88f561aa40a6dc9453d13a00231f10ed867f741ac7f6ded2757c1b8d"

source .jdk-versions.sh

if ! [ -f win32_jre.zip ] ; then
    curl -Lo win32_jre.zip $WIN32_LINK
fi

echo "$WIN32_CHKSUM win32_jre.zip" | sha256sum -c

# packr requires a "jdk" and pulls the jre from it - so we have to place it inside
# the jdk folder at jre/
if ! [ -d win32-jdk ] ; then
    unzip win32_jre.zip
    mkdir win32-jdk
    mv jdk-$WIN32_VERSION-jre win32-jdk/jre
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

dumpbin //HEADERS native-win32/RuneLite.exe

# We use the filtered iss file
iscc target/filtered-resources/runelite32.iss