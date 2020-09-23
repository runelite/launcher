#!/bin/bash

set -e

JDK_VER="11.0.8"
JDK_BUILD="10"
PACKR_VERSION="runelite-1.0"
APPIMAGE_VERSION="12"

if ! [ -f OpenJDK11U-jre_x64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz ] ; then
    curl -Lo OpenJDK11U-jre_x64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz \
        https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-${JDK_VER}%2B${JDK_BUILD}/OpenJDK11U-jre_x64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz
fi

echo "98615b1b369509965a612232622d39b5cefe117d6189179cbad4dcef2ee2f4e1 OpenJDK11U-jre_x64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz" | sha256sum -c

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

echo "18b7cbaab4c3f9ea556f621ca42fbd0dc745a4d11e2a08f496e2c3196580cd53  packr_${PACKR_VERSION}.jar" | sha256sum -c

java -jar packr_${PACKR_VERSION}.jar \
    --platform \
    linux64 \
    --jdk \
    linux-jdk \
    --executable \
    RuneLite \
    --classpath \
    target/RuneLite.jar \
    --mainclass \
    net.runelite.launcher.Launcher \
    --vmargs \
    Drunelite.launcher.nojvm=true \
    Xmx512m \
    Xss2m \
    XX:CompileThreshold=1500 \
    Djna.nosys=true \
    --output \
    native-linux/RuneLite.AppDir/ \
    --resources \
    target/filtered-resources/runelite.desktop \
    appimage/runelite.png

pushd native-linux/RuneLite.AppDir
mkdir -p jre/lib/amd64/server/
ln -s ../../server/libjvm.so jre/lib/amd64/server/ # packr looks for libjvm at this hardcoded path
popd

# Symlink AppRun -> RuneLite
pushd native-linux/RuneLite.AppDir/
ln -s RuneLite AppRun
popd

if ! [ -f appimagetool-x86_64.AppImage ] ; then
    curl -Lo appimagetool-x86_64.AppImage \
        https://github.com/AppImage/AppImageKit/releases/download/$APPIMAGE_VERSION/appimagetool-x86_64.AppImage
    chmod +x appimagetool-x86_64.AppImage
fi

echo "d918b4df547b388ef253f3c9e7f6529ca81a885395c31f619d9aaf7030499a13  appimagetool-x86_64.AppImage" | sha256sum -c

./appimagetool-x86_64.AppImage \
	native-linux/RuneLite.AppDir/ \
	native-linux/RuneLite.AppImage
