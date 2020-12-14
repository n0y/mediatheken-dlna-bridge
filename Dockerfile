FROM openjdk:16-jdk-slim-buster
COPY --from=arpaulnet/s6-overlay-stage:2.0 / /

RUN groupadd --gid 1000 medlna && useradd --gid 1000 --no-create-home --uid 1000 --shell /bin/false medlna
RUN echo '/app/data true medlna,1000:1000 0664 0775' > /etc/fix-attrs.d/01-mediathek-dlna-bridge-datadir

COPY target/libraries/* /app/libraries/
COPY target/*.jar /app/
RUN chmod -R g-w,o-w /app

VOLUME /app/data

WORKDIR /app
ENTRYPOINT ["/init"]
CMD ["s6-setuidgid", "medlna", "java", "-XX:MaxRAMPercentage=80", "-jar", "mediatheken-dlna-bridge.jar"]
