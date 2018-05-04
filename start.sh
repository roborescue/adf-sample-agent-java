#!/bin/sh

LOADER="adf.sample.SampleLoader"

cd `dirname $0`

PWD=`pwd`
CP=`find $PWD/library/ -name '*.jar' ! -name '*-sources.jar' | awk -F '\n' -v ORS=':' '{print}'`

java -classpath "${CP}./build" adf.Main ${LOADER} -t $1,$2,$3,$4,$5,$6 -h $7 -pre false
