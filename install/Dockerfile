FROM ubuntu:18.04

RUN ["/bin/bash", "-c", "apt-get update"]
RUN ["/bin/bash", "-c", "apt-get install -y software-properties-common"]

RUN ["/bin/bash", "-c", "add-apt-repository ppa:webupd8team/java"]
RUN ["/bin/bash", "-c", "add-apt-repository ppa:ubuntu-toolchain-r/test"]

RUN apt-get update

RUN apt-get install -y software-properties-common
RUN apt-get install -y bc
RUN apt-get install -y time

RUN apt install -y openjdk-8-jdk
#RUN apt-get install software-properties-common
#RUN echo "oracle-java8-installer shared/accepted-oracle-license-v1-1 select true" | debconf-set-selections
#RUN echo "oracle-java8-installer shared/accepted-oracle-license-v1-1 seen true" | debconf-set-selections
#RUN apt-get install -y oracle-java8-installer

RUN apt-get install -y gcc-7
RUN apt-get install -y g++-7
RUN update-alternatives --install /usr/bin/gcc gcc /usr/bin/gcc-7 60 --slave /usr/bin/g++ g++ /usr/bin/g++-7

#install mono - https://www.mono-project.com/download/stable/#download-lin
RUN apt-get install gnupg ca-certificates
RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 3FA7E0328081BFF6A14DA29AA6A19B38D3D831EF
#RUN curl -sSL 'http://keyserver.ubuntu.com/pks/lookup?op=get&search=0x3FA7E0328081BFF6A14DA29AA6A19B38D3D831EF' | apt-key add -
RUN echo "deb https://download.mono-project.com/repo/ubuntu stable-bionic main" | tee /etc/apt/sources.list.d/mono-official-stable.list
RUN apt-get update
RUN apt-get install -y mono-devel


RUN apt-get install -y python3-pip python3-dev \
  && cd /usr/local/bin \
  && ln -s /usr/bin/python3 python \
  && pip3 install --upgrade pip

COPY sandbox.sh /scripts/sandbox.sh
RUN chmod +x /scripts/sandbox.sh
