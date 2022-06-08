FROM mariadb:10.4-focal

RUN apt-get update && apt-get -y install \
  openjdk-11-jre-headless \
  && rm -fr /var/lib/apt/lists/*

COPY ./butler-server/build/libs/ /butler/
CMD ["/sbin/init"]

COPY ./butler-server/src/main/resources/db/schema.sql /butler/
COPY ./butler-server/docker/entrypoint.sh /usr/local/bin/

EXPOSE 8080/tcp

ENTRYPOINT ["entrypoint.sh"]
