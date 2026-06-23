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
├── application.yml          ← Base (valores vacíos, resueltos por cada perfil)
├── application-dev.yml      ← Desarrollo (valores locales hardcodeados)
└── application-prod.yml     ← Producción (todo desde variables de entorno)
```

### ¿Cómo se activa un perfil?

| Método | Comando |
|---|---|
| Variable de entorno | `SPRING_PROFILES_ACTIVE=prod` |
| Argumento JVM | `java -Dspring.profiles.active=prod -jar app.jar` |
| En `application.yml` | `spring.profiles.active: dev` (valor por defecto) |

> En este proyecto el `application.yml` tiene `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}`, lo que significa que **por defecto usa `dev`**. Solo cambia a `prod` si configurás la variable.

---

## 🖥️ Desarrollo Local

### Requisitos

- Java 17+
- Maven 3.8+
- Docker (solo para PostgreSQL)

### Paso 1: Levantar la base de datos

```bash
docker compose up -d postgres
```

Esto levanta solo el contenedor de PostgreSQL. La base de datos, usuario y contraseña se crean automáticamente con los valores del `.env`.

### Paso 2: Ejecutar la aplicación

```bash
mvn spring-boot:run
```

- Usa el perfil `dev` automáticamente (archivo `application-dev.yml`).
- Crea las tablas automáticamente (`ddl-auto: update`).
- Admin panel en: `http://localhost:8020/admin` (usuario: `admin` / contraseña: `admin123`).

### Paso 3: Probar el endpoint

```bash
curl -X POST http://localhost:8020/api/mail/send \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-api-key-change-me" \
  -d '{
    "to": "test@ejemplo.com",
    "subject": "Prueba",
    "template": "welcome",
    "variables": { "customerName": "Carlos", "link": "https://example.com/confirm" }
  }'
```

---

## 🐳 Deploy en Producción (Docker Compose)

### Requisitos

- Docker y Docker Compose instalados en el servidor
- Un proveedor SMTP configurado (ej: Brevo, Gmail SMTP, SendGrid)
- Acceso SSH al servidor

### Estructura en el servidor

```
/var/java-dist/mail-service/
├── Dockerfile
├── docker-compose.yml
├── .env                   ← secretos reales (NO se sube a Git)
└── target/
    └── mail-service-1.0.0-SNAPSHOT.jar
```

---

### Paso 1: Compilar el proyecto (en tu máquina local)

```bash
mvn clean install -DskipTests
```

Esto genera: `target/mail-service-1.0.0-SNAPSHOT.jar`

### Paso 2: Subir archivos al servidor

```bash
# Crear la estructura en el servidor si no existe
ssh usuario@tuserver "mkdir -p /var/java-dist/mail-service/target"

# Subir el JAR compilado
scp target/mail-service-1.0.0-SNAPSHOT.jar \
    usuario@tuserver:/var/java-dist/mail-service/target/

# Subir Dockerfile y docker-compose.yml (solo si cambiaron)
scp Dockerfile docker-compose.yml \
    usuario@tuserver:/var/java-dist/mail-service/
```

> ⚠️ **NO subir el `.env` por SCP.** Las credenciales de producción deben crearse directamente en el servidor. Ver Paso 3.

### Paso 3: Crear el `.env` en el servidor

Conectate por SSH y creá el archivo directamente allí:

```bash
ssh usuario@tuserver
cd /var/java-dist/mail-service
nano .env
```

Pegá el siguiente contenido y completá con tus valores reales:

```env
# ── Perfil de Spring Boot ────────────────────────────────────────────────────
SPRING_PROFILES_ACTIVE=prod

# ── Base de datos PostgreSQL ─────────────────────────────────────────────────
POSTGRES_DB=mailservice
POSTGRES_USER=mailuser
POSTGRES_PASSWORD=tu-password-seguro-aqui

# ── Puertos expuestos ────────────────────────────────────────────────────────
POSTGRES_PORT=5432
APP_PORT=8081

# ── Servidor SMTP (ej. Brevo / Gmail / SendGrid) ────────────────────────────
SPRING_MAIL_HOST=smtp-relay.brevo.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=tu-usuario@smtp-brevo.com
SPRING_MAIL_PASSWORD=tu-api-key-smtp-aqui
SPRING_MAIL_AUTH=true
SPRING_MAIL_STARTTLS=true

# ── Remitente de los emails ──────────────────────────────────────────────────
MAIL_FROM=no-reply@tudominio.com
MAIL_FROM_NAME=Tu Empresa

# ── API Key para autenticación de la API REST ────────────────────────────────
# Generar con: openssl rand -hex 32
API_KEY=pon-aqui-una-clave-muy-larga-y-segura

# ── Credenciales del Panel de Administración ─────────────────────────────────
# Generar password con: openssl rand -base64 24
ADMIN_USERNAME=admin
ADMIN_PASSWORD=pon-aqui-una-contraseña-fuerte

# ── Cron de depuración de logs (cada 6 meses) ───────────────────────────────
PURGE_CRON=0 0 3 1 1,7 *
```

