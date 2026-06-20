#!/bin/bash
set -e

# Build cap-captcha-keycloak.jar and cap-captcha-theme.jar in a Docker
# container and copy them to target/.
# Requires Docker. For a native build with Java 17 + Maven, run:
#     mvn clean compile package

docker build -t cap-captcha-keycloak:build .
id=$(docker create cap-captcha-keycloak:build)
mkdir -p target
docker cp "$id:/cap-captcha-keycloak.jar" target/cap-captcha-keycloak.jar
docker cp "$id:/cap-captcha-theme.jar" target/cap-captcha-theme.jar
docker rm "$id"
echo "Built target/cap-captcha-keycloak.jar target/cap-captcha-theme.jar"
