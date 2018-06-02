#!/bin/bash

#creates .deb package for the runelite
#launch as root after `mvn package`

PACKAGE_NAME=runelite
PACKAGE_VERSION="1.5.3"
SOURCE_DIR=$PWD
TEMP_DIR="/tmp"
LICENSE_URL=https://raw.githubusercontent.com/runelite/runelite/master/LICENSE
 
mkdir -p $TEMP_DIR/runelite
mkdir -p $TEMP_DIR/debian/DEBIAN
mkdir -p $TEMP_DIR/debian/lib
mkdir -p $TEMP_DIR/debian/usr/games
mkdir -p $TEMP_DIR/debian/usr/share/applications
mkdir -p $TEMP_DIR/debian/usr/share/$PACKAGE_NAME
mkdir -p $TEMP_DIR/debian/usr/share/doc/$PACKAGE_NAME
mkdir -p $TEMP_DIR/debian/usr/share/common-licenses/$PACKAGE_NAME
 
echo "Package: $PACKAGE_NAME" > $TEMP_DIR/debian/DEBIAN/control
echo "Version: $PACKAGE_VERSION" >> $TEMP_DIR/debian/DEBIAN/control
cat <<EOT >> $TEMP_DIR/debian/DEBIAN/control
Section: games
Priority: optional
Architecture: all
Maintainer: Adam <Adam@sigterm.info> 
Description: RuneLite client for the OldSchool RuneScape.
Depends: openjdk-8-jre | openjdk-7-jre | openjdk-6-jre | oracle-java8-installer | oracle-java7-installer | oracle-java6-installer | jarwrapper
EOT

cat <<EOT > $TEMP_DIR/debian/usr/share/applications/RuneLite.desktop
[Desktop Entry]
Encoding=UTF-8
Name=RuneLite
Comment=OldSchool RuneScape client
Terminal=false
Type=Application
Categories=Game
StartupNotify=true
EOT
echo "Exec=/usr/games/$PACKAGE_NAME" >> $TEMP_DIR/debian/usr/share/applications/RuneLite.desktop
echo "Icon=/usr/share/$PACKAGE_NAME/runelite.png" >> $TEMP_DIR/debian/usr/share/applications/RuneLite.desktop

wget $LICENSE_URL -O $TEMP_DIR/debian/usr/share/common-licenses/$PACKAGE_NAME/copyright
 
cp target/RuneLite.jar $TEMP_DIR/debian/usr/share/$PACKAGE_NAME/
echo "#!/bin/bash" > $TEMP_DIR/debian/usr/games/$PACKAGE_NAME
echo "java -jar /usr/share/$PACKAGE_NAME/RuneLite.jar" >> $TEMP_DIR/debian/usr/games/$PACKAGE_NAME
chmod +x $TEMP_DIR/debian/usr/games/$PACKAGE_NAME
 
echo "$PACKAGE_NAME ($PACKAGE_VERSION) trusty; urgency=low" > $TEMP_DIR/runelite/changelog
echo "  * Rebuild" >> $TEMP_DIR/runelite/changelog
gzip -9c $TEMP_DIR/runelite/changelog > $TEMP_DIR/debian/usr/share/doc/$PACKAGE_NAME/changelog.gz
 
mkdir $TEMP_DIR/runelite/icons
icotool -x src/main/resources/runelite.ico -o $TEMP_DIR/runelite/icons
cp $TEMP_DIR/runelite/icons/runelite_5_128x128x32.png $TEMP_DIR/debian/usr/share/$PACKAGE_NAME/runelite.png
chmod 0664 $TEMP_DIR/debian/usr/share/$PACKAGE_NAME/*png
 
PACKAGE_SIZE=`du -bs $TEMP_DIR/debian | cut -f 1`
PACKAGE_SIZE=$((PACKAGE_SIZE/1024))
echo "Installed-Size: $PACKAGE_SIZE" >> $TEMP_DIR/debian/DEBIAN/control
 
chown -R root $TEMP_DIR/debian/
chgrp -R root $TEMP_DIR/debian/
 
cd $TEMP_DIR/
dpkg --build debian
mv debian.deb $SOURCE_DIR/target/$PACKAGE_NAME-$PACKAGE_VERSION.deb
rm -r $TEMP_DIR/debian
rm -r $TEMP_DIR/runelite

