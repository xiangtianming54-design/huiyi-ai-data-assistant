# ===== 构建阶段 =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 项目级 Maven 配置（阿里云镜像），随后先离线依赖再编译，充分利用镜像层缓存
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
