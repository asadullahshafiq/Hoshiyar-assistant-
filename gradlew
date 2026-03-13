#!/bin/sh
# Gradle wrapper - GitHub Actions isko use karega

GRADLE_VERSION=8.1

# Wrapper properties
GRADLE_WRAPPER_DIR="$(dirname "$0")/gradle/wrapper"
GRADLE_WRAPPER_JAR="$GRADLE_WRAPPER_DIR/gradle-wrapper.jar"

exec java -jar "$GRADLE_WRAPPER_JAR" "$@"