#### ¿Cómo generar claves seguras?

```bash
# API Key (64 caracteres hexadecimales)
openssl rand -hex 32

# Contraseña de admin (32 caracteres base64)
openssl rand -base64 24

# Contraseña de base de datos
openssl rand -base64 20
```

#### ¿Por qué no se sube el `.env` por SCP?

No es un problema de interceptación (SCP viaja cifrado por SSH), sino de **seguridad operativa**:

1. **Error humano:** Podrías subir por accidente tu `.env` de desarrollo y pisar las credenciales de producción.
2. **Superficie de ataque:** Las claves de producción solo deben existir en el servidor, nunca en tu laptop.
3. **Principio de mínimo privilegio:** Cuantos menos lugares tengan los secretos reales, más seguro es el sistema.

### Paso 4: Primer inicio (crear tablas)

**Solo la primera vez**, necesitás que Hibernate cree las tablas. Cambiá temporalmente el `ddl-auto`:

```bash
# Agregar temporalmente esta variable al .env:
echo "SPRING_JPA_DDL_AUTO=update" >> .env

# Levantar todo
docker compose --profile production up -d --build

# Verificar que arrancó bien
docker compose logs -f mail-service

# Una vez que las tablas se crearon, ELIMINAR la línea temporal:
sed -i '/SPRING_JPA_DDL_AUTO/d' .env

# Reiniciar para que vuelva al modo 'validate'
docker compose --profile production restart mail-service
```

### Paso 5: Levantar los servicios

```bash
cd /var/java-dist/mail-service
docker compose --profile production up -d --build
```

---

### Paso 6: Configurar Nginx (proxy inverso)

La app corre en el puerto **8020** con `context-path: /mail-service`. Agregá el siguiente bloque en tu archivo de Nginx (`/etc/nginx/sites-available/tudominio`):

```nginx
################### --- SPRING BOOT - MAIL-SERVICE ---  ###################

    # Actuator bloqueado desde internet — solo accesible desde el servidor
    location /mail-service/actuator/ {
        allow 127.0.0.1;
        deny all;
    }

    location /mail-service/ {
        proxy_pass http://localhost:8020/mail-service/;   # ← mantiene el prefijo completo
        proxy_http_version 1.1;
        proxy_set_header Host               $host;
        proxy_set_header X-Real-IP          $remote_addr;
        proxy_set_header X-Forwarded-For    $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto  $scheme;
    }

################### --- SPRING BOOT - MAIL-SERVICE ---  ###################
```

#### ¿Cómo funciona este bloque?

| Directiva | Qué hace |
|---|---|
| `proxy_pass http://localhost:8020/mail-service/;` | Nginx reenvía la URL completa con el prefijo `/mail-service/`. Spring la recibe tal cual y la resuelve con su `context-path` |
| `context-path: /mail-service` (en Spring) | Thymeleaf automáticamente antepone `/mail-service` a todas las URLs generadas (`@{/css/...}`, `@{/admin/...}`, redirects de login) |
| `location /mail-service/actuator/` | Bloquea el actuator desde internet (retorna 403). Sigue accesible desde el servidor vía `curl localhost` |

> ⚠️ **El `proxy_pass` DEBE terminar en `/mail-service/` (con el prefijo).** Si termina en solo `/`, nginx recorta el prefijo y Spring no encontrará las rutas, volverán 404.

**Aplicar los cambios:**
```bash
nginx -t                  # verificar config sin errores de sintáxis
systemctl reload nginx    # aplicar sin interrumpir conexiones activas
```

**Verificar health desde el servidor (acceso directo, sin nginx):**
```bash
curl http://localhost:8020/mail-service/actuator/health
# {"status":"UP"}
```

