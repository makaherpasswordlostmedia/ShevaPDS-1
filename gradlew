#!/bin/sh

##############################################################################
# Gradle start up script for UN*X
##############################################################################

app_path=$0
while [ -h "$app_path" ] ; do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in
      /*)   app_path=$link ;;
      *)    app_path=${app_path%/*}/$link ;;
    esac
done
APP_HOME=$( cd "${app_path%/*}" && pwd -P ) || exit

APP_NAME="Gradle"
APP_BASE_NAME=${0##*/}

# JVM options — NO quotes around individual flags
DEFAULT_JVM_OPTS="-Xmx512m -Xms64m -Dfile.encoding=UTF-8"

# Java command
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

exec "$JAVACMD" \
    $DEFAULT_JVM_OPTS \
    $JAVA_OPTS \
    $GRADLE_OPTS \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
