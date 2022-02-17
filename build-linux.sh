#!/bin/sh

set -e

echo "Building 64 bit executable"
./fips set config linux-make-release
./fips clean && ./fips build

echo "Copying 64 bit executable ..."
cp ../fips-deploy/packr/linux-make-release/packr ../../resources/packr-linux-x64
