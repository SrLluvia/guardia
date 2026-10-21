# One Dockerfile for all three services.
#
# The build stage compiles the whole reactor once; the runtime stage then picks out a single
# service's jar by MODULE. Building each service from its own Dockerfile would download the
# same dependencies three times, and `docker compose build` reuses this stage across all
# three.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

# Poms first, so a change to source code does not re-resolve every dependency.
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
COPY platform/pom.xml platform/
COPY incidents/pom.xml incidents/
COPY scheduling/pom.xml scheduling/
COPY sla/pom.xml sla/
COPY e2e/pom.xml e2e/
RUN ./mvnw -B --no-transfer-progress -pl platform,incidents,scheduling,sla dependency:go-offline

COPY platform platform
COPY incidents incidents
COPY scheduling scheduling
COPY sla sla
# The end-to-end module needs Docker itself to run, which is not available in here, so it is
# left out of the image build entirely.
RUN ./mvnw -B --no-transfer-progress -pl platform,incidents,scheduling,sla package -DskipTests


FROM eclipse-temurin:25-jre AS runtime
ARG MODULE
# curl is here only so the healthcheck can ask the service whether it is ready; compose waits
# on that rather than on the container merely having started.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Not root: nothing in here needs to be.
RUN useradd --system --create-home --uid 10001 guardia
USER guardia
WORKDIR /app

COPY --from=build /src/${MODULE}/target/${MODULE}-*-app.jar /app/service.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/service.jar"]
