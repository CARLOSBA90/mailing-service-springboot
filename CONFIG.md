# 🔐 Gestión de Configuración y Secretos

Este proyecto usa **Spring Profiles** para manejar diferentes entornos de forma segura.

## 📁 Estructura de Archivos de Configuración

```
src/main/resources/
├── application.yml                    # ✅ Configuración base (se sube a GitHub)
├── application-dev.yml                # ❌ Credenciales de desarrollo (NO se sube)
├── application-dev.yml.example        # ✅ Plantilla de ejemplo (se sube a GitHub)
└── application-prod.yml               # ✅ Lee variables de entorno (se sube a GitHub)
```

## 🚀 Configuración Inicial (Primera vez)

### 1. Crear tu archivo de desarrollo local

```bash
# Copia el archivo de ejemplo
cp src/main/resources/application-dev.yml.example src/main/resources/application-dev.yml

# Edita application-dev.yml con tus credenciales reales
```

### 2. Completar `application-dev.yml` con tus credenciales

```yaml
spring:
  mail:
    host: smtp-relay.brevo.com
    port: 587
    username: TU_EMAIL_BREVO
    password: TU_PASSWORD_BREVO
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

mail-service:
  api-key: dev-secret-key-123
  from:
    address: tu-email-verificado@ejemplo.com
    name: Dev Mail Service
```

## 💻 Desarrollo Local

### Ejecutar con perfil de desarrollo:

```bash
# Opción 1: Maven
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Opción 2: Variable de entorno
export SPRING_PROFILES_ACTIVE=dev
mvn spring-boot:run

# Opción 3: IntelliJ IDEA
# Run > Edit Configurations > Active profiles: dev
```

## 🐳 Producción con Docker

### 1. Crear `.env` en el servidor (NO en el repositorio)

```env
# .env (en el servidor de producción)
SPRING_MAIL_HOST=smtp-relay.brevo.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=prod-user@ejemplo.com
SPRING_MAIL_PASSWORD=super-secret-password
SPRING_MAIL_AUTH=true
SPRING_MAIL_STARTTLS=true
MAIL_FROM=noreply@tudominio.com
MAIL_FROM_NAME=Mi Aplicación
API_KEY=super-secret-api-key-prod-xyz
```

### 2. Configurar `docker-compose.yml`

```yaml
version: '3.8'
services:
  mail-service:
    build: .
    ports:
      - "8081:8081"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_MAIL_HOST=${SPRING_MAIL_HOST}
      - SPRING_MAIL_PORT=${SPRING_MAIL_PORT}
      - SPRING_MAIL_USERNAME=${SPRING_MAIL_USERNAME}
      - SPRING_MAIL_PASSWORD=${SPRING_MAIL_PASSWORD}
      - SPRING_MAIL_AUTH=${SPRING_MAIL_AUTH}
      - SPRING_MAIL_STARTTLS=${SPRING_MAIL_STARTTLS}
      - MAIL_FROM=${MAIL_FROM}
      - MAIL_FROM_NAME=${MAIL_FROM_NAME}
      - API_KEY=${API_KEY}
    env_file:
      - .env
```

### 3. Desplegar

```bash
docker-compose up -d
```

## 🔒 Seguridad

### ✅ Archivos que SÍ se suben a GitHub:
- `application.yml` (sin secretos)
- `application-prod.yml` (solo lee variables de entorno)
- `application-dev.yml.example` (plantilla sin secretos)
- `docker-compose.yml` (lee variables del .env)

### ❌ Archivos que NO se suben a GitHub:
- `application-dev.yml` (credenciales de desarrollo)
- `.env` (credenciales de producción)

Estos archivos están protegidos en `.gitignore`:
```gitignore
src/main/resources/application-dev.yml
*.env
```

## 📝 Notas Importantes

1. **Nunca commits credenciales reales** en archivos que se suben a GitHub
2. **`application-dev.yml` es personal** - cada desarrollador tiene el suyo
3. **En producción** usa variables de entorno, nunca archivos con secretos
4. **Brevo requiere email verificado** como remitente (`MAIL_FROM`)

## 🧪 Testing

Para probar el envío de emails:

```bash
curl -X POST http://localhost:8081/api/mail/send \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-secret-key-123" \
  -d '{
    "to": "destinatario@ejemplo.com",
    "subject": "Test",
    "templateName": "welcome",
    "variables": {
      "nombre": "Carlos"
    }
  }'
```
