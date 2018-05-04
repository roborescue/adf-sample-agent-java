#!/bin/sh

cd `dirname $0`

rm -rf build
mkdir build

PWD=`pwd`
CP=`find $PWD/library/ -name '*.jar' ! -name '*-sources.jar' | awk -F '\n' -v ORS=':' '{print}'`

cd src
javac -encoding UTF-8 -classpath "${CP}." -d ../build/ `find ./ -name '*.java'` && echo Done. || echo Failed.
