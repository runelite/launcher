#!/bin/bash

set -e

PACKR_VERSION="runelite-1.8"
PACKR_HASH="ea9e8a9b276cc7548f85cf587c7bd3519104aa9b877f3d7b566fb8492d126744"

source .jdk-versions.sh

if ! [ -f win-aarch64_jre.zip ] ; then
    curl -Lo win-aarch64_jre.zip $WIN_AARCH64_LINK
fi

echo "$WIN_AARCH64_CHKSUM win-aarch64_jre.zip" | sha256sum -c

# packr requires a "jdk" and pulls the jre from it - so we have to place it inside
# the jdk folder at jre/
if ! [ -d win-aarch64-jdk ] ; then
    unzip win-aarch64_jre.zip
    mkdir win-aarch64-jdk
    jlink \
      --compress 2 \
      --strip-debug \
      --no-header-files \
      --no-man-pages \
      --output win-aarch64-jdk/jre \
      --module-path jdk-$WIN_AARCH64_VERSION/jmods \
      --add-modules java.base,java.compiler,java.datatransfer,java.xml,java.prefs,java.desktop,java.instrument \
      --add-modules java.logging,java.management,java.security.sasl,java.naming,java.rmi,java.management.rmi \
      --add-modules java.net.http,java.scripting,java.security.jgss,java.transaction.xa,java.sql,java.sql.rowset \
      --add-modules java.xml.crypto,java.se,java.smartcardio,jdk.accessibility,jdk.internal.jvmstat,jdk.attach \
      --add-modules jdk.charsets,jdk.compiler,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.crypto.mscapi,jdk.dynalink \
      --add-modules jdk.internal.ed,jdk.editpad,jdk.hotspot.agent,jdk.httpserver,jdk.internal.le,jdk.internal.opt \
      --add-modules jdk.jartool,jdk.javadoc,jdk.jcmd,jdk.management,jdk.management.agent,jdk.jconsole,jdk.jdeps \
      --add-modules jdk.jdwp.agent,jdk.jdi,jdk.jfr,jdk.jlink,jdk.jshell,jdk.jsobject,jdk.jstatd,jdk.localedata \
      --add-modules jdk.management.jfr,jdk.naming.dns,jdk.naming.ldap,jdk.naming.rmi,jdk.net,jdk.pack,jdk.rmic \
      --add-modules jdk.scripting.nashorn,jdk.scripting.nashorn.shell,jdk.sctp,jdk.security.auth,jdk.security.jgss \
      --add-modules jdk.unsupported,jdk.unsupported.desktop,jdk.xml.dom,jdk.zipfs
fi

if ! [ -f packr_${PACKR_VERSION}.jar ] ; then
    curl -Lo packr_${PACKR_VERSION}.jar \
        https://github.com/runelite/packr/releases/download/${PACKR_VERSION}/packr.jar
fi

echo "${PACKR_HASH}  packr_${PACKR_VERSION}.jar" | sha256sum -c

java -jar packr_${PACKR_VERSION}.jar \
    packr/win-aarch64-config.json

tools/rcedit-x64 native-win-aarch64/RuneLite.exe \
  --application-manifest packr/runelite.manifest \
  --set-icon runelite.ico

echo RuneLite.exe aarch64 sha256sum
sha256sum native-win-aarch64/RuneLite.exe

# We use the filtered iss file
iscc target/filtered-resources/runeliteaarch64.iss