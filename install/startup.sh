#!/bin/bash

set -e -x

git -C /vagrant/sandbox checkout noi
git -C /vagrant/sandbox pull
mvn install -f /vagrant/sandbox/pom.xml

git -C /vagrant/grader checkout noi
git -C /vagrant/grader pull
mvn install -f /vagrant/grader/pom.xml

git -C /vagrant/worker pull
if [[ $# -gt 0 ]]
then
	rm /vagrant/worker/target/worker-0.0.1-SNAPSHOT.jar
	mvn install -f /vagrant/worker/pom.xml
else
	mvn spring-boot:run -f /vagrant/worker/pom.xml >>/vagrant/worker/stdout 2>> /vagrant/worker/stderr &
fi
