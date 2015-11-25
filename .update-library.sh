#!/bin/sh

cd `dirname $0`
cd ./library/rescue/adf
curl -O 'https://raw.githubusercontent.com/RCRS-ADF/core/master/build/libs/adf-core.jar'
cd sources
curl -O 'https://raw.githubusercontent.com/RCRS-ADF/core/master/build/libs/adf-core-sources.jar'
