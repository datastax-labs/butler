## Compilation

The butler project uses Gradle and requires Java 11+, [Node.js](https://nodejs.org/en/download/package-manager/).  
Building the whole
project can be done with 

    ./gradlew build

The project is configured to use the following tools:
1. [Spotless](https://github.com/diffplug/spotless) (configured with the
   [Google code style](https://google.github.io/styleguide/javaguide.html)) is
   used to enforce a consistent code style. The tl;dr is that is that if
   building fails because the code does not conform, one should run `./gradlew
   spotlessApply`.
2. [Checkstyle](https://checkstyle.sourceforge.io/) is also configured to
   enforce a few additional rules (like requiring Javadoc on public methods).
3. [Error prone](https://errorprone.info/) is run on compilation to catch a
   few common mistakes.

## Linting and formatting

    pushd butler-webui
    npm run lint
    popd
    ./gradlew spotlessApply

## Running tests

Running all tests includes unit tests and integration tests:

    ./gradlew check

Integration tests require `docker-compose` to be available.
Check if all is properly set up:

    ./gradlew composeUp && ./gradlew composeDown





## IntelliJ IDEA

Check the option `Enable annotation processing` 
under `File | Settings | Build, Execution, Deployment | Compiler | Annotation Processors`  
