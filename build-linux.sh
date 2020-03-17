#!/bin/bash

set -e

JDK_VER="11.0.4"
JDK_BUILD="11"
PACKR_VERSION="runelite-1.0"

# Check if there's a client jar file - If there's no file the AppImage will not work but will still be built.
if ! [ -e build/libs/OpenOSRS-shaded.jar ]
then
  echo "build/libs/OpenOSRS-shaded.jar not found, exiting"
  exit 1
fi

if ! [ -f OpenJDK11U-jre_x64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz ] ; then
    curl -Lo OpenJDK11U-jre_x64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz \
        https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-${JDK_VER}%2B${JDK_BUILD}/OpenJDK11U-jre_x64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz
fi

rm -f packr.jar
curl -o packr.jar https://libgdx.badlogicgames.com/ci/packr/packr.jar

echo "70d2cc675155476f1d8516a7ae6729d44681e4fad5a6fc8dfa65cab36a67b7e0 OpenJDK11U-jre_x64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz" | sha256sum -c

# packr requires a "jdk" and pulls the jre from it - so we have to place it inside
# the jdk folder at jre/
if ! [ -d linux-jdk ] ; then
    tar zxf OpenJDK11U-jre_x64_linux_hotspot_${JDK_VER}_${JDK_BUILD}.tar.gz
    mkdir linux-jdk
    mv jdk-11.0.4+11-jre linux-jdk/jre
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
    OpenOSRS \
    --classpath \
    build/libs/OpenOSRS-shaded.jar \
    --mainclass \
    net.runelite.launcher.Launcher \
    --vmargs \
    Drunelite.launcher.nojvm=true \
    Xmx512m \
    Xss2m \
    XX:CompileThreshold=1500 \
    Djna.nosys=true \
    --output \
    native-linux/OpenOSRS.AppDir/ \
    --resources \
    build/filtered-resources/openosrs.desktop \
    appimage/openosrs.png

pushd native-linux/OpenOSRS.AppDir
mkdir -p jre/lib/amd64/server/
ln -s ../../server/libjvm.so jre/lib/amd64/server/ # packr looks for libjvm at this hardcoded path
popd

# Symlink AppRun -> RuneLite
pushd native-linux/OpenOSRS.AppDir/
ln -s OpenOSRS AppRun
popd

curl -Lo appimagetool-x86_64.AppImage https://github.com/AppImage/AppImageKit/releases/download/12/appimagetool-x86_64.AppImage
chmod 755 appimagetool-x86_64.AppImage

./appimagetool-x86_64.AppImage \
	native-linux/OpenOSRS.AppDir/ \
	native-linux/OpenOSRS.AppImage
