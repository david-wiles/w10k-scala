FROM alpine

RUN apk update && apk add --no-cache openjdk11

COPY target/scala-2.13/server.jar server.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "server.jar"]
