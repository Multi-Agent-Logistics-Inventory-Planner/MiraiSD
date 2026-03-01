FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21.0.5_11-jdk AS runner

WORKDIR /app

# Create non-root user
RUN groupadd --system --gid 1001 appgroup && \
    useradd --system --uid 1001 --gid appgroup appuser

COPY --from=builder --chown=appuser:appgroup ./app/target/inventory-service-0.0.1-SNAPSHOT.jar ./app.jar

# Switch to non-root user
USER appuser

EXPOSE 4000

# Use shell form to expand JAVA_OPTS environment variable
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]