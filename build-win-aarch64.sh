#!/bin/bash

set -e

echo Launcher sha256sum
sha256sum build/libs/RuneLite.jar

cmake -S liblauncher -B liblauncher/buildaarch64 -A ARM64
cmake --build liblauncher/buildaarch64 --config Release

pushd native
cmake -B build-aarch64 -A ARM64
cmake --build build-aarch64 --config Release
popd

source .jdk-versions.sh

rm -rf build/win-aarch64
mkdir -p build/win-aarch64

if ! [ -f win-aarch64_jre.zip ] ; then
    curl -Lo win-aarch64_jre.zip $WIN_AARCH64_LINK
fi

echo "$WIN_AARCH64_CHKSUM win-aarch64_jre.zip" | sha256sum -c

cp native/build-aarch64/src/Release/RuneLite.exe build/win-aarch64/
cp build/libs/RuneLite.jar build/win-aarch64/
cp packr/win-aarch64-config.json build/win-aarch64/config.json
cp liblauncher/buildaarch64/Release/launcher_aarch64.dll build/win-aarch64/

unzip win-aarch64_jre.zip
mv $WIN_AARCH64_RELEASE-jre build/win-aarch64/jre

echo RuneLite.exe aarch64 sha256sum
sha256sum build/win-aarch64/RuneLite.exe

dumpbin //HEADERS build/win-aarch64/RuneLite.exe

# We use the filtered iss file
iscc build/filtered-resources/runeliteaarch64.iss