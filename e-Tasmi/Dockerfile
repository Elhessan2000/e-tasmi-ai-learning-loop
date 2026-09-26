FROM tomcat:9.0-jdk17-temurin AS tomcat-libs
FROM eclipse-temurin:17-jdk-jammy AS build
COPY --from=tomcat-libs /usr/local/tomcat/lib/tomcat-jdbc.jar /opt/tomcat-lib/tomcat-jdbc.jar
RUN apt-get update \
    && apt-get install -y ant \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY . .
WORKDIR /workspace/e-Tasmi
RUN set -eux; \
    mkdir -p /workspace/package/WEB-INF/classes /workspace/package/WEB-INF/lib; \
    cp -r web/. /workspace/package/; \
    cp -n web/WEB-INF/lib/*.jar /workspace/package/WEB-INF/lib/ || true; \
    cp -n build/web/WEB-INF/lib/*.jar /workspace/package/WEB-INF/lib/ || true; \
    find src/java -name "*.java" | sort > /tmp/java-sources.txt; \
    CLASSPATH=$(find /workspace/package/WEB-INF/lib /opt/tomcat-lib -name "*.jar" | tr '\n' ':'); \
    javac -encoding UTF-8 -source 17 -target 17 -cp "$CLASSPATH" -d /workspace/package/WEB-INF/classes @/tmp/java-sources.txt; \
    if [ -d src/conf ]; then cp -r src/conf/. /workspace/package/WEB-INF/classes/; fi; \
    cd /workspace/package && jar -cf /workspace/e-Tasmi.war .

FROM tomcat:9.0-jdk17-temurin
# Fonts for Arabic transcript rendering in evaluation PDFs (AWT → raster embed).
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-noto \
    && rm -rf /var/lib/apt/lists/*
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /workspace/e-Tasmi.war /usr/local/tomcat/webapps/ROOT.war

# Make the HTTP connector bind to $PORT (Railway injects it) on 0.0.0.0.
# Tomcat supports ${system.property} substitution in server.xml; we inject the
# value at runtime via CATALINA_OPTS so the image stays portable (local Docker
# Compose still works because PORT defaults to 8080).
RUN sed -i 's|port="8080" protocol="HTTP/1.1"|port="${port.http}" address="0.0.0.0" protocol="HTTP/1.1"|' \
    /usr/local/tomcat/conf/server.xml

EXPOSE 8080

CMD ["sh", "-c", "CATALINA_OPTS=\"-Dport.http=${PORT:-8080} ${CATALINA_OPTS:-}\" exec catalina.sh run"]
