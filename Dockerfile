# Docker container for an information content computation program in relational databases
#
# Copyright 2024, Christoph Köhnen <christoph.koehnen@uni-passau.de>
# Copyright 2024, Stefan Klessinger <stefan.klessinger@uni-passau.de>
# SPDX-License-Identifier: GPL-3.0

FROM maven:3.8.7-openjdk-18-slim as build

WORKDIR /home
COPY src src
COPY pom.xml .
RUN mvn clean install

FROM ubuntu:22.04

LABEL org.opencontainers.image.authors="Christoph Köhnen <christoph.koehnen@uni-passau.de>"

ENV DEBIAN_FRONTEND=noninteractive
ENV LANG="C.UTF-8"
ENV LC_ALL="C.UTF-8"

# Install packages
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-18-jre \
    sudo

# Add user
RUN useradd -m -G sudo -s /bin/bash repro && echo "repro:repro" | chpasswd
RUN usermod -a -G staff repro
USER repro
WORKDIR /home/repro

# Copy jar of entropy computation program
COPY --chown=repro:repro --from=build /home/target/relational_information_content-1.0-SNAPSHOT-jar-with-dependencies.jar relational_information_content.jar
