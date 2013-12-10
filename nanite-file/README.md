# libmagic-jna-wrapper

## About libmagic-jna-wrapper
A Java / JNA wrapper for libmagic

### What does libmagic-jna-wrapper do?

Given an instance of the libmagic shared library it provides a Java class that facilitates magic identification of
streams or files.

### What are the benefits for end user?

Convenient and efficient invocation of libmagic from within JVM.

### Who is intended audience?

The project is aimed at developers who wish to perform efficient magic identification from within Jvm.

## Features and roadmap

### Version 0.0.1

Allows the caller to:

* Compile magic files to a .mgc database
* Load an existing .mgc database
* Perform magic identification of files, Input Streams, and nio.Buffers

### Roadmap

* Tidy API in line with user requests, just [raise an Issue](https://github.com/openplanets/libmagic-jna-wrapper/issues)

## How to install and use

### Requirements

TODO: Requirements.

### Download

Currently no direct download

### Install instructions

TODO: Install.

### Use

TODO:

### Troubleshooting

TODO: 
## More information

### Publications

* Publication 1
* Publication 2

### Licence

https://github.com/openplanets/libmagic-jna-wrapper is released under [Apache version 2.0 license](LICENSE.txt).

### Acknowledgements

Part of this work was supported by the European Union in the 7th Framework Program, IST, through the SCAPE project, Contract 270137.

### Support

This tool is supported by the [Open Planets Foundation](http://www.openplanetsfoundation.org). Commercial support is provided by company X.

## Develop

[![Build Status](https://travis-ci.org/openplanets/libmagic-jna-wrapper.png)](https://travis-ci.org/openplanets/libmagic-jna-wrapper)

### Requirements

To build you require:

* Git client
* Apache Maven
* Java Developers Kit (e.g. OpenJDK 6)

For using the recommended IDE you require:

* Eclipse of Java

### Setup IDE

TODO:

### Build

To compile go to the sources folder and execute the command:

```bash
$ mvn clean install
```

### Deploy

TODO: 

### Contribute

1. [Fork the GitHub project](https://help.github.com/articles/fork-a-repo)
2. Change the code and push into the forked project
3. [Submit a pull request](https://help.github.com/articles/using-pull-requests)

To increase the changes of you code being accepted and merged into the official source here's a checklist of things to go over before submitting a contribution. For example:

* Has unit tests (that covers at least 80% of the code)
* Has documentation (at least 80% of public API)
* Agrees to contributor license agreement, certifying that any contributed code is original work and that the copyright is turned over to the project
