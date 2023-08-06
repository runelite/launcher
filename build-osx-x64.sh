#!/bin/bash

set -e

SIGNING_IDENTITY="Developer ID Application"

source .jdk-versions.sh

rm -rf build/macos-x64
mkdir -p build/macos-x64

if ! [ -f mac64_jre.tar.gz ] ; then
    curl -Lo mac64_jre.tar.gz $MAC_AMD64_LINK
fi

echo "$MAC_AMD64_CHKSUM  mac64_jre.tar.gz" | shasum -c

APPBASE="build/macos-x64/RuneLite.app"

mkdir -p $APPBASE/Contents/{MacOS,Resources}

cp native/build-x64/src/RuneLite $APPBASE/Contents/MacOS/
cp target/RuneLite.jar $APPBASE/Contents/Resources/
cp packr/macos-x64-config.json $APPBASE/Contents/Resources/config.json
cp target/filtered-resources/Info.plist $APPBASE/Contents/
cp osx/runelite.icns $APPBASE/Contents/Resources/icons.icns

tar zxf mac64_jre.tar.gz
mkdir $APPBASE/Contents/Resources/jre
mv jdk-$MAC_AMD64_VERSION-jre/Contents/Home/* $APPBASE/Contents/Resources/jre

echo Setting world execute permissions on RuneLite
pushd $APPBASE
chmod g+x,o+x Contents/MacOS/RuneLite
popd

codesign -f -s "${SIGNING_IDENTITY}" --entitlements osx/signing.entitlements --options runtime $APPBASE || true

# create-dmg exits with an error code due to no code signing, but is still okay
# note we use Adam-/create-dmg as upstream does not support UDBZ
create-dmg --format UDBZ $APPBASE . || true
mv RuneLite\ *.dmg RuneLite-x64.dmg

if ! hdiutil imageinfo RuneLite-x64.dmg | grep -q "Format: UDBZ" ; then
    echo "Format of resulting dmg was not UDBZ, make sure your create-dmg has support for --format"
    exit 1
fi

# Notarize app
if xcrun notarytool submit RuneLite-x64.dmg --wait --keychain-profile "AC_PASSWORD" ; then
    xcrun stapler staple RuneLite-x64.dmg
fi
