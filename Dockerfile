FROM amazoncorretto:21.0.2-alpine

LABEL org.label-schema.schema-version="1.0"
LABEL org.label-schema.name="Mediatheken-DLNA-Bridge"
LABEL org.label-schema.description="Retrieves content of various (german) Mediatheken & serves it's content to your local network using DLNA."
LABEL org.label-schema.usage="https://github.com/n0y/mediatheken-dlna-bridge/blob/master/README.md"
LABEL org.label-schema.url="https://github.com/n0y/mediatheken-dlna-bridge"
LABEL org.label-schema.vcs-url="https://github.com/n0y/mediatheken-dlna-bridge"
LABEL org.label-schema.docker.cmd="docker run corelogicsde/mediatheken-dlna-bridge:latest"

USER root
COPY --from=arpaulnet/s6-overlay-stage:2.2 / /

RUN addgroup --g 1000 medlna && adduser -G medlna -H -D -u 1000 -s /bin/false medlna
RUN echo '/app/data true medlna,1000:1000 0664 0775' >> /etc/fix-attrs.d/01-mediathek-dlna-bridge-datadir
RUN echo '/app/cache true medlna,1000:1000 0664 0775' >> /etc/fix-attrs.d/01-mediathek-dlna-bridge-datadir

COPY target/libraries/* /app/libraries/
COPY target/*.jar /app/
RUN mkdir /app/data /app/cache && chmod -R g-w,o-w /app

VOLUME /app/data
VOLUME /app/cache

WORKDIR /app
ENTRYPOINT ["/init"]
CMD ["s6-setuidgid", "medlna", \
    "java", \
    "--enable-preview", \
    "-XX:MaxRAMPercentage=70", \
    "-XX:+UseShenandoahGC", \
    "-XX:+UnlockExperimentalVMOptions", \
    "-XX:ShenandoahUncommitDelay=5000", \
    "-XX:ShenandoahGuaranteedGCInterval=20000", \
    "-jar", \
    "mediatheken-dlna-bridge.jar"]
