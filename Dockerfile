FROM eclipse-temurin:21-jdk

ENV TZ=Asia/Tashkent
WORKDIR /app
COPY  target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
