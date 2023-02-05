FROM amazoncorretto:19-alpine

COPY target/scala-2.13/broadcast.jar broadcast.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "broadcast.jar"]
