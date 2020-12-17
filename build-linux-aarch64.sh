#!/bin/bash

set -e

JDK_VER="11.0.8"
JDK_BUILD="10"
PACKR_VERSION="runelite-1.1"
APPIMAGE_VERSION="12"

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

echo "ee3b0386d7a6474b042429e2fe7826fd40088258aec05707f0c722d773b5b1bd  packr_${PACKR_VERSION}.jar" | sha256sum -c

rm -rf native-linux-aarch64

java -jar packr_${PACKR_VERSION}.jar \
    --platform \
    linuxaarch64 \
    --jdk \
    linux-aarch64-jdk \
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
    native-linux-aarch64/RuneLite.AppDir/ \
    --resources \
    target/filtered-resources/runelite.desktop \
    appimage/runelite.png

pushd native-linux-aarch64/RuneLite.AppDir
mkdir -p jre/lib/amd64/server/
ln -s ../../server/libjvm.so jre/lib/amd64/server/ # packr looks for libjvm at this hardcoded path
popd

# Symlink AppRun -> RuneLite
pushd native-linux-aarch64/RuneLite.AppDir/
ln -s RuneLite AppRun
popd

if ! [ -f appimagetool-aarch64.AppImage ] ; then
    curl -Lo appimagetool-aarch64.AppImage \
        https://github.com/AppImage/AppImageKit/releases/download/$APPIMAGE_VERSION/appimagetool-aarch64.AppImage
    chmod +x appimagetool-aarch64.AppImage
fi

echo "c9d058310a4e04b9fbbd81340fff2b5fb44943a630b31881e321719f271bd41a  appimagetool-aarch64.AppImage" | sha256sum -c

# patch appimagetool to run on qemu https://github.com/AppImage/AppImageKit/issues/1056#issuecomment-643382397
sed "s|AI\x02|\x00\x00\x00|" appimagetool-aarch64.AppImage > appimagetool-aarch64.AppImage-patched
chmod +x appimagetool-aarch64.AppImage-patched

# instead of allowing fuse in this container, just extract the appimage fs and run it directly
rm -rf squashfs-root
./appimagetool-aarch64.AppImage-patched --appimage-extract

./squashfs-root/AppRun \
	native-linux-aarch64/RuneLite.AppDir/ \
	native-linux-aarch64/RuneLite-aarch64.AppImage
