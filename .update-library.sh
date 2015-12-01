#!/bin/sh

cd `dirname $0`
cd ./library/rescue/adf
curl -o 'adf-core.jar' "https://raw.githubusercontent.com/RCRS-ADF/core/master/build/libs/adf-core.jar?$$"
cd sources
curl -o 'adf-core-sources.jar' "https://raw.githubusercontent.com/RCRS-ADF/core/master/build/libs/adf-core-sources.jar?$$"