> ℹ️ El actuator está bloqueado desde internet por nginx (`deny all`). Solo es accesible desde el propio servidor con `curl localhost`.

**Verificar admin panel:**
```bash
# Desde el navegador (debe mostrar login con diseño oscuro):
https://tudominio.com/mail-service/admin
```

**Rutas resultantes:**

| Ruta pública (externa) | Spring recibe | Resultado |
|---|---|---|
| `tudominio.com/mail-service/api/mail/send` | `POST /mail-service/api/mail/send` → servlet `/api/mail/send` | ✅ Requiere `X-API-Key` |
| `tudominio.com/mail-service/admin` | `GET /mail-service/admin` → servlet `/admin` | ✅ Requiere login |
| `tudominio.com/mail-service/actuator/` | Bloqueado en nginx | ❌ 403 Forbidden |
| `servidor: curl localhost:8020/mail-service/actuator/health` | Directo a Spring | ✅ `{"status":"UP"}` |

---



## 🔀 Alternativa: Variables por línea de comandos

Si preferís no usar un archivo `.env`, podés exportar las variables directamente en la terminal.

### Linux / macOS / WSL

```bash
export SPRING_PROFILES_ACTIVE=prod
export POSTGRES_DB=mailservice
export POSTGRES_USER=mailuser
export POSTGRES_PASSWORD=tu-password-seguro
export POSTGRES_PORT=5432
export APP_PORT=8081
export SPRING_MAIL_HOST=smtp-relay.brevo.com
export SPRING_MAIL_PORT=587
export SPRING_MAIL_USERNAME=tu-usuario@smtp
export SPRING_MAIL_PASSWORD=tu-api-key-smtp
export SPRING_MAIL_AUTH=true
export SPRING_MAIL_STARTTLS=true
export MAIL_FROM=no-reply@tudominio.com
export MAIL_FROM_NAME="Tu Empresa"
export API_KEY=clave-muy-larga-y-segura
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD=contraseña-fuerte
export PURGE_CRON="0 0 3 1 1,7 *"

docker compose --profile production up -d
```

### PowerShell (Windows)

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:POSTGRES_DB="mailservice"
$env:POSTGRES_USER="mailuser"
$env:POSTGRES_PASSWORD="tu-password-seguro"
$env:POSTGRES_PORT="5432"
$env:APP_PORT="8081"
$env:SPRING_MAIL_HOST="smtp-relay.brevo.com"
$env:SPRING_MAIL_PORT="587"
$env:SPRING_MAIL_USERNAME="tu-usuario@smtp"
$env:SPRING_MAIL_PASSWORD="tu-api-key-smtp"
$env:SPRING_MAIL_AUTH="true"
$env:SPRING_MAIL_STARTTLS="true"
$env:MAIL_FROM="no-reply@tudominio.com"
$env:MAIL_FROM_NAME="Tu Empresa"
$env:API_KEY="clave-muy-larga-y-segura"
$env:ADMIN_USERNAME="admin"
$env:ADMIN_PASSWORD="contraseña-fuerte"
$env:PURGE_CRON="0 0 3 1 1,7 *"

