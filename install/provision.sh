#!/bin/bash

set -e -x

apt-get update

apt-get install -y curl git gcc make python-dev vim-nox jq cgroup-lite silversearcher-ag

#docker
wget -qO- https://get.docker.com/ | sh
docker pull pppepito86/judge

#java
apt-get purge openjdk*
apt-get install openjdk-8-jdk -y

#maven
sudo apt-get install -y maven

#create start service
cp /vagrant/worker/install/worker /etc/init.d/worker
chmod 700 /etc/init.d/worker
update-rc.d worker defaults

#judge project
git clone https://github.com/pppepito86/sandbox.git /vagrant/sandbox
git clone https://github.com/pppepito86/grader.git /vagrant/grader
git clone https://github.com/pppepito86/worker.git /vagrant/worker
mvn install -f /vagrant/sandbox/pom.xml
mvn install -f /vagrant/grader/pom.xml
mvn install -f /vagrant/worker/pom.xml

service worker start
