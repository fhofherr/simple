# Simple

A very simple CI server for individual projects.

## Building

Checkout `https://github.com/fhofherr/simple` then run `lein uberjar`.

## Usage

Simple is configured using a `simple.clj` file. For an example see
Simple's own [`simple.clj`](simple.clj) file. You can check the file
into Git along with the rest of your code.

After you created such a file run simple with

```bash
java -jar simple-0.1.0-standalone.jar <directory containing simple.clj>
```

# Releasing

* [ ] `git flow release start <version number>`
* [ ] Bump version number in `project.clj`.
* [ ] Edit [CHANGELOG.md](CHANGELOG.md) and update sections.
* [ ] Execute `lein uberjar` and use it to execute Simple's own tests.
* [ ] `git flow release finish <version number>`
* [ ] `git flow release publish <version number>`

## License

Copyright © 2015 Ferdinand Hofherr

Distributed under the MIT license.
