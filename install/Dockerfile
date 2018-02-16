FROM ubuntu:16.04

RUN ["/bin/bash", "-c", "apt-get update"]
RUN ["/bin/bash", "-c", "apt-get install -y software-properties-common"]

RUN ["/bin/bash", "-c", "add-apt-repository ppa:webupd8team/java"]
RUN ["/bin/bash", "-c", "add-apt-repository ppa:ubuntu-toolchain-r/test"]
RUN apt-get update

RUN apt-get install -y software-properties-common
RUN apt-get install -y bc

RUN echo "oracle-java8-installer shared/accepted-oracle-license-v1-1 select true" | debconf-set-selections
RUN echo "oracle-java8-installer shared/accepted-oracle-license-v1-1 seen true" | debconf-set-selections
RUN apt-get install -y oracle-java8-installer

RUN apt-get install -y gcc-7
RUN apt-get install -y g++-7
RUN update-alternatives --install /usr/bin/gcc gcc /usr/bin/gcc-7 60 --slave /usr/bin/g++ g++ /usr/bin/g++-7

COPY sandbox.sh /scripts/sandbox.sh
RUN chmod +x /scripts/sandbox.sh
