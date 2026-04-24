FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
