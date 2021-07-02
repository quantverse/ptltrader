#!/usr/bin/env bash
./gradlew shadowJar -PforceArch=win64
./gradlew shadowJar -PforceArch=macosx
./gradlew shadowJar -PforceArch=linux64