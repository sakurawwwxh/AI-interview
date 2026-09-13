# ==============================================================================
# 第一阶段：构建环境 (Builder Stage)
# 作用：使用包含 JDK 和 Maven 的完整镜像来编译源代码
# ==============================================================================
FROM maven:3.9-eclipse-temurin-21 AS builder
# 设置工作目录，后续命令都在这个目录下执行
WORKDIR /workspace

# 1. 优先复制 Maven 构建配置文件（父 POM + app 模块 POM）
# 技巧：利用 Docker 缓存层机制。
# 只要 pom.xml 不变，这一层就会被缓存，从而跳过耗时的依赖下载步骤。
COPY pom.xml ./
COPY app/pom.xml app/

# 2. 预下载依赖 (Dependency Resolution)
# 作用：利用 Docker 缓存层预先下载依赖。只要 pom.xml 不变，这一层就会被缓存。
#
# 参数详解：
# -pl app -am:  只解析 app 模块及其依赖（父 POM）
# -B:           批处理模式（非交互式，适合 CI/Docker 环境）
# || true:      "兜底策略" (Fail-Safe)。go-offline 无法覆盖部分运行期才解析的插件依赖，
#               即使预下载失败也不要打断构建，后续的 package 步骤会再次检查并下载。
RUN mvn -pl app -am dependency:go-offline -B || true

# 3. 复制后端源代码
# 将 app 模块源代码复制进来。由于上面的步骤已经缓存了依赖，
# 即使源代码发生变化，也不会触发重新下载依赖。
COPY app app

# 4. 执行构建
# package     -> 编译并打包，Spring Boot repackage 生成可执行 JAR
# -DskipTests -> 跳过单元测试（加快构建速度）
RUN mvn -pl app -am package -DskipTests -B

# ==============================================================================
# 第二阶段：运行环境 (Runtime Stage)
# 作用：使用轻量级的 JRE 镜像来运行编译好的 JAR 包
# 技巧：多阶段构建 (Multi-stage build) 可以显著减小最终镜像的体积
# ==============================================================================
FROM eclipse-temurin:21-jre
WORKDIR /app

# 从第一阶段 (builder) 中复制生成的 JAR 文件到当前镜像
# 注意：Spring Boot repackage 会在 target 下生成两个文件：
#   app-<version>.jar           -> 可执行 JAR（fat jar，复制这个）
#   app-<version>.jar.original  -> repackage 前的原始 JAR
# 通配符 app-*.jar 只匹配以 .jar 结尾的文件，天然排除 .jar.original
COPY --from=builder /workspace/app/target/app-*.jar app.jar

# 声明容器将暴露的端口（仅作为文档说明，实际映射需在 docker-compose 中配置）
EXPOSE 8080

# 容器启动时执行的命令
ENTRYPOINT ["java", "-jar", "app.jar"]
