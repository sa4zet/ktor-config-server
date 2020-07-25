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
    openssh-client \
  && echo "config-server:x:1000:1000:,,,:/home/config-server:/bin/sh" >>/etc/passwd \
  && echo "config-server:x:1000:" >>/etc/group \
  && mkdir -p /home/config-server \
  && chown 1000:1000 /home/config-server \
  && rm -rf /tmp/* /var/cache/*

ENV GIT_SSH=/usr/bin/ssh
LABEL maintainer="sa4zet <docker@sa4zet.win>"

EXPOSE 5454

WORKDIR /app/
COPY --from=builder /build/target/*-jar-with-dependencies.jar /app/ktor-config-server.jar
ENTRYPOINT [ "java", "-jar", "/app/ktor-config-server.jar" ]
