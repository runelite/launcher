#!/bin/bash

set -e

PATH=$PATH:tools/create-dmg

APPBASE="build/macos-x64/RuneLite.app"

build() {
    echo Launcher sha256sum
    shasum -a 256 build/libs/RuneLite.jar

    pushd native
    cmake -DCMAKE_OSX_ARCHITECTURES=x86_64 -B build-x64 .
    cmake --build build-x64 --config Release
    popd

    source .jdk-versions.sh

    rm -rf build/macos-x64
    mkdir -p build/macos-x64

    if ! [ -f mac64_jre.tar.gz ] ; then
        curl -Lo mac64_jre.tar.gz $MAC_AMD64_LINK
    fi

    echo "$MAC_AMD64_CHKSUM  mac64_jre.tar.gz" | shasum -c

    mkdir -p $APPBASE/Contents/{MacOS,Resources}

    cp native/build-x64/src/RuneLite $APPBASE/Contents/MacOS/
    cp build/libs/RuneLite.jar $APPBASE/Contents/Resources/
    cp packr/macos-x64-config.json $APPBASE/Contents/Resources/config.json
    cp build/filtered-resources/Info.plist $APPBASE/Contents/
    cp osx/runelite.icns $APPBASE/Contents/Resources/icons.icns

    tar zxf mac64_jre.tar.gz
    mkdir $APPBASE/Contents/Resources/jre
    mv $MAC_AMD64_RELEASE-jre/Contents/Home/* $APPBASE/Contents/Resources/jre

    echo Setting world execute permissions on RuneLite
    pushd $APPBASE
    chmod g+x,o+x Contents/MacOS/RuneLite
    popd

    echo Dumping RuneLite binary
    otool -l $APPBASE/Contents/MacOS/RuneLite

    RL_MINOS=$(otool -l $APPBASE/Contents/MacOS/RuneLite | awk '/LC_BUILD_VERSION/{f=1} f && /minos/{print $2; exit}')
    JAVA_MINOS=$(otool -l $APPBASE/Contents/Resources/jre/lib/libjava.dylib | awk '/LC_BUILD_VERSION/{f=1} f && /minos/{print $2; exit}')
    echo "minos: RL: $RL_MINOS Java: $JAVA_MINOS"

    if [ "$(printf '%s\n%s\n' "$RL_MINOS" "$JAVA_MINOS" | sort -V | tail -n1)" = "$JAVA_MINOS" ] && [ "$JAVA_MINOS" != "$RL_MINOS" ] ; then
        echo "Java minimum macOS version ($JAVA_MINOS) is greater than RuneLite minimum macOS version ($RL_MINOS)"
        exit 1
    fi
}

dmg() {
    SIGNING_IDENTITY="Developer ID Application"
    codesign -f -s "${SIGNING_IDENTITY}" --entitlements osx/signing.entitlements --options runtime $APPBASE || true

    create-dmg \
      --volname RuneLite \
      --volicon osx/runelite.icns \
      --window-size 660 400 \
      --icon-size 160 \
      --icon RuneLite.app 180 170 \
      --app-drop-link 480 170 \
      --format ULFO \
      --filesystem APFS \
      RuneLite-x64.dmg \
      $APPBASE

    # dump for CI
    hdiutil imageinfo RuneLite-x64.dmg

    # Notarize app
    if xcrun notarytool submit RuneLite-x64.dmg --wait --keychain-profile "AC_PASSWORD" ; then
        xcrun stapler staple RuneLite-x64.dmg
    fi
}

while test $# -gt 0; do
  case "$1" in
    --build)
      build
      shift
      ;;
    --dmg)
      dmg
      shift
      ;;
    *)
      break
      ;;
  esac
done
