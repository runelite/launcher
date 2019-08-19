#!/bin/bash

set -e

JDK_VER="11.0.4"
JDK_BUILD="11"

if ! [ -f OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip ] ; then
    curl -Lo OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip \
        https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-${JDK_VER}%2B${JDK_BUILD}/OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip
fi

echo "24eb66c5858c09c58a50b51df98b8dd6f75151a2b9f8e2e822441fa9f29009b6 OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip" | sha256sum -c

# packr requires a "jdk" and pulls the jre from it - so we have to place it inside
# the jdk folder at jre/
if ! [ -d win32-jdk ] ; then
    unzip OpenJDK11U-jre_x86-32_windows_hotspot_${JDK_VER}_${JDK_BUILD}.zip
    mkdir win32-jdk
    mv jdk-11.0.4+11-jre win32-jdk/jre
fi

java -jar packr.jar \
    --platform \
    windows32 \
    --jdk \
    win32-jdk \
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
    native-win32

# modify packr exe manifest to enable Windows dpi scaling
resourcehacker \
    -open native-win32/RuneLite.exe \
    -save native-win32/RuneLite.exe \
    -action addoverwrite \
    -res packr/runelite.manifest \
    -mask MANIFEST,1,

# packr on Windows doesn't support icons, so we use resourcehacker to include it
resourcehacker \
    -open native-win32/RuneLite.exe \
    -save native-win32/RuneLite.exe \
    -action add \
    -res runelite.ico \
    -mask ICONGROUP,MAINICON,

if ! [ -f vcredist_x86.exe ] ; then
    # Visual C++ Redistributable Packages for Visual Studio 2013
    curl -Lo vcredist_x86.exe https://download.microsoft.com/download/2/E/6/2E61CFA4-993B-4DD4-91DA-3737CD5CD6E3/vcredist_x86.exe
fi

echo "a22895e55b26202eae166838edbe2ea6aad00d7ea600c11f8a31ede5cbce2048 *vcredist_x86.exe" | sha256sum -c

# We use the filtered iss file
iscc target/filtered-resources/runelite32.iss