FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# El JAR se copia y renombra a app.jar para independizar el ENTRYPOINT del nombre de versión
COPY target/mail-service-1.0.0-SNAPSHOT.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8020

ENTRYPOINT ["java", "-jar", "app.jar"]
