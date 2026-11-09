# Stage 1: Build the application using Maven
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper and build files
mvnw pom.xml ./
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src

# Package the application (skip tests for faster cloud build)
RUN ./mvnw clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the port your backend runs on
EXPOSE 5000

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]