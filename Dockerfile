FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY target/mail-service-1.0.0-SNAPSHOT.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8081

# En Docker siempre se activa el perfil 'prod'
# La variable API_KEY debe ser provista via docker run -e API_KEY=... o docker-compose
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
