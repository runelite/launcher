#!/bin/bash

set -e

JDK_VER="11.0.16.1"
JDK_BUILD="1"
JDK_HASH="1ffe1a682e8179e35238bf3f93aba0cb185850e202c676f41d38cb0561883eda"
PACKR_VERSION="runelite-1.7"
PACKR_HASH="f61c7faeaa364b6fa91eb606ce10bd0e80f9adbce630d2bae719aef78d45da61"
APPIMAGE_VERSION="13"

umask 022

if ! [ -f OpenJDK11U-jre_x64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz ] ; then
    curl -Lo OpenJDK11U-jre_x64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz \
        https://github.com/adoptium/temurin11-binaries/releases/download/jdk-${JDK_VER}%2B${JDK_BUILD}/OpenJDK11U-jre_x64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz
fi

echo "${JDK_HASH} OpenJDK11U-jre_x64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz" | sha256sum -c

# packr requires a "jdk" and pulls the jre from it - so we have to place it inside
# the jdk folder at jre/
if ! [ -d linux-jdk ] ; then
    tar zxf OpenJDK11U-jre_x64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz
    mkdir linux-jdk
    mv jdk-$JDK_VER+$JDK_BUILD-jre linux-jdk/jre
fi

if ! [ -f packr_${PACKR_VERSION}.jar ] ; then
    curl -Lo packr_${PACKR_VERSION}.jar \
        https://github.com/runelite/packr/releases/download/${PACKR_VERSION}/packr.jar
fi

echo "${PACKR_HASH}  packr_${PACKR_VERSION}.jar" | sha256sum -c

# Note: Host umask may have checked out this directory with g/o permissions blank
chmod -R u=rwX,go=rX appimage
# ...ditto for the build process
chmod 644 target/RuneLite.jar

java -jar packr_${PACKR_VERSION}.jar \
    packr/linux-x64-config.json

pushd native-linux-x86_64/RuneLite.AppDir
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

echo "df3baf5ca5facbecfc2f3fa6713c29ab9cefa8fd8c1eac5d283b79cab33e4acb  appimagetool-x86_64.AppImage" | sha256sum -c

./appimagetool-x86_64.AppImage \
	native-linux-x86_64/RuneLite.AppDir/ \
	native-linux-x86_64/RuneLite.AppImage
