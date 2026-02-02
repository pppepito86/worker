#!/bin/bash

set -e -x

echo off > /sys/devices/system/cpu/smt/control
echo 0 > /proc/sys/kernel/randomize_va_space
echo never > /sys/kernel/mm/transparent_hugepage/enabled
echo never > /sys/kernel/mm/transparent_hugepage/defrag
echo 0 > /sys/kernel/mm/transparent_hugepage/khugepaged/defrag
echo core >/proc/sys/kernel/core_pattern

if [[ $# -gt 0 ]]
then
	git -C /app/sandbox checkout noi
	git -C /app/sandbox pull
	mvn install -f /app/sandbox/pom.xml

	git -C /app/grader checkout noi
	git -C /app/grader pull
	mvn install -f /app/grader/pom.xml

	git -C /app/worker checkout noi
	git -C /app/worker pull
	rm -f /app/worker/target/worker-0.0.1-SNAPSHOT.jar
	mvn install -f /app/worker/pom.xml
else
	git -C /vagrant/sandbox checkout noi
	git -C /vagrant/sandbox pull
	mvn install -f /vagrant/sandbox/pom.xml

	git -C /vagrant/grader checkout noi
	git -C /vagrant/grader pull
	mvn install -f /vagrant/grader/pom.xml

	git -C /vagrant/worker pull
	mvn spring-boot:run -f /vagrant/worker/pom.xml >>/vagrant/worker/stdout 2>> /vagrant/worker/stderr &
fi
