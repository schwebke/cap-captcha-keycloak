FROM maven:3.9-eclipse-temurin-17

WORKDIR /build

# Pre-fetch deps for better layer caching. Copy all three poms so the
# multi-module reactor can resolve without sources.
COPY pom.xml .
COPY src/cap-captcha-spi/pom.xml src/cap-captcha-spi/
COPY src/cap-captcha-theme/pom.xml src/cap-captcha-theme/
RUN mvn -B -q dependency:go-offline

# Build both modules (SPI + theme).
COPY src ./src
RUN mvn -B clean package \
    && cp src/cap-captcha-spi/target/cap-captcha-keycloak.jar /cap-captcha-keycloak.jar \
    && cp src/cap-captcha-theme/target/cap-captcha-theme.jar /cap-captcha-theme.jar
