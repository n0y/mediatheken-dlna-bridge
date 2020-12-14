# Mediatheken DLNA Bridge

Mediatheken DLNA Bridge is a java standalone program, which consumes contents of various (german) Mediatheken,
and serves it's content to your local network using DLNA.

Find us at our [HOME].

See [LICENSE] for the Mediatheken DLNA Bridge license.

For a list of all authors, see the [AUTHORS] file. 

## Usage

There're two options running the Mediatheken DLNA Bridge: Local installation, or docker.

### Local installation

* Download latest tar.gz from the [RELEASES] page.
* unpack it
* run the provided JAR file with `java -jar mediatheken-dlna-brige.jar`.
* you may give a java parameter `-Xmx700m` or so for a memory limit. The memory will be used for a
  database cache. Giving it more than 1GB doesn't improve things.

You may override any setting in three ways:

* Create a file `config/application.properties`, and put the settings there, or
* Add the settings to your `java` command line with `-DSETTING=VALUE`, or
* Create environment variables `SETTING=VALUE`

A local database will be created in a directory local to your working directory. It's a H2 database, with
username/password of `sa`/`sa` (as usual with h2). Feel free to dig into it.

### Docker installation

Find the released Docker images at [DOCKERHUB]. Simply run them with:

`docker run corelogicsde/mediatheken-dlna-bridge:latest`

You may set a memory limit with adding `--memory=700M` to the docker line. The memory will be used for a
database cache. Giving it more than 1GB doesn't improve things.

You may override any setting in two ways:

* set environment variables with `-e SETTING=VALUE`. These will be picked up by the image.
* create a configuration file, and link it into `/app/config/application.properties`.

The image will then run with _host networking_, which should usually be fine.

### Docker Compose

Use something like this `docker-compose.yaml`:

```
version: "3.3"

services:
  mediathekbridge:
    image: "corelogicsde/mediatheken-dlna-bridge"
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 800M
    # This volume can be used to store the database outside of the container
    #volumes:
    #  - source: ./data/mediathek-data
    #    target: /app/data
    #    type: bind
    # You may overwrite any property here, i.e. the Database Location
    environment:
       DISPLAY_NAME: "My Mediathek DLNA Bridge"
    #  DATABASE_LOCATION: /app/data/clipdb
```

## Configuration

* _DATABASE_LOCATION_ points to the directory and name of the H2 database. Defaults to `./data/clipdb`
* _UPDATEINTERVAL_FULL_HOURS_ number of hours between full db updates. Defaults to `24`.
* _DISPLAY_NAME_ under this name, the Mediatheken-DLNA-Bridge will be visible in your network. Defaults to `Mediatheken`.


[HOME]: https://github.com/n0y/mediatheken-dlna-bridge
[RELEASES]: https://github.com/n0y/mediatheken-dlna-bridge/releases
[LICENSE]: https://github.com/n0y/mediatheken-dlna-bridge/blob/master/LICENSE
[AUTHORS]: https://github.com/n0y/mediatheken-dlna-bridge/blob/master/AUTHORS
[DOCKERHUB]: https://hub.docker.com/repository/docker/corelogicsde/mediatheken-dlna-bridge
