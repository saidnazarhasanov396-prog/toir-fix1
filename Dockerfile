FROM eclipse-temurin:21-jre
WORKDIR /app
COPY /app/target/*.jar app.jar
RUN mkdir -p /app/uploads
ENV FILE_STORAGE_PATH=/app/uploads
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
