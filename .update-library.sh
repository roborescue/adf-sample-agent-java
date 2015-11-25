#!/bin/sh

cd `dirname $0`
cd ./library/rescue/adf
curl -O 'https://github.com/RCRS-ADF/core/raw/master/build/libs/adf-core.jar'
cd sources
curl -O 'https://github.com/RCRS-ADF/core/raw/master/build/libs/adf-core-sources.jar'
