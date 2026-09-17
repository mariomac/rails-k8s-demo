FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package

FROM quay.io/wildfly/wildfly:41.0.1.Final-jdk21

COPY --from=build /build/target/ROOT.war /opt/jboss/wildfly/standalone/deployments/ROOT.war

EXPOSE 3000

CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-Djboss.http.port=3000"]
