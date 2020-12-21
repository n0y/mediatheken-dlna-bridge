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
    #  - source: ./data/mediathek-cache
    #    target: /app/cache
    #    type: bind
    # You may overwrite any property here, i.e. the Database Location
    environment:
       DISPLAY_NAME: "My Mediathek DLNA Bridge"
    #  DATABASE_LOCATION: /app/data/clipdb
    #  ENABLE_PREFETCHING: 'true'
    #  CACHE_SIZE_GB: '30'
```

## Configuration

Common configuration

* _DATABASE_LOCATION_ points to the directory and name of the H2 database. Defaults to `./data/clipdb`
* _UPDATEINTERVAL_FULL_HOURS_ number of hours between full db updates. Defaults to `24`.
* _DISPLAY_NAME_ under this name, the Mediatheken-DLNA-Bridge will be visible in your network. Defaults to `Mediatheken`.

Configuration for prefetching

* _ENABLE_PREFETCHING_ (boolean) decides if prefetching is turned on. Defaults to `false`.
* _PUBLIC_BASE_URL_ needs to be set when server prefetching is enabled. It's the base HTTP path, where this service is published.
  Could be `http://<your-hostname>:8080/` or any other location, if you put this behind a reverse proxy. *there is no default*, 
  you have to configure it when you're using prefetching.
* _PUBLIC_HTTP_PORT_ the port this server should listen for connections, if prefetching is turned on. Defaults to `8080`.
* _CACHE_DIRECTORY_ is path to a directory where prefetched files are placed. May be absolute or relative to the run directory. It defaults to `./cache`.
* _CACHE_SIZE_GB_ the amount of disk space (in Gigabyte) dedicated to prefetched files. Defaults to `10`. Values below 10GB are rejected.
* _CACHE_DOWNLODERS_PER_VIDEO_ number of parallel connections, per video, that are used to fetch the file. Defaults to `2`.   
* _CACHE_MAX_PARALLEL_DOWNLOADS_ number of videos that can be prefetched in parallel. Defaults to `4`. Note that each downloads requires at least 1.3MBytes/s of bandwidth!

## Configure prefetching

Sometimes the Mediatheken's CDN is slow. Most of the time, that only means that some servers, or clusters are slow, over some time.

Using prefetching, the Mediatheken-Dlna-Bridge will try to fetch in advance your videos, discard servers that are too slow to handle the required bandwidth.

To use prefetching, you need to:

* create, and maybe mount a temporary directory for prefetched files
* configure the base URL, where this server will be reachable, using the `PUBLIC_HTTP_PORT` configuration. For standalone operation, this will be `http://<hostname>:8080/`.
  For docker, the port may differ. You are free to place the Mediathek-Dlna-Bridge behind a reverse proxy. In that case, you may also add an URL prefix here. 
* maybe override the cache directory, using the `CACHE_DIRECTORY` configuration
* decide on how much space you'll assign to prefetched videos. Use the `CACHE_SIZE_GB` configuration. Mediathek-Dlna-Bridge will never use more disk space than that.
* set the 'ENABLE_PREFETCHING' configuration to `true`

[HOME]: https://github.com/n0y/mediatheken-dlna-bridge
[RELEASES]: https://github.com/n0y/mediatheken-dlna-bridge/releases
[LICENSE]: https://github.com/n0y/mediatheken-dlna-bridge/blob/master/LICENSE
[AUTHORS]: https://github.com/n0y/mediatheken-dlna-bridge/blob/master/AUTHORS
[DOCKERHUB]: https://hub.docker.com/repository/docker/corelogicsde/mediatheken-dlna-bridge
