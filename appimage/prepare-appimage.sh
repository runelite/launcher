#!/usr/bin/env bash

cd $1
ln -s RuneLite AppRun
mkdir -p usr/share/applications/
mkdir -p usr/share/icons/hicolor/512x512/
cp runelite.png usr/share/icons/hicolor/512x512/runelite.png
cp runelite.desktop usr/share/applications/runelite.desktop