docker compose --profile production up -d
```

> ⚠️ Las variables exportadas solo viven en la sesión actual de la terminal. Si cerrás la terminal, tenés que volver a exportarlas.

---

## 📋 Variables de Entorno — Referencia Completa

| Variable | Obligatoria Prod | Default Dev | Descripción |
|---|:---:|---|---|
| `SPRING_PROFILES_ACTIVE` | ✅ | `dev` | Perfil activo de Spring Boot |
| `POSTGRES_DB` | ✅ | `mailservice` | Nombre de la base de datos |
| `POSTGRES_USER` | ✅ | `mailuser` | Usuario de PostgreSQL |
| `POSTGRES_PASSWORD` | ✅ | `mailpass123` | Contraseña de PostgreSQL |
| `POSTGRES_PORT` | ✅ | `5432` | Puerto expuesto de PostgreSQL |
| `APP_PORT` | ✅ | `8081` | Puerto expuesto de la aplicación |
| `SPRING_MAIL_HOST` | ✅ | `localhost` | Host del servidor SMTP |
| `SPRING_MAIL_PORT` | ✅ | `25` | Puerto SMTP (587 para TLS) |
| `SPRING_MAIL_USERNAME` | ✅ | *(vacío)* | Usuario SMTP |
| `SPRING_MAIL_PASSWORD` | ✅ | *(vacío)* | Contraseña SMTP |
| `SPRING_MAIL_AUTH` | ✅ | `true` | Autenticación SMTP |
| `SPRING_MAIL_STARTTLS` | ✅ | `true` | Cifrado TLS para SMTP |
| `MAIL_FROM` | ✅ | `no-reply@cepr0.com` | Dirección del remitente |
| `MAIL_FROM_NAME` | ✅ | `CEPR0` | Nombre visible del remitente |
| `API_KEY` | ✅ | `dev-api-key-change-me` | Clave de autenticación de la API REST |
| `ADMIN_USERNAME` | ✅ | `admin` | Usuario del panel de administración |
| `ADMIN_PASSWORD` | ✅ | `admin123` | Contraseña del panel de administración |
| `PURGE_CRON` | ✅ | `0 0 3 1 1,7 *` | Cron de depuración automática de logs |

---

## 🔒 ¿Qué cambia entre Dev y Prod?

| Aspecto | Dev | Prod |
|---|---|---|
| Fuente de config | Valores hardcodeados en `application-dev.yml` | Variables de entorno (`.env`) |
| API Key | Fallback de desarrollo | **Obligatoria** (falla sin ella) |
| DDL Auto | `update` (crea tablas automáticamente) | `validate` (solo verifica) |
| SQL en consola | `true` (muestra queries) | `false` |
| SMTP Auth/TLS | `true` (Brevo) | Según variables de entorno |
| Usuario Docker | *(no aplica)* | `appuser` (no-root) |

---

## 🔄 Actualizar la aplicación

Para desplegar una nueva versión:

```bash
# 1. En tu máquina local: compilar
mvn clean install -DskipTests

# 2. Subir el nuevo JAR al servidor
scp target/mail-service-1.0.0-SNAPSHOT.jar \
    usuario@tuserver:/var/java-dist/mail-service/target/

# 3. En el servidor: reconstruir y reiniciar
ssh usuario@tuserver
cd /var/java-dist/mail-service
docker compose --profile production up -d --build
```

---

## 📌 Comandos Rápidos

| Acción | Comando |
|---|---|
| Levantar | `docker compose --profile production up -d` |
| Levantar y reconstruir | `docker compose --profile production up -d --build` |
| Detener | `docker compose --profile production down` |
| Ver logs en tiempo real | `docker compose logs -f mail-service` |
| Ver estado de servicios | `docker compose ps` |
| Reiniciar la app | `docker compose --profile production restart mail-service` |
| Solo la base de datos | `docker compose up -d postgres` |

---

## 🔐 Notas de Seguridad

> ⚠️ **El archivo `.env` NUNCA debe subirse a GitHub.**
> Verificá que `.env` esté incluido en tu `.gitignore`.

- El archivo `.env.example` (con valores vacíos) **sí se sube** al repositorio como referencia.
- El archivo `.env` solo funciona si está en el **mismo directorio** desde el que ejecutás `docker-compose`.
- Las credenciales de producción **solo deben existir en el servidor**, nunca en tu máquina personal.

---

## ❓ Preguntas Frecuentes y Solución de Problemas

---

### 🟡 El panel de admin se ve en blanco (sin CSS / sin diseño oscuro)

**Causa:** El JAR desplegado es una versión anterior a los cambios de configuración, o el `proxy_pass` de nginx está recortando el prefijo `/mail-service/`.

**Diagnóstico — mirar el HTML generado:**

En el navegador, ver el código fuente de la página de login:

| Lo que ves en el HTML | Qué significa |
|---|---|
| `href="/mail-service/css/admin.css"` | ✅ Correcto — Spring tiene el context-path bien |
| `href="/css/admin.css"` | ❌ JAR desactualizado o context-path no configurado |

**Solución paso a paso:**

```bash
# 1. En tu máquina local — compilar con los cambios
mvn clean install -DskipTests

# 2. Subir el JAR al servidor
scp target/mail-service-1.0.0-SNAPSHOT.jar \
    usuario@tuserver:/var/java-dist/mail-service/target/

# 3. Verificar el nginx (proxy_pass debe tener el prefijo)
grep proxy_pass /etc/nginx/sites-available/tudominio
# Debe mostrar: proxy_pass http://localhost:8020/mail-service/;
# Si muestra:   proxy_pass http://localhost:8020/;  ← INCORRECTO, corregir

