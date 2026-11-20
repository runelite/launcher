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

source util.sh

rm -rf build/win-aarch64
mkdir -p build/win-aarch64

cp native/build-aarch64/src/Release/RuneLite.exe build/win-aarch64/
cp build/libs/RuneLite.jar build/win-aarch64/
cp native/config.json build/win-aarch64/config.json
cp liblauncher/buildaarch64/Release/launcher_aarch64.dll build/win-aarch64/

download_jre WIN_AARCH64 win-aarch64_jre.zip build/win-aarch64/jre

update_jre_cacerts WIN64 win64_jre.zip build/win-aarch64/jre

echo RuneLite.exe aarch64 sha256sum
sha256sum build/win-aarch64/RuneLite.exe

dumpbin //HEADERS build/win-aarch64/RuneLite.exe

# We use the filtered iss file
iscc build/filtered-resources/runeliteaarch64.iss
