#!/bin/sh
./fips set config packr-osx-x64-xcode-release
./fips clean && ./fips build
cp ../fips-deploy/packr/packr-osx-x64-xcode-release/packr ../../resources/packr-mac-x64

./fips set config packr-osx-arm64-xcode-release
./fips clean && ./fips build
cp ../fips-deploy/packr/packr-osx-arm64-xcode-release/packr ../../resources/packr-mac-arm64