# 4. Reiniciar contenedor y recargar nginx
cd /var/java-dist/mail-service
docker compose --profile production up -d --build
nginx -t && systemctl reload nginx

# 5. Verificar health con el context-path correcto
curl http://localhost:8020/mail-service/actuator/health
# {"status":"UP"}
```

---

### 🟡 El login redirige a `/admin/login` y da 404 (no `/mail-service/admin/login`)

**Causa:** El JAR desactualizado no tiene el `context-path` configurado. Spring genera el redirect sin el prefijo y nginx no encuentra la ruta.

**Solución:** Igual que el caso anterior: reconstruir el JAR y corregir el `proxy_pass` en nginx.

---

### 🟡 El actuator devuelve 404 desde el servidor

**Causa:** El URL de health tiene que incluir el context-path:

```bash
# ❌ Incorrecto (sin context-path)
curl http://localhost:8020/actuator/health

# ✅ Correcto (con context-path)
curl http://localhost:8020/mail-service/actuator/health
# {"status":"UP"}
```

---

### 🔴 Error: `missing table [mail_logs]` al arrancar

**Causa:** Es la primera vez que se levanta la app en esta base de datos. `ddl-auto: validate` (modo producción) verifica que las tablas existan, pero no las crea.

**Solución — crear las tablas una sola vez:**

```bash
# 1. Agregar temporalmente al .env
echo "SPRING_JPA_DDL_AUTO=update" >> .env

# 2. Reconstruir y levantar
docker compose --profile production up -d --build

# 3. Esperar logs de éxito:
docker compose logs -f mail-service
# Buscar: "Started MailServiceApplication in X seconds"

# 4. Una vez arrancada, quitar la variable temporal
sed -i '/SPRING_JPA_DDL_AUTO/d' .env

# 5. Reiniciar en modo validate (seguro para producción)
docker compose --profile production restart mail-service
```

> ⚠️ Este proceso solo es necesario la **primera vez** o cuando se agrega una nueva tabla al modelo.

---

### 🔴 Error: `docker-compose: command not found`

**Causa:** Solo está instalada la v2 de Docker Compose (como plugin), que usa `docker compose` (con espacio, sin guión).

**Verificar versión instalada:**
```bash
docker compose version
# Docker Compose version v2.24.5
```

**Instalar si no está:**
```bash
apt update && apt install -y docker.io docker-compose-v2
```

> En este proyecto todos los comandos usan la sintaxis v2: `docker compose` (sin guión).

---

### � Error 502 Bad Gateway en Nginx

**Causa más frecuente:** El contenedor de la app **no está corriendo**. Nginx intenta enviarle la request al puerto 8020 pero nadie escucha allí.

**Diagnóstico paso a paso:**

```bash
# 1. Ver todos los contenedores (incluyendo los caídos)
docker ps -a | grep mail-service
```

Interpretación:

| Resultado | Qué significa |
|---|---|
| Aparece `Up X minutes` | ✅ Está corriendo — problema en Nginx |
| Aparece `Exited (1)` | ❌ Crasheó — revisar logs |
| No aparece | ❌ Nunca levantó o fue removido |

```bash
# 2. Si está caído, ver por qué falló
docker logs mail-service-app

# 3. Verificar que el puerto 8020 esté libre
ss -tlnp | grep 8020
# Si otro proceso lo ocupa, revisar con:
docker ps | grep 8020
```

> ⚠️ **Conflicto de puertos:** Si tenés otros proyectos Java en el servidor (ej: PDV corriendo en 8081), asegurate de que cada servicio use un puerto distinto y que el `APP_PORT` en el `.env` no colisione con ninguno.

**Solución — volver a levantar:**
```bash
cd /var/java-dist/mail-service
docker compose --profile production up -d --build

# Verificar que levantó
docker ps | grep mail-service
# Debe mostrar: 0.0.0.0:8020->8020/tcp

# Verificar health (acceso directo desde servidor, sin pasar por nginx)
curl http://localhost:8020/actuator/health
# {"status":"UP"}

