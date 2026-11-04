FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
# las pruebas corren aqui: una configuracion rota no debe llegar a la imagen
RUN mvn -B package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /app/target/config-server-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8888
# HTTP real contra /actuator/health (no basta con que el puerto este abierto); la imagen no trae curl
HEALTHCHECK --interval=15s --timeout=10s --start-period=30s --retries=10 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/localhost/8888 && printf 'GET /actuator/health HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]
ENTRYPOINT ["java", "-jar", "app.jar"]
