#!/bin/bash

if [ ! -e /config/netrc ]; then
    echo "Error: Could not find /config/netrc" >&2
    exit 1
fi

export MYSQL_ROOT_PASSWORD="butler-test-password"

bash -c "/docker-entrypoint.sh mysqld" &

# Wait for the temporary server to come up
while [ ! -e "/var/run/mysqld/mysqld.sock" ]; do
    sleep 1
done

# Wait for the real server to come up
while [ $(ps aux | grep -v grep | grep -c 'skip-networking') -eq 1 ]; do
    sleep 1
done

while [ ! -e "/var/run/mysqld/mysqld.sock" ]; do
    sleep 1
done

echo "CREATE USER test@'%';
CREATE DATABASE butler_db CHARACTER SET utf8;
GRANT ALL PRIVILEGES ON butler_db.* to test;
USE butler_db;
SOURCE /butler/schema.sql" | mysql -u root --password="$MYSQL_ROOT_PASSWORD"

cp /config/netrc ~/.netrc

/butler/butler*.jar
