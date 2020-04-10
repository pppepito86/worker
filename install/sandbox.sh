#!/bin/bash

COMMAND=$1
TIMEOUT=$2
MEMORY=$3

#cd "$(dirname "$0")"
cd /shared

INPUT=${4:-input}
OUTPUT=${5:-output}
ERROR=${6:-error}

if [ ! -f $INPUT ]; then
  INPUT=/dev/null
fi

OOM_START_COUNT=$(dmesg | egrep -i 'killed process' | wc -l)
STARTTIME=`date +%s.%N`
if [[ "$COMMAND" != java* ]] && [[ "$COMMAND" != jar* ]];
then
  ulimit -s $MEMORY
  ulimit -m $MEMORY
  ulimit -v $MEMORY
fi
timeout --signal=KILL $TIMEOUT time -p -f "%U" -o time $COMMAND < $INPUT > $OUTPUT 2> $ERROR
echo $? > exitcode
ENDTIME=`date +%s.%N`

OOM_END_COUNT=$(dmesg | egrep -i 'killed process' | wc -l)
OOM_DIFF=$((OOM_END_COUNT-OOM_START_COUNT))
if [ "$OOM_DIFF" -ne "0" ]; then
  echo $OOM_DIFF > oom
fi

TIMEDIFF=`echo "$ENDTIME - $STARTTIME" | bc | awk -F"." '{print $1"."substr($2,1,3)}'`
if [ ! -s time ]; then
  echo $TIMEDIFF > time
fi

# if "exitcode" exists - the program has finished
# or "timeout" exists - the program has timed out
# or the program was killed, most probably because of memory limit
# if "time" exists - the time the program took if more than TIMEOUT then equal to TIMEOUT
# or if doesn't exist - the time is equal to TIMEOUT
