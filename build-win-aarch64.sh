#!/bin/bash

set -e

PACKR_VERSION="runelite-1.8"
PACKR_HASH="ea9e8a9b276cc7548f85cf587c7bd3519104aa9b877f3d7b566fb8492d126744"

source .jdk-versions.sh

if ! [ -f win-aarch64_jre.zip ] ; then
    curl -Lo win-aarch64_jre.zip $WIN_AARCH64_LINK
fi

echo "$WIN_AARCH64_CHKSUM win-aarch64_jre.zip" | sha256sum -c

# packr requires a "jdk" and pulls the jre from it - so we have to place it inside
# the jdk folder at jre/
if ! [ -d win-aarch64-jdk ] ; then
    unzip win-aarch64_jre.zip
    mkdir win-aarch64-jdk
    mv jdk-$WIN_AARCH64_VERSION win-aarch64-jdk/jre
fi

if ! [ -f packr_${PACKR_VERSION}.jar ] ; then
    curl -Lo packr_${PACKR_VERSION}.jar \
        https://github.com/runelite/packr/releases/download/${PACKR_VERSION}/packr.jar
fi

echo "${PACKR_HASH}  packr_${PACKR_VERSION}.jar" | sha256sum -c

java -jar packr_${PACKR_VERSION}.jar \
    packr/win-aarch64-config.json

tools/rcedit-x64 native-win-aarch64/RuneLite.exe \
  --application-manifest packr/runelite.manifest \
  --set-icon runelite.ico

echo RuneLite.exe aarch64 sha256sum
sha256sum native-win-aarch64/RuneLite.exe

# We use the filtered iss file
iscc target/filtered-resources/runeliteaarch64.iss