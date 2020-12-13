FROM maven:3.6-openjdk-11
ADD src /src/src/
ADD pom.xml /src/
WORKDIR /src
RUN find .
RUN mvn clean package

FROM openjdk:16-jdk-alpine
ADD https://github.com/just-containers/s6-overlay/releases/download/v2.1.0.2/s6-overlay-amd64-installer /tmp/
RUN chmod +x /tmp/s6-overlay-amd64-installer && /tmp/s6-overlay-amd64-installer /
COPY --from=0 target/libraries/* /app/libraries/
COPY --from=0 target/*.jar /app/
WORKDIR /app
ENTRYPOINT ["/init"]
CMD ["java", "-cp", "*:libraries/*", "de.corelogics.mediaview.Main"]
