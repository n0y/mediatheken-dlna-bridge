FROM openjdk:16-jdk-slim-buster
COPY --from=arpaulnet/s6-overlay-stage:2.0 / /
ADD target/libraries/* /app/libraries/
ADD target/*.jar /app/
WORKDIR /app
ENTRYPOINT ["/init"]
CMD ["java", "-XX:MaxRAMPercentage=80", "-jar", "mediatheken-dlna-bridge.jar"]
