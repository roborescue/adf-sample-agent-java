#!/bin/sh

LOADER="adf.sample.SampleLoader"

cd `dirname $0`

PWD=`pwd`
CP=`find $PWD/library/ -name '*.jar' ! -name '*-sources.jar' | awk -F '\n' -v ORS=':' '{print}'`

if [ ! -z "$1" ]; then
  java -classpath "${CP}./build" adf.Main ${LOADER} $*
else
  echo "Options:"
  echo "-t [FB],[FS],[PF],[PO],[AT],[AC]\tnumber of agents"
  echo "-fb [FB]\t\t\t\tnumber of FireBrigade"
  echo "-fs [FS]\t\t\t\tnumber of FireStation"
  echo "-pf [PF]\t\t\t\tnumber of PoliceForce"
  echo "-po [PO]\t\t\t\tnumber of PoliceOffice"
  echo "-at [AT]\t\t\t\tnumber of AmbulanceTeam"
  echo "-ac [AC]\t\t\t\tnumber of AmbulanceCentre"
  echo "-all\t\t\t\t\t[alias] -t -1,-1,-1,-1,-1,-1"
  echo "-s [HOST]:[PORT]\t\t\tRCRS server host and port"
  echo "-h [HOST]\t\t\t\tRCRS server host (port:7000)"
  echo "-local\t\t\t\t\t[alias] -h localhost"
  echo "-pre [0|1]\t\t\t\tPrecompute flag"
  echo "-d [0|1]\t\t\t\tDebug flag"	       
  echo "-mc [FILE]\t\t\t\tModuleConfig file name"
fi
