#!/bin/bash

set -e

JDK_VER="11.0.4"
JDK_BUILD="11"
PACKR_VERSION="runelite-1.0"

if ! [ -f OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip ] ; then
    curl -Lo OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip \
        https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-${JDK_VER}%2B${JDK_BUILD}/OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip
fi

rm -f packr.jar
curl -o packr.jar https://libgdx.badlogicgames.com/ci/packr/packr.jar

echo "24eb66c5858c09c58a50b51df98b8dd6f75151a2b9f8e2e822441fa9f29009b6 OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip" | sha256sum -c

# packr requires a "jdk" and pulls the jre from it - so we have to place it inside
# the jdk folder at jre/
if ! [ -d win32-jdk ] ; then
    unzip OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip
    mkdir win32-jdk
    mv jdk-11.0.4+11-jre win32-jdk/jre
fi

if ! [ -f packr_${PACKR_VERSION}.jar ] ; then
    curl -Lo packr_${PACKR_VERSION}.jar \
        https://github.com/runelite/packr/releases/download/${PACKR_VERSION}/packr.jar
fi

echo "18b7cbaab4c3f9ea556f621ca42fbd0dc745a4d11e2a08f496e2c3196580cd53  packr_${PACKR_VERSION}.jar" | sha256sum -c

java -jar packr_${PACKR_VERSION}.jar \
    --platform \
    windows32 \
    --jdk \
    win32-jdk \
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
    native-win32

# modify packr exe manifest to enable Windows dpi scaling
"C:\Program Files (x86)\Resource Hacker\ResourceHacker.exe" \
    -open native-win32/OpenOSRS.exe \
    -save native-win32/OpenOSRS.exe \
    -action addoverwrite \
    -res packr/openosrs.manifest \
    -mask MANIFEST,1,

# packr on Windows doesn't support icons, so we use resourcehacker to include it
"C:\Program Files (x86)\Resource Hacker\ResourceHacker.exe" \
    -open native-win32/OpenOSRS.exe \
    -save native-win32/OpenOSRS.exe \
    -action add \
    -res openosrs.ico \
    -mask ICONGROUP,MAINICON,

if ! [ -f vcredist_x86.exe ] ; then
    # Visual C++ Redistributable Packages for Visual Studio 2013
    curl -Lo vcredist_x86.exe https://download.microsoft.com/download/2/E/6/2E61CFA4-993B-4DD4-91DA-3737CD5CD6E3/vcredist_x86.exe
fi

echo "a22895e55b26202eae166838edbe2ea6aad00d7ea600c11f8a31ede5cbce2048 *vcredist_x86.exe" | sha256sum -c

# We use the filtered iss file
"C:\Program Files (x86)\Inno Setup 6\ISCC.exe" build/filtered-resources/openosrs32.iss