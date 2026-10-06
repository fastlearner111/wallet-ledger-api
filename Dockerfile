# Build stage: compile and package the jar with the Maven wrapper.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Windows checkouts can leave CRLF line endings, which break the script on Linux.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src src
RUN ./mvnw -B -q -DskipTests package

# Run stage: a JRE and the jar, nothing else.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --no-create-home wallet
COPY --from=build /workspace/target/*.jar app.jar
USER wallet
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
