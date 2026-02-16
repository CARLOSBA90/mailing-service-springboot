# 🚀 Manual de Deploy — mail-service

---

## 📖 ¿Qué son los Spring Profiles?

Spring Boot permite tener **distintas configuraciones según el entorno** (desarrollo, producción, testing, etc.) sin cambiar código ni renombrar archivos.

### ¿Cómo funciona?

Cuando la app arranca, Spring Boot carga los archivos de configuración en este orden:

```
1. application.yml          ← SIEMPRE se carga (configuración base)
2. application-{perfil}.yml ← Se carga SI el perfil está activo
```

El archivo del perfil **sobreescribe** los valores del archivo base. No lo reemplaza completo, solo las propiedades que declara.

### Archivos en este proyecto

```
src/main/resources/
├── application.yml          ← Base (sirve para desarrollo)
└── application-prod.yml     ← Sobreescribe valores para producción
```

### Ejemplo concreto

**`application.yml`** (base):
```yaml
mail-service:
  api-key: ${API_KEY:dev-api-key-change-me}   # ← tiene fallback para dev
```

**`application-prod.yml`** (producción):
```yaml
mail-service:
  api-key: ${API_KEY}                          # ← SIN fallback, OBLIGA a configurar
```

- **En desarrollo**: Solo se lee `application.yml` → usa `dev-api-key-change-me` si no hay variable.
- **En producción**: Se lee `application.yml` + `application-prod.yml` → el segundo sobreescribe la API Key → si no existe la variable `API_KEY`, **la app no arranca** y te dice exactamente qué falta.

### ¿Cómo se activa un perfil?

El perfil se activa con la variable `SPRING_PROFILES_ACTIVE`:

| Método | Comando |
|---|---|
| Variable de entorno | `SPRING_PROFILES_ACTIVE=prod` |
| Argumento JVM | `java -Dspring.profiles.active=prod -jar app.jar` |
| En `application.yml` | `spring.profiles.active: dev` (valor por defecto) |

> **En este proyecto**, el `application.yml` tiene `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}`, lo que significa que **por defecto usa `dev`**. Solo cambia a `prod` si configurás la variable.

---

## 🖥️ Desarrollo Local

### Requisitos

- Java 17+
- Maven 3.8+

### Ejecutar

```bash
mvn spring-boot:run
```

1. Usa el perfil `dev` automáticamente
2. Usa la API Key de desarrollo (`dev-api-key-change-me`)
3. Conecta al SMTP en `localhost:25` sin autenticación
4. Muestra un warning en consola: `⚠ Usando API Key de desarrollo`

### Probar el endpoint

```bash
curl -X POST http://localhost:8081/api/mail/send \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-api-key-change-me" \
  -d '{
    "to": "test@ejemplo.com",
    "subject": "Prueba",
    "template": "welcome",
    "variables": { "nombre": "Carlos" }
  }'
```

---

## 🐳 Deploy en Docker (Producción)

### Requisitos

- Docker instalado en el servidor
- Un proveedor SMTP configurado (ej: Brevo, Gmail SMTP, Postfix)
- Una API Key segura generada

### Paso 1: Generar una API Key segura

Usa cualquiera de estos métodos para generar un token aleatorio:

```bash
# Linux/Mac
openssl rand -hex 32

# Resultado ejemplo: a1b2c3d4e5f6...64 caracteres hexadecimales
```

```powershell
# PowerShell (Windows)
-join ((1..64) | ForEach-Object { '{0:x}' -f (Get-Random -Max 16) })
```

> ⚠️ **Nunca uses** `dev-api-key-change-me` en producción. La app lo detecta y **se niega a arrancar**.

### Paso 2: Compilar el proyecto

```bash
mvn clean package -DskipTests
```

Esto genera: `target/mail-service-1.0.0-SNAPSHOT.jar`

### Paso 3: Construir la imagen Docker

```bash
docker build -t mail-service:latest .
```

### Paso 4: Ejecutar el contenedor

