FROM python:3.11-slim AS builder

WORKDIR /app

RUN set -eux; \
    sed -i 's@http://deb.debian.org@https://mirrors.aliyun.com@g' /etc/apt/sources.list.d/debian.sources; \
    sed -i 's@http://security.debian.org@https://mirrors.aliyun.com@g' /etc/apt/sources.list.d/debian.sources; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates openjdk-21-jdk-headless maven; \
    rm -rf /var/lib/apt/lists/*

COPY --from=m2repo . /root/.m2/repository
RUN find /root/.m2/repository -name _remote.repositories -delete && \
    find /root/.m2/repository -name "*.lastUpdated" -delete

COPY pom.xml ./
COPY src ./src
RUN mvn -o -B -ntp package -DskipTests

FROM python:3.11-slim

WORKDIR /app

RUN set -eux; \
    sed -i 's@http://deb.debian.org@https://mirrors.aliyun.com@g' /etc/apt/sources.list.d/debian.sources; \
    sed -i 's@http://security.debian.org@https://mirrors.aliyun.com@g' /etc/apt/sources.list.d/debian.sources; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates openjdk-21-jre-headless; \
    rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
