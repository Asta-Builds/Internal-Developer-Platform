# ---- Build stage: quality gate + package --------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml checkstyle.xml ./
COPY src ./src
# Lint gate (Checkstyle) runs inside verify, which also packages the fat jar.
RUN mvn -B -q verify

# ---- Runtime stage --------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]