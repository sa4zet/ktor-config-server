FROM alpine:latest as builder
RUN apk update \
  && apk add --no-cache \
    maven \
    openjdk8
COPY . /build/
WORKDIR /build/
RUN mvn clean package

FROM alpine:latest
RUN apk update \
  && apk add --no-cache \
    openjdk8-jre \
    curl \
  && echo "config-server:x:1000:1000:,,,:/home/config-server:/bin/sh" >>/etc/passwd \
  && echo "config-server:x:1000:" >>/etc/group \
  && mkdir -p /home/config-server \
  && chown 1000:1000 /home/config-server \
  && rm -rf /tmp/* /var/cache/* \
  && mkdir -p /var/cache/apk

LABEL maintainer="sa4zet <docker@sa4zet.win>"
EXPOSE 5454
WORKDIR /app/

HEALTHCHECK \
  --interval=3m \
  --retries=2 \
  --timeout=2s \
  CMD if [ "ok" == $(curl --silent --show-error --max-time 3 "http://localhost:5454/health_check") ]; then exit 0; else exit 1; fi;

COPY --from=builder /build/target/*-jar-with-dependencies.jar /app/ktor-config-server.jar
USER config-server
ENTRYPOINT [ "java", "-jar", "/app/ktor-config-server.jar" ]
