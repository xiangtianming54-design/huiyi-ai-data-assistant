# ===== 构建阶段 =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 项目级 Maven 配置（腾讯云镜像）。Docker Desktop 上 Java 双栈 AAAA 会卡住 DNS，强制 IPv4。
ENV JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"
COPY .mvn .mvn
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

# ===== 运行阶段 =====
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /build/target/huiyi-backend-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
