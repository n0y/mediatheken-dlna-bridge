FROM openjdk:16-jdk-alpine
ADD https://github.com/just-containers/s6-overlay/releases/download/v2.1.0.2/s6-overlay-amd64-installer /tmp/
RUN chmod +x /tmp/s6-overlay-amd64-installer && /tmp/s6-overlay-amd64-installer /
ADD target/libraries/* /app/libraries/
ADD target/*.jar /app/
WORKDIR /app
ENTRYPOINT ["/init"]
CMD ["java", "-XX:MaxRAMPercentage=80", "-jar", "mediatheken-dlna-bridge.jar"]
