ARG MAVEN_IMAGE=swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/eclipse-temurin:17-jre-jammy
FROM ${MAVEN_IMAGE} AS builder
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp package -DskipTests

FROM ${JRE_IMAGE}
ARG UBUNTU_MIRROR=https://mirrors.aliyun.com/ubuntu
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN set -eux; \
    if [ -f /etc/apt/sources.list ]; then \
        sed -i "s|http://archive.ubuntu.com/ubuntu|${UBUNTU_MIRROR}|g; s|http://security.ubuntu.com/ubuntu|${UBUNTU_MIRROR}|g" /etc/apt/sources.list; \
    fi; \
    apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r appgroup \
    && useradd -r -g appgroup appuser \
    && mkdir -p /app/data/logs \
    && chown -R appuser:appgroup /app
USER appuser
EXPOSE 8080
HEALTHCHECK CMD curl -fsS http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
