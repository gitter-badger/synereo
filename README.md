# synereo

[![Build Status](https://travis-ci.org/synereo/synereo.svg?branch=staging)](https://travis-ci.org/synereo/synereo)

Home of:

* [SpecialK](specialk)
* The Synereo [Agent Service](agent-service)
* [GLoSEval](gloseval)

## Requirements

To work with the projects in this repository you will need:
* [MongoDB](https://www.mongodb.com/), version 2.6.12 (also tested with version 2.4.14)
  * available at https://www.mongodb.com/download-center (go to "Previous Releases")
* [Erlang](https://www.erlang.org/), version 15B03 (also tested with version R14B04) (required to run RabbitMQ)
  * available at https://www.erlang-solutions.com/resources/download.html
* [RabbitMQ](http://www.rabbitmq.com/), version 3.0.2 (also tested with version 2.7.1)
  * available at http://www.rabbitmq.com/download.html (go to "Older Versions")
* Java Development Kit (JDK), version 7
  * available at http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html
  * alternatively, the [OpenJDK](http://openjdk.java.net/) can be installed using most common package managers.
* [sbt](http://www.scala-sbt.org/)
  * available at http://www.scala-sbt.org/download.html

Additionally, to run the Agent Service test suites you will need:
* [Memcached](https://memcached.org/), latest stable version
  * available at https://memcached.org/downloads

### Why can't I use newer versions?

See note [here](specialk/README.md#why-cant-i-use-newer-versions).

## Usage

After installing the these dependencies, you can clone this repo and run tasks using sbt:
```
$ git clone https://github.com/synereo/synereo.git
  ...
$ cd synereo
$ sbt "gloseval/run gencert --self-signed"
  ...

# Run a GLoSEval server
$ sbt gloseval/run
  ...

# Run the SpecialK test suites
$ sbt specialk/test
  ...

# Run the test suites for all projects
$ sbt test
  ...
```

**NOTE**: In order to run most tasks, MongoDB and RabbitMQ must also be running.

For the Agent Service test suites, Memcached must also be running.


## Issues

We welcome reports of any issues on the [issue tracker](https://github.com/synereo/synereo/issues).

We are also using JIRA to track issues for this project and the rest of the Synereo Platform:
https://synereo.atlassian.net/projects/SOC/issues