# Recargar Nginx
systemctl reload nginx
```

---

### �🟡 La app arrancó pero no es accesible desde afuera

**Verificar que el contenedor está corriendo:**
```bash
docker ps | grep mail-service
# Debe mostrar "Up X minutes" y el mapeo de puertos:
# 0.0.0.0:8020->8020/tcp
```

**Verificar health desde el servidor (acceso directo, sin nginx):**
```bash
curl http://localhost:8020/mail-service/actuator/health
# {"status":"UP"}
```

> ℹ️ El endpoint `/actuator/health` está bloqueado desde internet (nginx retorna 403). Solo se puede consultar desde el propio servidor incluyendo el context-path.

**Verificar Nginx:**
```bash
nginx -t                  # verificar config sin errores
systemctl reload nginx    # aplicar cambios
```

---

### 🟡 Cambié archivos pero el contenedor sigue igual

**Causa:** Docker reutiliza la imagen cacheada anteriormente.

**Solución:** Forzar la reconstrucción:
```bash
docker compose --profile production up -d --build
```

> El flag `--build` obliga a Docker a leer el `Dockerfile` de nuevo y copiar el JAR actualizado.

---

### 🔵 Cómo verificar logs en tiempo real

```bash
# Todos los servicios
docker compose logs -f

# Solo la app
docker compose logs -f mail-service

# Solo las últimas 50 líneas
docker compose logs --tail=50 mail-service

# Solo postgres
docker compose logs -f postgres
```

---

### 🔵 Cómo reiniciar servicios

```bash
# Reiniciar solo la app (sin reconstruir)
docker compose --profile production restart mail-service

# Reiniciar todo
docker compose --profile production restart

# Detener todo y volver a levantar
docker compose --profile production down
docker compose --profile production up -d
```

---

### 🔵 Cómo ver el estado de los contenedores

```bash
docker ps -a | grep mail-service
```

| Estado | Significado |
|---|---|
| `Up X minutes` | ✅ Corriendo normalmente |
| `Up X seconds` | ⚠️ Recién arrancó, revisar logs |
| `Exited (1)` | ❌ Falló al arrancar, revisar logs |
| `Restarting` | ❌ Crasheando en loop, revisar logs |

---

### 🔵 Cómo entrar directamente a la base de datos

```bash
docker exec -it mail-service-postgres psql -U mailuser -d mailservice
```

Comandos útiles dentro de psql:
```sql
\dt               -- listar todas las tablas
\d mail_logs      -- ver estructura de una tabla
SELECT count(*) FROM mail_logs;
\q                -- salir
```

---

### 🔵 Cómo verificar qué variables de entorno recibió el contenedor

```bash
docker exec mail-service-app env | grep -E "SPRING|MAIL|API|ADMIN|PURGE"
```

---

### 🔵 Cómo verificar qué perfil está activo

Buscá esta línea en los logs al arrancar:
```
The following 1 profile is active: "prod"
```

O desde el servidor:
```bash
docker compose logs mail-service | grep "profile is active"
```

---

### ¿Tengo que renombrar archivos según el entorno?
**No.** Todos los archivos (`application.yml`, `application-dev.yml`, `application-prod.yml`) siempre están en el proyecto. Spring Boot elige cuál cargar según el perfil activo.

### ¿Qué pasa si falta una variable en el `.env`?
La app **no arranca** y el log indica exactamente qué variable falta.

### ¿Puedo cambiar la configuración sin reiniciar?
**Sí**, para los parámetros dinámicos: límite diario, reintentos, templates, cron de depuración y retención de logs. Se gestionan desde el **panel admin** (`/mail-service/admin/settings`).

### ¿Docker crea la base de datos automáticamente?
**Sí.** La imagen de PostgreSQL lee `POSTGRES_DB`, `POSTGRES_USER` y `POSTGRES_PASSWORD` del `.env` y crea la base de datos, el usuario y la contraseña automáticamente en el primer inicio. Las **tablas** las crea la app al levantar (ver primer incidente arriba).

### ¿Cómo verifico que la app está corriendo?
```bash
# Desde el servidor (acceso directo a Spring, incluyendo el context-path)
curl http://localhost:8020/mail-service/actuator/health
# Respuesta: {"status":"UP"}
```

> ℹ️ El actuator está bloqueado desde internet por nginx. No es accesible desde el navegador.

### ¿El `.env` se sube a Git?
**Nunca.** El `.gitignore` lo excluye. Solo se sube `.env.example` como referencia.

### ¿Puedo usar `docker-compose` (con guión) en lugar de `docker compose`?
Solo si tenés la v1 instalada (`apt install docker-compose`). Ambas versiones conviven en el sistema. En este proyecto se usa v2 (`docker compose` sin guión) por ser la versión moderna y recomendada.