```bash
docker run -d \
  --name mail-service \
  -p 8081:8081 \
  -e API_KEY=tu-api-key-segura-generada-en-paso-1 \
  -e SPRING_MAIL_HOST=smtp.brevo.com \
  -e SPRING_MAIL_PORT=587 \
  -e SPRING_MAIL_USERNAME=tu-usuario@brevo.com \
  -e SPRING_MAIL_PASSWORD=tu-password-smtp \
  -e MAIL_FROM=no-reply@tudominio.com \
  -e MAIL_FROM_NAME="Mi Tienda" \
  mail-service:latest
```

> **Nota:** No necesitas pasar `SPRING_PROFILES_ACTIVE=prod` porque el `Dockerfile` ya lo fuerza automáticamente con `-Dspring.profiles.active=prod`.

### Paso 4 (alternativa): Docker Compose

```yaml
# docker-compose.yml
services:
  mail-service:
    build: .
    ports:
      - "8081:8081"
    environment:
      - API_KEY=tu-api-key-segura
      - SPRING_MAIL_HOST=smtp.brevo.com
      - SPRING_MAIL_PORT=587
      - SPRING_MAIL_USERNAME=tu-usuario@brevo.com
      - SPRING_MAIL_PASSWORD=tu-password-smtp
      - MAIL_FROM=no-reply@tudominio.com
      - MAIL_FROM_NAME=Mi Tienda
    restart: unless-stopped
```

```bash
docker compose up -d
```

---

## 📋 Variables de Entorno — Referencia Completa

| Variable | Obligatoria en Prod | Default (Dev) | Descripción |
|---|---|---|---|
| `API_KEY` | ✅ **Sí** | `dev-api-key-change-me` | Clave para autenticarse al enviar emails |
| `SPRING_MAIL_HOST` | ✅ Sí | `localhost` | Host del servidor SMTP |
| `SPRING_MAIL_PORT` | ✅ Sí | `25` | Puerto SMTP (587 para TLS) |
| `SPRING_MAIL_USERNAME` | ✅ Sí | *(vacío)* | Usuario SMTP |
| `SPRING_MAIL_PASSWORD` | ✅ Sí | *(vacío)* | Contraseña SMTP |
| `MAIL_FROM` | Recomendado | `no-reply@tudominio.com` | Dirección del remitente |
| `MAIL_FROM_NAME` | Opcional | `Mi Tienda` | Nombre visible del remitente |
| `SPRING_PROFILES_ACTIVE` | No* | `dev` | Perfil activo (*Docker lo fuerza a `prod`) |

---

## 🔒 ¿Qué cambia entre Dev y Prod?

| Aspecto | Dev | Prod |
|---|---|---|
| API Key | Fallback de desarrollo | **Obligatoria** (falla sin ella) |
| SMTP Auth | `false` | `true` (forzado) |
| SMTP TLS | `false` | `true` (forzado) |
| Nivel de Log | `INFO` | `WARN` |
| Usuario Docker | *(no aplica)* | `appuser` (no-root) |
| Warning en consola | ⚠ Usando API Key de dev | No |

---

## ❓ Preguntas Frecuentes

### ¿Tengo que renombrar archivos según el entorno?
**No.** Ambos archivos (`application.yml` y `application-prod.yml`) siempre están en el proyecto. Spring Boot elige cuál cargar según el perfil activo. No hay que mover, copiar ni renombrar nada.

### ¿Qué pasa si no configuro `API_KEY` en Docker?
La app **no arranca** y muestra este error en los logs:
```
API_KEY no configurada o usando valor por defecto.
En perfil 'prod' es obligatorio configurar la variable de entorno API_KEY.
```

### ¿Qué pasa si uso la key de desarrollo en Docker?
Mismo error. La app detecta `dev-api-key-change-me` y se niega a arrancar en perfil `prod`.

### ¿Cómo verifico que la app está corriendo?
```bash
curl http://localhost:8081/actuator/health
# Respuesta: {"status":"UP"}
```

### ¿Cómo verifico qué perfil está activo?
Buscá esta línea en los logs al arrancar:
```
The following 1 profile is active: "dev"
```
o
```
The following 1 profile is active: "prod"
```
