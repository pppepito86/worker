#!/bin/bash

set -e -x

apt-get update

apt-get install -y curl git gcc make python-dev-is-python3 vim-nox jq cgroup-lite silversearcher-ag

#latex and fonts
apt-get install -y fontforge
apt-get install -y cabextract
wget https://gist.githubusercontent.com/maxwelleite/10774746/raw/ttf-vista-fonts-installer.sh -q -O - | sudo bash
apt-get install -y fonts-noto-color-emoji
apt-get install -y ttf-mscorefonts-installer
apt-get install -y latexmk
apt-get install -y texlive-luatex texlive-xetex
apt-get install -y texlive-science
apt-get install -y texlive-latex-recommended
apt-get install -y texlive-lang-cyrillic
#generate tfm files for 10pt, 11pt and 12pt
mktextfm larm1000
mktextfm larm1095
mktextfm larm1200
fc-cache -fv

git clone https://github.com/ioi/isolate.git /vagrant/worker/isolate
echo 0 > /proc/sys/kernel/randomize_va_space
echo never > /sys/kernel/mm/transparent_hugepage/enabled
echo never > /sys/kernel/mm/transparent_hugepage/defrag
echo 0 > /sys/kernel/mm/transparent_hugepage/khugepaged/defrag

apt-get install -y asciidoc
apt-get install -y libcap-dev
apt-get install -y pkg-config libsystemd-dev
make -C /vagrant/worker/isolate/ install

#mv /vagrant/worker/isolate/isolate /usr/bin/.
cp /vagrant/worker/isolate/default.cf /usr/local/etc/isolate
cp /vagrant/worker/isolate/systemd/isolate.service /etc/systemd/system/isolate.service
systemctl enable isolate
systemctl start isolate

#c++ and c
apt install -y gcc-14 g++-14
update-alternatives --install /usr/bin/gcc gcc /usr/bin/gcc-14 80 --slave /usr/bin/g++ g++ /usr/bin/g++-14 --slave /usr/bin/gcov gcov /usr/bin/gcov-14

#python
apt-get install -y pypy3

#g++ -DEVAL -std=c++20 -O2 -pipe -static -s -o solution solution.cpp
#isolate --run -M meta1 -m 266000 -t 1 -w 3 -x 1.5 -i input -o output -- ./solution

isolate --cleanup -b 0
isolate --init -b 0
cp solution /var/local/lib/isolate/0/box/.
cp input /var/local/lib/isolate/0/box/.

#!/bin/bash

for i in {1..20}
do
    ./isolate --run -M meta1 -m 266000 -t 1 -w 3 -x 1.5 -i substrings.08.in -o substrings.08.out -- ./solution

    cat /vagrant/worker/./submissions/134_3_42/test/meta1|grep OK
done







#java
apt-get purge openjdk*
apt-get install openjdk-8-jdk -y

#maven
sudo apt-get install -y maven

#download projects
git clone https://github.com/pppepito86/sandbox.git /vagrant/sandbox
git clone https://github.com/pppepito86/grader.git /vagrant/grader
git clone https://github.com/pppepito86/worker.git /vagrant/worker

#copy config file
cp /vagrant/worker/config/application.properties.sample /vagrant/worker/src/main/resources/application.properties

git -C /vagrant/sandbox checkout noi
mvn install -f /vagrant/sandbox/pom.xml
mvn install -f /vagrant/grader/pom.xml
mvn install -f /vagrant/worker/pom.xml

#create start service
cp /vagrant/worker/install/worker /etc/init.d/worker
chmod 700 /etc/init.d/worker
update-rc.d worker defaults

service worker start
