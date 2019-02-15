#!/bin/bash

echo Setting world execute permissions on RuneLite
cd $1
chmod g+x,o+x Contents/MacOS/RuneLite
