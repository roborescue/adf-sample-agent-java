#!/bin/sh

WGET='wget --no-check-certificate -O'
if ! [ -x `which wget||echo /dev/null` ]; then
	if [ -x `which curl||echo /dev/null` ]; then
		WGET='curl -o'
	else
		if ! [ -x `which apt-get||echo /dev/null` ]; then
			echo "[!] This script repuire apt-get or wget."
			exit
		fi
		sudo apt-get install -y wget
		sh -c "sh $0"
		exit
	fi
fi

cd `dirname $0`
cd ./library/rescue/adf

$WGET 'adf-core.jar' "https://raw.githubusercontent.com/RCRS-ADF/core/jar/build/libs/adf-core.jar?$$"
# $WGET 'adf-modules.jar' "https://raw.githubusercontent.com/RCRS-ADF/modules/master/build/libs/adf-modules.jar?$$"
mkdir -p sources
cd sources
$WGET 'adf-core-sources.jar' "https://raw.githubusercontent.com/RCRS-ADF/core/jar/build/libs/adf-core-sources.jar?$$"
# $WGET 'adf-modules-sources.jar' "https://raw.githubusercontent.com/RCRS-ADF/modules/master/build/libs/adf-modules-sources.jar?$$"
