# Stage 1: build, on the runner's own architecture. A jar is the same on every platform, so
# Maven runs once, natively; only stage 2 is built per platform.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

# pom and wrapper first, so the dependency layer survives a source-only change
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
# go-offline leaves out the annotation processor (MapStruct): compiling a stub class fetches it
# here, in the cached layer, instead of during the package below on every build.
RUN mkdir -p src/main/java && echo 'class Warmup {}' > src/main/java/Warmup.java \
    && ./mvnw -B -q compile && rm -rf src target target-maven

COPY src src
# Tests run in CI; repeating them here would double the feedback loop without adding a signal.
RUN ./mvnw -B package -DskipTests

# Stage 2: runtime
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Non-root: nothing here writes outside /tmp or binds a privileged port.
RUN addgroup -S -g 1001 app && adduser -S -u 1001 -G app app

# target-maven, not target: the local-isolation profile is active whenever CI is unset, which it
# always is inside a Docker build. wely-chat copies from the same place, for the same reason.
COPY --from=build --chown=app:app /app/target-maven/*.jar app.jar

USER app:app

EXPOSE 8088

# MaxRAMPercentage rather than a fixed -Xmx: the JVM sizes the heap from the container's limit.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
