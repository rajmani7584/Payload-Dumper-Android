#!/bin/bash

cargo ndk build -t arm64-v8a -t armeabi-v7a -o ../../app/src/main/jniLibs --release