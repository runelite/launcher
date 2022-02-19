#!/bin/bash

set -e

JDK_VER="11.0.8"
JDK_BUILD="10"
PACKR_VERSION="runelite-1.3"
APPIMAGE_VERSION="12"

umask 022

if ! [ -f OpenJDK11U-jre_aarch64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz ] ; then
    curl -Lo OpenJDK11U-jre_aarch64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz \
        https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-${JDK_VER}%2B${JDK_BUILD}/OpenJDK11U-jre_aarch64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz
fi

echo "286c869dbaefda9b470ae71d1250fdecf9f06d8da97c0f7df9021d381d749106 OpenJDK11U-jre_aarch64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz" | sha256sum -c

# packr requires a "jdk" and pulls the jre from it - so we have to place it inside
# the jdk folder at jre/
if ! [ -d linux-aarch64-jdk ] ; then
    tar zxf OpenJDK11U-jre_aarch64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz
    mkdir linux-aarch64-jdk
    mv jdk-$JDK_VER+$JDK_BUILD-jre linux-aarch64-jdk/jre
fi

if ! [ -f packr_${PACKR_VERSION}.jar ] ; then
    curl -Lo packr_${PACKR_VERSION}.jar \
        https://github.com/runelite/packr/releases/download/${PACKR_VERSION}/packr.jar
fi

echo "f200fb7088dbb5e61e0835fe7b0d7fc1310beda192dacd764927567dcd7c4f0f  packr_${PACKR_VERSION}.jar" | sha256sum -c

# Note: Host umask may have checked out this directory with g/o permissions blank
chmod -R u=rwX,go=rX appimage
# ...ditto for the build process
chmod 644 target/RuneLite.jar

rm -rf native-linux-aarch64

java -jar packr_${PACKR_VERSION}.jar \
    packr/linux-aarch64-config.json

pushd native-linux-aarch64/RuneLite.AppDir
mkdir -p jre/lib/amd64/server/
ln -s ../../server/libjvm.so jre/lib/amd64/server/ # packr looks for libjvm at this hardcoded path

# Symlink AppRun -> RuneLite
ln -s RuneLite AppRun

# Ensure RuneLite is executable to all users
chmod 755 RuneLite
popd

if ! [ -f appimagetool-x86_64.AppImage ] ; then
    curl -Lo appimagetool-x86_64.AppImage \
        https://github.com/AppImage/AppImageKit/releases/download/$APPIMAGE_VERSION/appimagetool-x86_64.AppImage
    chmod +x appimagetool-x86_64.AppImage
fi

echo "d918b4df547b388ef253f3c9e7f6529ca81a885395c31f619d9aaf7030499a13  appimagetool-x86_64.AppImage" | sha256sum -c

if ! [ -f runtime-aarch64 ] ; then
    curl -Lo runtime-aarch64 \
	    https://github.com/AppImage/AppImageKit/releases/download/$APPIMAGE_VERSION/runtime-aarch64
fi

echo "207f8955500cfe8dd5b824ca7514787c023975e083b0269fc14600c380111d85  runtime-aarch64" | sha256sum -c

ARCH=arm_aarch64 ./appimagetool-x86_64.AppImage \
	--runtime-file runtime-aarch64  \
	native-linux-aarch64/RuneLite.AppDir/ \
	native-linux-aarch64/RuneLite-aarch64.AppImage
