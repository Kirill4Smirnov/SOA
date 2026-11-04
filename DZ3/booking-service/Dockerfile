FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY flight-contract/pom.xml flight-contract/pom.xml
COPY flight-service/pom.xml flight-service/pom.xml
COPY booking-service/pom.xml booking-service/pom.xml
COPY flight-contract/src flight-contract/src
COPY booking-service/src booking-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -pl booking-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /workspace/booking-service/target/booking-service-1.0.0.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
