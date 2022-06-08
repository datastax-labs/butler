# Butler Server

This module implements the server side (exposing REST API) of the Butler service.

## Prerequisites

### Database

The server uses a MariaDB/MySQL database to store data, and hence the server expect a MySQL/MariaDB server to communicate with. 

:warning: Minimum required MySQL version is 10.4. 

Here is example script to create database for local testing.
Butler app by default uses database `butler_db` and user `test` without password:
This database should have schema created using [schema.sql](src/main/resources/schema.sql).

```
CREATE USER test@'%';
CREATE DATABASE butler_db CHARACTER SET utf8;
GRANT ALL PRIVILEGES ON butler_db.* to test;
USE butler_db;
SOURCE schema.sql
```

:warning: Please do not run this on the production server.


### Jenkins

The server usually is configured to access to some jenkins machine so that it can import builds.

#### Authentication

If this jenkins instance requires authentication/authorization then credentials 
will be read from the [`~/.netrc` file](https://www.gnu.org/software/inetutils/manual/html_node/The-_002enetrc-file.html),
which must thus contains entries looking like:

```
machine jenkins.example.com
  login <jenkins user name>
  password <jenkins api token>
```

### JIRA

Butler provides functionality of connecting test failures to issue tracking system tickets.

At the moment fully supported system is [JIRA](https://www.atlassian.com/software/jira) 
that allows to link and create tickets directly from `butler`.

#### Authentication

Butler server reads credentials from those from your
[`~/.netrc` file](https://www.gnu.org/software/inetutils/manual/html_node/The-_002enetrc-file.html),
which must thus contains entries looking like:

```
machine example.jira.com
  login <jira user name>
  password <jira api token>
```

## Running

The server is a [Spring Boot](https://spring.io/projects/spring-boot) app and as such, 
the easiest way to run it locally is with:

```bash
> ./gradlew bootRun
```
which will make the service accessible at http://localhost:8080/.

By default, the server will expect a local database server and will use a user named `test` with no
password, and a database name specified in the `application.properties` file. 
Butler expects this database t be pre-populated with the [the schema](src/main/resources/schema.sql)).

You can change those through environment variables however:
```
> MYSQL_HOST=xxx MYSQL_USER=user MYSQL_PASS=secret MYSQL_DB=butler ./gradlew bootRun
```

The server protects a number of API behind an admin role, with a [default admin user](src/main/java/com/datastax/butler/server/service/UsersService.java#L17)
created on first startup.

## Web UI

The server provides a REST API, but also serves the web UI implemented by the
[`butler-webui`](../butler-webui) (technically, the build in `butler-webui` packages all the assets
(through [webpack](https://webpack.js.org/)) within a jar, which the server imports). Effectively,
this means that by running `./gradlew bootRun`, both the REST API _and_ the web UI will be
accessible on http://localhost:8080/.

## Running in Docker

To make it easier to build butler and run it with the prerequisite database, you can use the `dockerBuild` target which
builds a docker image named `datastax/butler-server:latest` that runs both MariaDB/MySQL and butler-server.

```
./gradlew dockerBuild
```

To run butler-server in a container, you need to pass it a volume with an `netrc` file containing the data described above (note the `netrc` filename for docker doesn't have a leading '.') and map it to `/config` in the container.

Optionally, if you'd like the database to persist even when your container isn't running, you need to pass another
docker volume that the container mounts at `/var/lib/mysql`.

```
docker run -it --rm -p 8080:8080/tcp -v ~/butler-mount/config:/config -v ~/butler-mount/data:/var/lib/mysql datastax/butler-server:latest
```

Once your container is running, the REST API and web UI will be available at `http://localhost:8080`.

## Integration Tests

Integration tests allow to test `db` or `service` layer
as well as `REST` api calls
using `MariaDB` started in the `docker` container.

### checking if all is good with docker-compose

To check if it can start:
```
./gradlew :butler-server:composeUp
./gradlew :butler-server:composeDown
```

Please note that default port for the `MariaDB` started for integration tests is 3308
to avoid conflicts with any local instance of the db engine.

### starting tests

Tests are included in `./gradlew check` and so they are also included in the CI or sonar coverage.

It is possible to run only integration tests via:
```
./gradlew integrationTest
```
