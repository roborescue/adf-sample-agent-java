#!/bin/sh

cd `dirname $0`
cd ./library/rescue/adf
# core
curl -o 'adf-core.jar' "https://raw.githubusercontent.com/RCRS-ADF/core/master/build/libs/adf-core.jar?$$"
# modules
curl -o 'adf-modules.jar' "https://raw.githubusercontent.com/RCRS-ADF/modules/master/build/libs/adf-modules.jar?$$"
cd sources
curl -o 'adf-core-sources.jar' "https://raw.githubusercontent.com/RCRS-ADF/core/master/build/libs/adf-core-sources.jar?$$"
curl -o 'adf-modules-sources.jar' "https://raw.githubusercontent.com/RCRS-ADF/modules/master/build/libs/adf-modules-sources.jar?$$"