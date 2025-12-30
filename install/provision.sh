#!/bin/bash

set -e -x

sudo apt-get update

sudo apt-get install -y curl git gcc make python-dev-is-python3 vim-nox jq cgroup-lite silversearcher-ag

#latex and fonts
sudo apt-get install -y fontforge
sudo apt-get install -y cabextract
sudo apt-get install -y fonts-noto-color-emoji
sudo apt-get install -y ttf-mscorefonts-installer
sudo apt-get install -y latexmk
sudo apt-get install -y texlive-luatex texlive-xetex
sudo apt-get install -y texlive-science
sudo apt-get install -y texlive-latex-recommended
sudo apt-get install -y texlive-lang-cyrillic
#generate tfm files for 10pt, 11pt and 12pt
mktextfm larm1000
mktextfm larm1095
mktextfm larm1200
sudo fc-cache -fv

git clone https://github.com/ioi/isolate.git /app/isolate
sudo -i
echo 0 > /proc/sys/kernel/randomize_va_space
echo never > /sys/kernel/mm/transparent_hugepage/enabled
echo never > /sys/kernel/mm/transparent_hugepage/defrag
echo 0 > /sys/kernel/mm/transparent_hugepage/khugepaged/defrag
exit

sudo apt-get install -y asciidoc
sudo apt-get install -y libcap-dev
sudo apt-get install -y pkg-config libsystemd-dev
sudo make -C /app/worker/isolate/ install

#mv /app/isolate/isolate /usr/bin/.
sudo cp /app/isolate/default.cf /usr/local/etc/isolate
sudo cp /app/isolate/systemd/isolate.service /etc/systemd/system/isolate.service
sudo systemctl enable isolate
sudo systemctl start isolate

#c++ and c
sudo apt install -y gcc-11 g++-11
sudo update-alternatives --install /usr/bin/gcc gcc /usr/bin/gcc-11 80 --slave /usr/bin/g++ g++ /usr/bin/g++-11 --slave /usr/bin/gcov gcov /usr/bin/gcov-11

#python
sudo apt-get install -y pypy3

#g++ -DEVAL -std=c++20 -O2 -pipe -static -s -o solution solution.cpp
#isolate --run -M meta1 -m 266000 -t 1 -w 3 -x 1.5 -i input -o output -- ./solution


#isolate --cleanup -b 0
#isolate --init -b 0
#cp solution /var/local/lib/isolate/0/box/.
#cp input /var/local/lib/isolate/0/box/.

#for i in {1..20}
#do
#    ./isolate --run -M meta1 -m 266000 -t 1 -w 3 -x 1.5 -i substrings.08.in -o substrings.08.out -- ./solution
#
#    cat /app/worker/./submissions/134_3_42/test/meta1|grep OK
#done


#java
sudo apt-get purge openjdk*
sudo apt-get install openjdk-8-jdk -y

#maven
sudo apt-get install -y maven

#download projects
git clone https://github.com/pppepito86/sandbox.git /app/sandbox
git -C /app/sandbox checkout noi
git clone https://github.com/pppepito86/grader.git /app/grader
git -C /app/grader checkout noi
git clone https://github.com/pppepito86/worker.git /app/worker
git -C /app/worker checkout noi

#copy config file
cp /app/worker/config/application.properties.sample /app/worker/src/main/resources/application.properties

mvn install -f /app/sandbox/pom.xml
mvn install -f /app/grader/pom.xml
mvn install -f /app/worker/pom.xml

sudo cp /app/worker/install/worker.service /etc/systemd/system/worker.service
sudo systemctl enable worker
sudo systemctl start worker

#create start service
#cp /app/worker/install/worker /etc/init.d/worker
#chmod 700 /etc/init.d/worker
#update-rc.d worker defaults
#service worker start
