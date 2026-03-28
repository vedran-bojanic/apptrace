FROM eclipse-temurin:25
ARG JAR_FILE=apptrace-server/target/apptrace-server-*.jar
COPY ${JAR_FILE} /opt/apptrace-server.jar
ENTRYPOINT ["java", "-jar", "/opt/apptrace-server.jar"]
EXPOSE 8080
