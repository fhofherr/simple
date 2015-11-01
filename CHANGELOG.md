# Change Log

All notable changes to this project will be documented in this file.
This change log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/). This project adheres
to [Semantic Versioning](http://semver.org/).

## [Unreleased][unreleased]

### Added

* Created the (`simple`)[https://github.com/fhofherr/simple] project

* Implemented execution of shell scripts or other programs which in turn
  may execute tests. An exit code of 0 signals successful test
  execution. An exit code >0 signals a failed text execution. The shell
  script to execute is configured via the `simple.clj` file in the
  project's directory.

* Created `fhofherr.clj-io` helpers. Currently they are part of
  `fhofherr.simple`. I intend to factor them out to a project of their
  own, but I don't know when yet.

* Implemented `:before` and `:after` hooks for test execution. If
  a `:before` hook fails the tests will not be executed. The `:after`
  hooks are executed even if `:test` or `:before` fail.

[unreleased]: https://github.com/fhofherr/simple/compare/0.1.0...HEAD
