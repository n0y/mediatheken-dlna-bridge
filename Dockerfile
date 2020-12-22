FROM openjdk:11-slim-buster
COPY --from=arpaulnet/s6-overlay-stage:2.0 / /

# build fix on arm
RUN ln -s /bin/* /usr/sbin/ && ln -s /sbin/* /usr/sbin/ && ln -s /usr/bin/* /usr/sbin/
RUN apt-get update && apt-get install -y libcap2-bin && rm -rf /var/lib/{apt,dpkg,cache,log}/
RUN setcap 'cap_net_bind_service=+ep' /usr/local/openjdk-11/bin/java

RUN groupadd --gid 1000 medlna && useradd --gid 1000 --no-create-home --uid 1000 --shell /bin/false medlna
RUN echo '/app/data true medlna,1000:1000 0664 0775' > /etc/fix-attrs.d/01-mediathek-dlna-bridge-datadir
RUN echo '/app/cache true medlna,1000:1000 0664 0775' > /etc/fix-attrs.d/01-mediathek-dlna-bridge-datadir

COPY target/libraries/* /app/libraries/
COPY target/*.jar /app/
RUN mkdir /app/{data,cache} && chmod -R g-w,o-w /app

VOLUME /app/data
VOLUME /app/cache

WORKDIR /app
ENTRYPOINT ["/init"]
CMD ["s6-setuidgid", "medlna", "java", "-XX:MaxRAMPercentage=80", "-jar", "mediatheken-dlna-bridge.jar"]
