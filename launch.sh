#!/bin/sh

LOADER="adf.sample.SampleLoader"

cd `dirname $0`

PWD=`pwd`
CP=`find $PWD/library/ -name '*.jar' ! -name '*-sources.jar' | awk -F '\n' -v ORS=':' '{print}'`

if [ ! -z "$1" ]; then
  java -classpath "${CP}./build" adf.Main ${LOADER} $*
else
  echo "Options:"
  echo "-t [FB],[FS],[PF],[PO],[AT],[AC] number of agents"
  echo "-fb [FB]                         number of FireBrigade"
  echo "-fs [FS]                         number of FireStation"
  echo "-pf [PF]                         number of PoliceForce"
  echo "-po [PO]                         number of PoliceOffice"
  echo "-at [AT]                         number of AmbulanceTeam"
  echo "-ac [AC]                         number of AmbulanceCentre"
  echo "-all                             [alias] -t -1,-1,-1,-1,-1,-1"
  echo "-s [HOST]:[PORT]                 RCRS server host and port"
  echo "-h [HOST]                        RCRS server host (port:7000)"
  echo "-local                           [alias] -h localhost"
  echo "-pre [0|1]                       Precompute flag"
  echo "-d [0|1]                         Debug flag"
  echo "-mc [FILE]                       ModuleConfig file name"
fi
