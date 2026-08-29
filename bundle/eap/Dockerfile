ARG EAP_BUILDER_IMAGE=registry.redhat.io/jboss-eap-8/eap81-openjdk21-builder-openshift-rhel9@sha256:ed4244965392a4db27fa869f02cb629ffe5c32eb11c2dba191c16415ef9820dd
ARG EAP_RUNTIME_IMAGE=registry.redhat.io/jboss-eap-8/eap81-openjdk21-runtime-openshift-rhel9@sha256:2c6c82f312125c937d9ffe2b98a5efb144b4e436a4e7fbd4b6f119857493c6f3

FROM ${EAP_BUILDER_IMAGE} AS eap-builder
ENV GALLEON_PROVISION_FEATURE_PACKS=org.jboss.eap:wildfly-ee-galleon-pack
ENV GALLEON_PROVISION_LAYERS=jaxrs-server,web-console
ENV GALLEON_PROVISION_CHANNELS=org.jboss.eap.channels:eap-8.1:1.7.1.GA-redhat-00001
RUN /usr/local/s2i/assemble

FROM maven:3.9.11-eclipse-temurin-21 AS jdbc
RUN mvn -B org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy \
    -Dartifact=com.oracle.database.jdbc:ojdbc11:23.6.0.24.10 \
    -DoutputDirectory=/jdbc

FROM ${EAP_RUNTIME_IMAGE}
USER root

COPY --from=eap-builder --chown=185:0 /opt/server /opt/server
RUN mkdir -p /opt/server/modules/com/oracle/ojdbc/main
COPY --from=jdbc /jdbc/ojdbc11-*.jar /opt/server/modules/com/oracle/ojdbc/main/ojdbc11.jar
COPY module.xml /opt/server/modules/com/oracle/ojdbc/main/module.xml
COPY configure-datasource.cli /usr/local/etc/itz/configure-datasource.cli
COPY launch-eap.sh /usr/local/bin/launch-eap.sh
RUN chmod 0755 /usr/local/bin/launch-eap.sh && chown -R 185:0 /opt/server/modules/com/oracle /usr/local/etc/itz

USER 185
EXPOSE 8080 9990
ENTRYPOINT ["/usr/local/bin/launch-eap.sh"]
