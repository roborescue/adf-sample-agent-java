#!/bin/bash

LOADER="adf.impl.DefaultLoader"
PARAMS=$*

cd `dirname $0`

if [ ! -z "$1" ]; then
  ./gradlew launch --args="${LOADER} ${PARAMS}"
else
  echo "Options:"
  echo "-tn                              Team name"
  echo "-t [FB],[FS],[PF],[PO],[AT],[AC] Number of agents"
  echo "-fb [FB]                         Number of FireBrigade"
  echo "-fs [FS]                         Number of FireStation"
  echo "-pf [PF]                         Number of PoliceForce"
  echo "-po [PO]                         Number of PoliceOffice"
  echo "-at [AT]                         Number of AmbulanceTeam"
  echo "-ac [AC]                         Number of AmbulanceCentre"
  echo "-s [HOST]:[PORT]                 RCRS server host and port"
  echo "-h [HOST]                        RCRS server host (port:27931)"
  echo "-pre [0|1]                       Precompute flag"
  echo "-d [0|1]                         Debug flag"
  echo "-dev [0|1]                       Development mode"
  echo "-mc [FILE]                       ModuleConfig file name"
  echo "-md [JSON]                       ModuleConfig JSON"
  echo "-df [FILE]                       DevelopData JSON file"
  echo "-dd [JSON]                       DevelopData JSON"
  echo "-all                             [alias] -t -1,-1,-1,-1,-1,-1"
  echo "-allp                            [alias] -t 1,0,1,0,1,0,"
  echo "-local                           [alias] -h localhost"
  echo "-precompute                      [alias] -pre 1"
  echo "-debug                           [alias] -d 1"
  echo "-develop                         [alias] -dev 1"
fi