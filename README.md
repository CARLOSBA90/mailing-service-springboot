<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4.13-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Java-17-E11F21?logo=openjdk&logoColor=white" alt="Java 17">
  <img src="https://img.shields.io/badge/PostgreSQL-16-336791?logo=postgresql&logoColor=white" alt="PostgreSQL">
  <img src="https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white" alt="Docker">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT">
</p>

# Mailing Service 

Microservicio **independiente, ligero y listo para producción** que expone una API REST para el envío de correos electrónicos con templates HTML. Incluye **panel de administración web**, **persistencia de logs**, **configuración dinámica** y **autenticación dual** (API Key + HTTP Basic). Diseñado para integrarse con cualquier backend (Spring Boot, Node.js, Django, Laravel, etc.) mediante una simple petición HTTP.

> Pensado como un **building block** reutilizable: lo desplegás una vez y cualquier aplicación de tu ecosistema puede enviar emails a través de él.

---

## Tabla de Contenidos

- [Visión General](#visión-general)
- [Características Principales](#características-principales)
- [Arquitectura](#arquitectura)
- [Stack Tecnológico](#stack-tecnológico)
- [Requisitos Previos](#requisitos-previos)
- [Instalación y Configuración](#instalación-y-configuración)
- [Uso de la API](#-uso-de-la-api)
- [API de Logs y Reintentos](#-api-de-logs-y-reintentos)
- [Panel de Administración](#-panel-de-administración)
- [Autenticación y Seguridad](#-autenticación-y-seguridad)
- [Configuración Dinámica (Settings)](#-configuración-dinámica-settings)
- [Templates Incluidos](#-templates-incluidos)
- [Cómo Crear Templates Personalizados](#-cómo-crear-templates-personalizados)
- [Configuración del Relay SMTP](#-configuración-del-relay-smtp)
- [Configuración DNS para Dominio Propio](#-configuración-dns-para-dominio-propio)
- [Docker](#-docker)
- [Integración con Otros Servicios](#-integración-con-otros-servicios)
- [Health Check](#-health-check)
- [Alcance y Limitaciones](#-alcance-y-limitaciones)
- [FAQ](#-faq)
- [Licencia](#-licencia)

---

## Visión General

En una arquitectura de microservicios o incluso en un monolito bien organizado, el envío de emails es una responsabilidad **transversal** que no debería vivir acoplada a la lógica de negocio. Este microservicio resuelve exactamente ese problema:

- **Desacopla** el envío de correos de tu aplicación principal.
- **Centraliza** templates, configuración SMTP y lógica de reintentos en un solo lugar.
- **Escala** de forma independiente: si necesitás enviar más correos, escalás solo este servicio.
- **Audita** cada email enviado con persistencia en base de datos y un panel de administración.

### ¿Por qué es un microservicio?
 
1. **Responsabilidad única:** Solo envía emails.
2. **Despliegue independiente:** Se construye, despliega y escala por separado.
3. **Comunicación vía red:** Los demás servicios se comunican con él mediante HTTP (API REST).
4. **Base de datos propia:** Usa PostgreSQL para persistencia de logs, configuración dinámica y auditoría.

> Podés tenerlo en la misma máquina, en otro VPS, en un clúster de Kubernetes, o en cualquier lugar con conectividad de red. Mientras la URL y la API Key sean accesibles, funciona.

---

##  Características Principales

| Característica | Descripción |
|---|---|
| **API REST** | Endpoint `POST /api/mail/send` para enviar correos |
| **Templates HTML** | Motor Thymeleaf para emails profesionales y responsive |
| **Procesamiento Async** | Los emails se encolan y procesan en background sin bloquear la respuesta HTTP |
| **Reintentos Automáticos** | Configurable desde Settings (por defecto 3 intentos con backoff exponencial) |
| **Persistencia de Logs** | Cada email se registra en PostgreSQL con estado, intentos y timestamps |
| **Panel de Administración** | Dashboard web con estadísticas, logs paginados, detalle, reintentos y configuración |
| **Autenticación Dual** | API Key para la API REST + HTTP Basic Auth para el Admin UI |
| **Configuración Dinámica** | Parámetros operativos modificables desde el panel sin reiniciar la aplicación |
| **Spring Profiles** | Configuración automática según entorno (`dev` / `prod`) |
| **Rate Limiting** | Protección contra abuso (30 req/min por IP) |
| **Security Headers** | X-Content-Type-Options, X-Frame-Options, Cache-Control |
| **Path Traversal Protection** | Whitelist dinámica de templates permitidos |
| **Health Check** | Endpoint `/actuator/health` para monitoreo y balanceadores de carga |
| **Dockerizado** | Imagen ligera con usuario no-root basada en `eclipse-temurin:17-jre-alpine` |
| **Validación** | Validación automática del request (email, campos obligatorios) |
| **Manejo de Errores** | Respuestas consistentes con `GlobalExceptionHandler` + página de error personalizada |

---

## Arquitectura

```
┌──────────────────────┐         HTTP POST           ┌──────────────────────────────────────┐
│                      │    (JSON + X-API-Key)        │         MAILING SERVICE              │
│  Tu Backend          │ ───────────────────────────► │                                      │
│  (cualquier lenguaje)│                              │  ┌──────────────────┐                │
│                      │ ◄─── 202 Accepted ────────── │  │  MailController   │                │
└──────────────────────┘                              │  └────────┬─────────┘                │
                                                      │           │                          │
┌──────────────────────┐       HTTP Basic Auth         │  ┌────────▼─────────┐                │
│                      │ ───────────────────────────► │  │  SecurityConfig   │                │
│  Admin (Browser)     │                              │  │  (Dual Chain)     │                │
│  /admin/*            │ ◄─── HTML Views ──────────── │  │  • API Key (REST) │                │
└──────────────────────┘                              │  │  • Basic (Admin)  │                │
                                                      │  └────────┬─────────┘                │
                                                      │           │                          │
                                                      │  ┌────────▼─────────┐                │
                                                      │  │  MailServiceImpl  │                │
                                                      │  │  (Async + Retry)  │                │
                                                      │  └──┬──────────┬────┘                │
                                                      │     │          │                     │
                                                      │  ┌──▼───┐  ┌──▼──────────┐           │
                                                      │  │Thyme-│  │MailLog      │           │     ┌─────────────┐
                                                      │  │leaf  │  │Repository   │───────────│────►│ PostgreSQL  │
                                                      │  │(HTML)│  │(Auditoría)  │           │     │ (Logs, Cfg) │
                                                      │  └──────┘  └─────────────┘           │     └─────────────┘
                                                      │                                      │
                                                      │  ┌──────────────────────┐            │     ┌─────────────┐
                                                      │  │  ConfigService       │            │────►│  SMTP Relay │
                                                      │  │  (Settings Cache)    │            │     │  (Brevo,    │
                                                      │  └──────────────────────┘            │     │  SendGrid…) │
                                                      └──────────────────────────────────────┘     └─────────────┘
```

### Flujo de un Email

1. Tu backend envía un `POST /api/mail/send` con el header `X-API-Key`.
2. El `SecurityConfig` (cadena API REST) valida la API Key con comparación constant-time.
3. El `MailController` valida el body del request. Si falla → `400 Bad Request`.
4. `MailServiceImpl` verifica que el servicio esté habilitado y el límite diario no se haya alcanzado.
5. Se crea un registro `MailLog` con estado `PENDING` en PostgreSQL.
6. El request se acepta inmediatamente con `202 Accepted` (non-blocking).
7. Thymeleaf renderiza el template HTML (validando contra la whitelist de templates permitidos).
8. El email se envía a través del relay SMTP con reintentos automáticos (configurable).
9. El `MailLog` se actualiza a `SENT` o `FAILED` según el resultado.

---

##  Stack Tecnológico

| Tecnología | Propósito |
|---|---|
| **Spring Boot 3.4** | Framework base |
| **Spring Mail** | Envío de correos vía SMTP |
| **Thymeleaf** | Motor de templates HTML (emails + Admin UI) |
| **Spring Security** | Autenticación dual: API Key + HTTP Basic |
| **Spring Data JPA** | Persistencia de logs y configuración dinámica |
| **PostgreSQL 16** | Base de datos para auditoría y settings |
| **HikariCP** | Connection pool de alto rendimiento |
| **Spring Actuator** | Health checks y monitoreo |
| **Lombok** | Reducción de boilerplate |
| **Docker + Compose** | Contenerización y orquestación |
| **Java 17** | Runtime |

---

## 🔐 Seguridad y Perfiles de Entorno

Este servicio implementa **Spring Profiles** para separar la configuración de desarrollo y producción de forma automática:

### Perfiles Disponibles

| Perfil | Cuándo se activa | API Key | Seguridad |
|---|---|---|---|
| **`dev`** | Por defecto (desarrollo local) | Fallback: `dev-api-key-change-me` | Permite arrancar sin configuración |
| **`prod`** | Docker (automático) o `SPRING_PROFILES_ACTIVE=prod` | **Obligatoria** vía `$API_KEY` | Falla si no está configurada o usa valor de dev |

### ¿Cómo funciona?

Spring Boot carga automáticamente los archivos de configuración según el perfil activo:

```
1. application.yml          ← SIEMPRE se carga (base)
2. application-dev.yml      ← Se carga SOLO si perfil = dev (credentials de desarrollo)
3. application-prod.yml     ← Se carga SOLO si perfil = prod (sobreescribe valores)
```

**En desarrollo:**
- Perfil `dev` activo por defecto
- API Key tiene fallback seguro para testing
- Admin UI: `admin` / `admin123` (por defecto)
- JPA: `ddl-auto: update` (auto-crea/actualiza tablas)
- Logs en nivel `INFO`

**En producción (Docker):**
- Perfil `prod` forzado automáticamente en el `Dockerfile`
- API Key **obligatoria** — la app no arranca sin ella
- Admin UI: credenciales leídas de variables de entorno (`ADMIN_USERNAME`, `ADMIN_PASSWORD`)
- JPA: `ddl-auto: validate` (no modifica el schema)
- SMTP requiere autenticación y TLS
- Logs en nivel `WARN`
- Usuario no-root en el contenedor

> **📖 Para más detalles sobre deploy**, consulta [`DEPLOY.md`](DEPLOY.md)

---

##  Requisitos Previos

- **Java 17+** (para desarrollo local)
- **Maven 3.8+** (para compilar)
- **PostgreSQL 16+** (o usar el contenedor de Docker Compose)
- **Docker + Docker Compose** (recomendado para desarrollo y producción)
- **Un servidor SMTP** (Brevo, SendGrid, Postfix local, Amazon SES, Mailgun, etc.)

---

##  Instalación y Configuración

### 1. Clonar el repositorio

```bash
git clone https://github.com/tu-usuario/mailing-service-springboot.git
cd mailing-service-springboot
```

### 2. Levantar PostgreSQL (desarrollo)

```bash
# Levanta solo la base de datos con Docker Compose
docker compose up -d postgres
```

Esto crea una instancia de PostgreSQL con:
- **DB:** `mailservice`
- **User:** `mailuser`
- **Password:** `mailpass123`
- **Puerto:** `5432`

### 3. Configurar variables de entorno

**Para desarrollo local:** No necesitas configurar nada extra. El perfil `dev` usa valores por defecto seguros. Solo asegúrate de tener PostgreSQL corriendo (paso 2).

**Para producción:** Configura estas variables de entorno:

| Variable | Descripción | Default (Dev) | Requerida en Prod |
|---|---|---|---|
| `API_KEY` | Clave API para autenticar requests REST | `dev-api-key-change-me` | ✅ **Sí** |
| `ADMIN_USERNAME` | Usuario del panel de administración | `admin` | ✅ Sí |
| `ADMIN_PASSWORD` | Contraseña del panel de administración | `admin123` | ✅ Sí |
| `SPRING_DATASOURCE_URL` | URL de conexión a PostgreSQL | `jdbc:postgresql://localhost:5432/mailservice` | ✅ Sí |
| `SPRING_DATASOURCE_USERNAME` | Usuario de la base de datos | `mailuser` | ✅ Sí |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de la base de datos | `mailpass123` | ✅ Sí |
| `SPRING_MAIL_HOST` | Host del servidor SMTP | `localhost` | ✅ Sí |
| `SPRING_MAIL_PORT` | Puerto SMTP | `25` | ✅ Sí |
| `SPRING_MAIL_USERNAME` | Usuario SMTP (si aplica) | _(vacío)_ | ✅ Sí |
| `SPRING_MAIL_PASSWORD` | Contraseña SMTP (si aplica) | _(vacío)_ | ✅ Sí |
| `MAIL_FROM` | Email del remitente | `no-reply@tudominio.com` | Recomendado |
| `MAIL_FROM_NAME` | Nombre visible del remitente | `Mi Tienda` | Opcional |

> **🔑 Generar API Key segura:**
> ```powershell
> ./generate-api-key.ps1
> ```
> Copia el resultado y úsalo como valor de `API_KEY` en producción.

> [!CAUTION]
> **Nunca uses `dev-api-key-change-me` en producción.** La aplicación detecta este valor en perfil `prod` y se niega a arrancar.

### 4. Compilar y ejecutar

```bash
# Compilar
mvn clean package -DskipTests

# Ejecutar (perfil dev por defecto)
java -jar target/mail-service-1.0.0-SNAPSHOT.jar
```

El servicio se levanta en **http://localhost:8081**.  
El panel de administración estará disponible en **http://localhost:8081/admin**.

---

##  Uso de la API

### Enviar un email

```
POST /api/mail/send
```

**Headers:**
```
Content-Type: application/json
X-API-Key: tu-api-key-secreta
```

**Body:**
```json
{
  "to": "destinatario@ejemplo.com",
  "subject": "Asunto del correo",
  "template": "welcome",
  "variables": {
    "customerName": "Juan Pérez",
    "storeUrl": "https://mi-tienda.com"
  }
}
```

**Respuesta exitosa** (`202 Accepted`):
```json
{
  "success": true,
  "message": "Email encolado para envío"
}
```

**Error de validación** (`400 Bad Request`):
```json
{
  "success": false,
  "message": "to: El destinatario es obligatorio, subject: El asunto es obligatorio"
}
```

**Template no permitido** (`400 Bad Request`):
```json
{
  "success": false,
  "message": "Template no permitido: 'mi-template'. Templates válidos: [welcome, password-reset, order-confirmation]"
}
```

**API Key inválida** (`401 Unauthorized`):
```json
{
  "error": "API Key inválida o ausente"
}
```

**Servicio deshabilitado** (`409 Conflict`):
```json
{
  "success": false,
  "message": "El servicio de envío está temporalmente deshabilitado."
}
```

**Límite diario alcanzado** (`409 Conflict`):
```json
{
  "success": false,
  "message": "Límite diario de envíos alcanzado (300/300)."
}
```

### Campos del Request

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `to` | `String` | ✅ | Email del destinatario (se valida formato) |
| `subject` | `String` | ✅ | Asunto del correo |
| `template` | `String` | ✅ | Nombre del template (debe estar en la whitelist de Settings) |
| `variables` | `Map<String, Object>` | ❌ | Variables dinámicas para inyectar en el template |

---

## 📋 API de Logs y Reintentos

El servicio expone una API REST para consultar logs de emails y gestionar reintentos. Todos los endpoints requieren autenticación con `X-API-Key`.

### Listar logs (paginado + filtros)

```
GET /api/mail/logs?page=0&size=20&status=FAILED&recipient=test@mail.com
```

| Parámetro | Tipo | Default | Descripción |
|---|---|---|---|
| `page` | `int` | `0` | Número de página (0-indexed) |
| `size` | `int` | `20` | Resultados por página |
| `status` | `MailStatus` | — | Filtrar por estado: `PENDING`, `SENT`, `FAILED`, `RETRYING` |
| `recipient` | `String` | — | Filtrar por destinatario (búsqueda parcial, case-insensitive) |

**Respuesta:**
```json
{
  "content": [
    {
      "id": "a1b2c3d4-...",
      "recipient": "user@example.com",
      "subject": "Bienvenido",
      "templateName": "welcome",
      "status": "SENT",
      "attempts": 1,
      "errorMessage": null,
      "variables": { "customerName": "Juan" },
      "createdAt": "2026-02-21T10:30:00",
      "sentAt": "2026-02-21T10:30:02",
      "lastRetryAt": null
    }
  ],
  "totalElements": 150,
  "totalPages": 8,
  "number": 0
}
```

### Obtener un log por ID

```
GET /api/mail/logs/{id}
```

### Obtener estadísticas

```
GET /api/mail/logs/stats
```

**Respuesta:**
```json
{
  "totalEmails": 1250,
  "pending": 3,
  "sent": 1200,
  "failed": 42,
  "retrying": 5,
  "sentToday": 45,
  "failedToday": 2
}
```

### Reintentar un email fallido

```
POST /api/mail/logs/{id}/retry
```

**Respuesta exitosa** (`202 Accepted`):
```json
{
  "message": "Reintento encolado exitosamente",
  "logId": "a1b2c3d4-..."
}
```

### Reintentar todos los fallidos (batch)

```
POST /api/mail/logs/retry-failed
```

Solo reintenta emails con estado `FAILED` y menos de 5 intentos totales.

**Respuesta:**
```json
{
  "message": "Reintento batch encolado",
  "count": 12
}
```

### Estados de un Email (`MailStatus`)

| Estado | Descripción |
|---|---|
| `PENDING` | Recibido, en cola para envío |
| `SENT` | Enviado exitosamente al relay SMTP |
| `FAILED` | Falló después de agotar todos los reintentos |
| `RETRYING` | En proceso de reintento (estado intermedio) |

---

## 🖥️ Panel de Administración

El servicio incluye un **panel de administración web** completo, construido con Thymeleaf y protegido por HTTP Basic Auth.

### Acceso

```
URL: http://localhost:8081/admin
Credenciales (dev): admin / admin123
```

### Páginas del Panel

| Ruta | Descripción |
|---|---|
| `/admin/login` | Página de inicio de sesión (formulario web) |
| `/admin` | **Dashboard** — Estadísticas generales: total de emails, enviados, fallidos, pendientes, reintentos, enviados hoy, últimos 5 emails |
| `/admin/logs` | **Logs** — Lista paginada de todos los emails con filtros por estado, destinatario y rango de fechas |
| `/admin/logs/{id}` | **Detalle de Log** — Información completa de un email específico (variables, error, timestamps) |
| `/admin/settings` | **Configuración** — Parámetros operativos del sistema modificables en tiempo real |

### Funcionalidades del Panel

- **📊 Dashboard:** Vista rápida con contadores de emails por estado, emails del día y tabla con los últimos 5 registros.
- **📋 Logs paginados:** Navegación paginada con filtros combinables (estado, destinatario, rango de fechas). Tamaño de página configurable (1 a 100).
- **🔍 Detalle de Log:** Vista completa de un email individual incluyendo variables enviadas, mensaje de error (si falló), y todos los timestamps.
- **🔄 Reintentos manuales:** Botón para reintentar un email fallido específico, o reintentar **todos** los fallidos en batch desde la UI.
- **⚙️ Settings:** Modificar parámetros operativos como límite diario, habilitación del servicio, reintentos máximos, etc.
- **📊 Indicador de límite diario:** Barra visual de progreso mostrando el uso diario vs. el límite configurado.

---

## 🔒 Autenticación y Seguridad

El servicio implementa **dos cadenas de seguridad separadas** mediante Spring Security `@Order`:

### Cadena 1: Admin UI (`/admin/**`) — HTTP Basic + Sesiones

```
Tipo:         Form Login (formulario web)
Ruta:         /admin/**
Autenticación: Usuario + Contraseña (In-Memory)
Sesiones:     IF_REQUIRED (stateful)
CSRF:         Habilitado (protección por defecto)
```

| Config | Dev | Prod |
|---|---|---|
| Usuario | `admin` | `$ADMIN_USERNAME` (variable de entorno) |
| Contraseña | `admin123` | `$ADMIN_PASSWORD` (variable de entorno) |
| Encoder | BCrypt | BCrypt |

**Endpoints públicos del Admin:**
- `/admin/login` — Página de login
- `/css/**`, `/js/**` — Recursos estáticos

### Cadena 2: API REST (`/**`) — Stateless + API Key

```
Tipo:         API Key via Header
Header:       X-API-Key
Autenticación: Comparación constant-time (timing-attack safe)
Sesiones:     STATELESS
CSRF:         Deshabilitado (API REST sin estado)
Rate Limiting: 30 requests/min por IP
```

**Endpoints públicos de la API:**
- `/actuator/health` — Health check
- `/favicon.ico` — Favicon
- `/error` — Página de error personalizada

### Seguridad Implementada

| Medida | Descripción |
|---|---|
| **Comparación constant-time** | `MessageDigest.isEqual()` para prevenir timing attacks en la API Key |
| **Rate Limiting** | Ventana fija de 1 minuto, máximo 30 requests por IP |
| **Security Headers** | `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Cache-Control` |
| **BCrypt** | Encoding de contraseñas del Admin UI |
| **Path Traversal Protection** | Whitelist dinámica de templates (configurable desde Settings) |
| **Validación de API Key en prod** | La app no arranca si `API_KEY` no está configurada o usa el valor de dev |

---

## ⚙️ Configuración Dinámica (Settings)

El servicio permite modificar parámetros operativos **sin reiniciar la aplicación**. Los valores se persisten en PostgreSQL y se cachean en memoria (`ConcurrentHashMap`) para máximo rendimiento.

### Parámetros Configurables

| Clave | Tipo | Default | Descripción |
|---|---|---|---|
| `daily_send_limit` | `INTEGER` | `300` | Límite máximo de emails por día (00:00 a 23:59) |
| `service_enabled` | `BOOLEAN` | `true` | Habilitar/deshabilitar el envío de emails globalmente |
| `max_retry_attempts` | `INTEGER` | `3` | Número máximo de reintentos por email fallido |
| `retry_cooldown_ms` | `INTEGER` | `2000` | Delay inicial en ms entre reintentos (backoff exponencial: 2s → 4s → 8s) |
| `allowed_templates` | `STRING` | `welcome,password-reset,order-confirmation` | Templates habilitados para envío (separados por coma) |

### Cómo funciona

1. Al arranque, `ConfigServiceImpl` inicializa los valores por defecto en la tabla `service_config` si no existen.
2. Cada lectura primero consulta la **caché en memoria**. Si no hay hit, lee de DB y cachea.
3. Al actualizar un valor (desde Admin UI o código), se **invalida la caché** para esa clave.
4. `MailServiceImpl` lee estos valores en cada envío — por ejemplo, verifica `service_enabled` y `daily_send_limit` antes de procesar.

### Modificar desde el Admin UI

1. Ir a `/admin/settings`
2. Modificar los valores deseados
3. Guardar — los cambios aplican **inmediatamente** (sin reinicio)

### Whitelist de claves

Solo se permiten las claves conocidas para evitar inyección arbitraria de configuración. El endpoint `POST /admin/settings` valida contra una whitelist de claves permitidas.

### Modelo de datos

```sql
-- Tabla: service_config
CREATE TABLE service_config (
    config_key   VARCHAR(100) PRIMARY KEY,
    config_value VARCHAR(1000) NOT NULL,
    description  VARCHAR(255)  NOT NULL,
    config_type  VARCHAR(20)   NOT NULL,  -- INTEGER, BOOLEAN, STRING
    updated_at   TIMESTAMP
);
```

---

##  Templates Incluidos

El servicio incluye **3 templates base** listos para usar. Son completamente responsive y compatibles con los principales clientes de email (Gmail, Outlook, Apple Mail, etc.).

### 1. `welcome` — Bienvenida

Email de bienvenida para nuevos usuarios registrados.

| Variable | Descripción |
|---|---|
| `customerName` | Nombre del usuario |
| `storeUrl` | URL del sitio |

```json
{
  "to": "usuario@ejemplo.com",
  "subject": "¡Bienvenido!",
  "template": "welcome",
  "variables": {
    "customerName": "María García",
    "storeUrl": "https://mi-app.com"
  }
}
```

### 2. `password-reset` — Restablecimiento de Contraseña

Email con enlace para restablecer la contraseña.

| Variable | Descripción |
|---|---|
| `customerName` | Nombre del usuario |
| `resetUrl` | URL del enlace de reseteo |
| `expirationMinutes` | Minutos de validez del enlace |

```json
{
  "to": "usuario@ejemplo.com",
  "subject": "Restablecer tu contraseña",
  "template": "password-reset",
  "variables": {
    "customerName": "María García",
    "resetUrl": "https://mi-app.com/reset?token=abc123",
    "expirationMinutes": 30
  }
}
```

### 3. `order-confirmation` — Confirmación de Pedido

Email con resumen detallado de un pedido (tabla de productos con cantidades y precios).

| Variable | Descripción |
|---|---|
| `customerName` | Nombre del cliente |
| `orderId` | Identificador del pedido |
| `items` | Lista de objetos con `name`, `quantity` y `price` |
| `total` | Total formateado |

```json
{
  "to": "cliente@ejemplo.com",
  "subject": "Pedido #1234 confirmado",
  "template": "order-confirmation",
  "variables": {
    "customerName": "Juan Pérez",
    "orderId": "1234",
    "items": [
      { "name": "Producto A", "quantity": 2, "price": "$50.00" },
      { "name": "Producto B", "quantity": 1, "price": "$30.00" }
    ],
    "total": "$130.00"
  }
}
```

---

##  Cómo Crear Templates Personalizados

1. Creá un archivo HTML en `src/main/resources/templates/mail/`:

```
src/main/resources/templates/mail/mi-template.html
```

2. Usá la sintaxis de Thymeleaf para variables dinámicas:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="es">
<head>
    <meta charset="UTF-8">
    <title>Mi Template</title>
</head>
<body>
    <h1>Hola <span th:text="${nombre}">Usuario</span></h1>
    <p th:text="${mensaje}">Contenido del mensaje</p>
</body>
</html>
```

3. **Registrá el template** en la whitelist de Settings:
   - Ir a `/admin/settings`
   - En `allowed_templates`, agregar el nombre separado por coma: `welcome,password-reset,order-confirmation,mi-template`
   - Guardar

4. Enviá un request referenciando tu template:

```json
{
  "to": "destino@ejemplo.com",
  "subject": "Mi asunto",
  "template": "mi-template",
  "variables": {
    "nombre": "Carlos",
    "mensaje": "Este es un mensaje personalizado"
  }
}
```

> [!TIP]
> Los templates de email deben usar **tablas HTML** para layout (no Flexbox/Grid) para máxima compatibilidad con clientes de correo como Outlook.

> [!IMPORTANT]
> Si un template no está en la whitelist de `allowed_templates`, el servicio lo rechazará con un `400 Bad Request`.

---

##  Configuración del Relay SMTP

Este servicio es **agnóstico del proveedor SMTP**. Podés usar cualquiera de estos:

### Opción A: Brevo (recomendado como servicio gratuito)

[Brevo](https://www.brevo.com) ofrece un tier gratuito de 300 emails/día. Se puede usar como API o como relay SMTP.

```bash
SPRING_MAIL_HOST=smtp-relay.brevo.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=tu-login@brevo.com
SPRING_MAIL_PASSWORD=tu-smtp-key
SPRING_MAIL_AUTH=true
SPRING_MAIL_STARTTLS=true
```

### Opción B: Postfix local (Dockerizado)

Si tenés un contenedor Postfix configurado como relay:

```bash
SPRING_MAIL_HOST=postfix       # nombre del servicio en Docker network
SPRING_MAIL_PORT=25
SPRING_MAIL_AUTH=false
SPRING_MAIL_STARTTLS=false
```

### Opción C: Otros proveedores

| Proveedor | Host | Puerto |
|---|---|---|
| **SendGrid** | `smtp.sendgrid.net` | `587` |
| **Amazon SES** | `email-smtp.us-east-1.amazonaws.com` | `587` |
| **Mailgun** | `smtp.mailgun.org` | `587` |
| **Gmail** (dev only) | `smtp.gmail.com` | `587` |

---

## 🌐 Configuración DNS para Dominio Propio

Para que los emails lleguen **sin caer en SPAM** y muestren tu dominio como remitente verificado, necesitás configurar registros DNS:

### 1. Registro SPF (Tipo TXT)

| Campo | Valor |
|---|---|
| **Nombre** | `@` |
| **Tipo** | `TXT` |
| **Valor** | `v=spf1 include:spf.brevo.com ~all` |

> Si ya tenés un registro SPF existente, solo agregá `include:spf.brevo.com` antes de `~all`.

### 2. Registro DKIM (Tipo TXT)

| Campo | Valor |
|---|---|
| **Nombre** | `mail._domainkey` (o el que indique tu proveedor) |
| **Tipo** | `TXT` |
| **Valor** | _(La cadena proporcionada por tu proveedor SMTP)_ |

### 3. Verificación

Después de agregar los registros DNS, verificalos desde el panel de tu proveedor SMTP. Los cambios DNS pueden demorar hasta **48 horas** en propagarse, aunque usualmente toman minutos.

> [!IMPORTANT]
> Sin SPF y DKIM configurados, los emails pueden llegar a la carpeta de spam o mostrar advertencias como _"enviado a través de brevo.com"_ en lugar de tu dominio.

---

## 🐳 Docker

### Docker Compose (Desarrollo)

El proyecto incluye un `docker-compose.yml` pre-configurado con PostgreSQL:

```bash
# Levantar solo la base de datos (para desarrollo local)
docker compose up -d postgres

# Levantar todo (incluyendo la app, perfil producción)
docker compose --profile production up -d
```

### Construir la imagen

```bash
mvn clean package -DskipTests
docker build -t mail-service:latest .
```

### Ejecutar el contenedor

```bash
docker run -d \
  --name mail-service \
  -p 8081:8081 \
  -e API_KEY=mi-clave-super-segura-123 \
  -e ADMIN_USERNAME=mi-admin \
  -e ADMIN_PASSWORD=mi-password-segura \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/mailservice \
  -e SPRING_DATASOURCE_USERNAME=mailuser \
  -e SPRING_DATASOURCE_PASSWORD=mailpass123 \
  -e SPRING_MAIL_HOST=smtp-relay.brevo.com \
  -e SPRING_MAIL_PORT=587 \
  -e SPRING_MAIL_USERNAME=tu-login@brevo.com \
  -e SPRING_MAIL_PASSWORD=tu-smtp-key \
  -e SPRING_MAIL_AUTH=true \
  -e SPRING_MAIL_STARTTLS=true \
  -e MAIL_FROM=no-reply@tudominio.com \
  -e MAIL_FROM_NAME="Mi Aplicación" \
  mail-service:latest
```

### Docker Compose (Producción completo)

```yaml
version: "3.8"

services:
  postgres:
    image: postgres:16-alpine
    container_name: mail-service-postgres
    environment:
      POSTGRES_DB: mailservice
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d mailservice"]
      interval: 10s
      timeout: 5s
      retries: 5

  mail-service:
    build: .
    container_name: mail-service-app
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/mailservice
      SPRING_DATASOURCE_USERNAME: ${DB_USER}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      API_KEY: ${API_KEY}
      ADMIN_USERNAME: ${ADMIN_USERNAME}
      ADMIN_PASSWORD: ${ADMIN_PASSWORD}
      SPRING_MAIL_HOST: ${SMTP_HOST}
      SPRING_MAIL_PORT: ${SMTP_PORT}
      SPRING_MAIL_USERNAME: ${SMTP_USERNAME}
      SPRING_MAIL_PASSWORD: ${SMTP_PASSWORD}
      SPRING_MAIL_AUTH: "true"
      SPRING_MAIL_STARTTLS: "true"
      MAIL_FROM: ${MAIL_FROM}
      MAIL_FROM_NAME: ${MAIL_FROM_NAME}
    ports:
      - "8081:8081"
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  postgres-data:
    driver: local
```

---

## 🔌 Integración con Otros Servicios

El microservicio se consume mediante una petición HTTP estándar desde **cualquier lenguaje o framework**:

### Spring Boot / Java (RestTemplate)

```java
RestTemplate restTemplate = new RestTemplate();

HttpHeaders headers = new HttpHeaders();
headers.set("X-API-Key", "tu-api-key");
headers.setContentType(MediaType.APPLICATION_JSON);

Map<String, Object> body = Map.of(
    "to", "usuario@ejemplo.com",
    "subject", "Bienvenido",
    "template", "welcome",
    "variables", Map.of("customerName", "Juan", "storeUrl", "https://mi-app.com")
);

HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
restTemplate.postForEntity("http://mail-service:8081/api/mail/send", request, String.class);
```

### Node.js (fetch)

```javascript
await fetch("http://mail-service:8081/api/mail/send", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "X-API-Key": "tu-api-key",
  },
  body: JSON.stringify({
    to: "usuario@ejemplo.com",
    subject: "Bienvenido",
    template: "welcome",
    variables: { customerName: "Juan", storeUrl: "https://mi-app.com" },
  }),
});
```

### Python (requests)

```python
import requests

requests.post(
    "http://mail-service:8081/api/mail/send",
    headers={"X-API-Key": "tu-api-key"},
    json={
        "to": "usuario@ejemplo.com",
        "subject": "Bienvenido",
        "template": "welcome",
        "variables": {"customerName": "Juan", "storeUrl": "https://mi-app.com"}
    }
)
```

### cURL

```bash
curl -X POST http://localhost:8081/api/mail/send \
  -H "Content-Type: application/json" \
  -H "X-API-Key: tu-api-key" \
  -d '{
    "to": "usuario@ejemplo.com",
    "subject": "Bienvenido",
    "template": "welcome",
    "variables": {
      "customerName": "Juan",
      "storeUrl": "https://mi-app.com"
    }
  }'
```

---

## 🩺 Health Check

El endpoint de salud está disponible **sin autenticación**:

```
GET /actuator/health
```

```json
{
  "status": "UP"
}
```

Útil para:
- Verificación en Docker (`healthcheck`)
- Load balancers (AWS ALB, Nginx upstream checks)
- Monitoreo (Uptime Kuma, Prometheus, etc.)

---

##  Alcance y Limitaciones

### ✅ Qué hace este servicio

- Enviar emails transaccionales (bienvenida, reseteo, confirmaciones, notificaciones).
- Renderizar templates HTML dinámicos con variables.
- Procesar envíos de forma asíncrona y con reintentos configurables.
- Proteger el acceso mediante API Key y HTTP Basic Auth.
- Persistir logs de cada envío en PostgreSQL para auditoría.
- Permitir reintentos manuales (individuales o batch) desde UI y API REST.
- Configurar parámetros operativos dinámicamente sin reinicio.

### 🔄 Posibles Upgrades

Este servicio está diseñado como una base sólida. Algunas mejoras que podrían implementarse en el futuro:

- **Colas avanzadas:** Integración con RabbitMQ o Kafka para mayor resiliencia y escalabilidad.
- **Email marketing:** Soporte para envíos masivos, segmentación de audiencias y campañas programadas.
- **Métricas avanzadas:** Integración con Prometheus/Grafana para monitoreo detallado.
- **Webhooks:** Notificaciones de eventos (email enviado, rebotado, abierto, click en links).
- **Adjuntos:** Soporte para archivos adjuntos (PDFs, imágenes, etc.).
- **Multi-tenant:** Soporte para múltiples API Keys con permisos diferenciados.

###  Finalidad

Servir como **servicio base de mailing** para cualquier proyecto que necesite enviar correos electrónicos de forma desacoplada, sin importar el lenguaje o framework del backend principal. 

---

## FAQ

<details>
<summary><strong>¿Puedo usar este servicio sin Docker?</strong></summary>

Sí. Compilá con Maven y ejecutá el JAR directamente. Solo necesitás PostgreSQL corriendo (puede ser local o remoto):

```bash
mvn clean package -DskipTests
java -jar target/mail-service-1.0.0-SNAPSHOT.jar
```

Configurá las variables de entorno en tu sistema operativo o pasalas como argumentos:

```bash
java -jar target/mail-service-1.0.0-SNAPSHOT.jar --spring.mail.host=smtp.ejemplo.com
```
</details>

<details>
<summary><strong>¿Necesita estar en la misma red Docker que mi backend?</strong></summary>

**No.** Si están en redes diferentes, simplemente usá la IP o dominio público del servicio. Si están en la misma red Docker, podés usar el nombre del servicio (ej: `http://mail-service:8081`).
</details>

<details>
<summary><strong>¿Puedo usar Brevo (u otro servicio) como relay SMTP base?</strong></summary>

**Sí, es la configuración recomendada.** Brevo ofrece un tier gratuito de 300 emails/día. Solo necesitás configurar las variables `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME` y `SPRING_MAIL_PASSWORD` con los datos que Brevo te proporciona. También es compatible con SendGrid, Amazon SES, Mailgun y cualquier servidor SMTP estándar.
</details>

<details>
<summary><strong>¿Cómo envío correos desde mi propio dominio?</strong></summary>

1. Configurá los registros DNS (SPF y DKIM) como se indica en la sección [Configuración DNS](#-configuración-dns-para-dominio-propio).
2. Verificá el dominio en tu proveedor SMTP (Brevo, SendGrid, etc.).
3. Configurá `MAIL_FROM=no-reply@tudominio.com`.

Los emails llegarán firmados por tu dominio, sin advertencias de terceros.
</details>

<details>
<summary><strong>¿Qué pasa si el envío de un email falla?</strong></summary>

El servicio reintenta automáticamente según la configuración de Settings (por defecto **3 intentos** con backoff exponencial):
- 1er reintento: 2 segundos después.
- 2do reintento: 4 segundos después.
- 3er reintento: 8 segundos después.

Si los 3 intentos fallan, el `MailLog` queda con estado `FAILED`. Podés reintentarlo manualmente desde el Admin UI o vía la API REST (`POST /api/mail/logs/{id}/retry`).
</details>

<details>
<summary><strong>¿Cuántos emails puede procesar simultáneamente?</strong></summary>

El thread pool está configurado con:
- **2 workers** base (core pool).
- **5 workers** máximo (bajo carga).
- **100 emails** en cola de espera.

Estos valores son configurables en `AsyncConfig.java`.
</details>

<details>
<summary><strong>¿Es seguro exponer este servicio a internet?</strong></summary>

El servicio está protegido por API Key (REST) y HTTP Basic (Admin), pero se recomienda:
- **No exponerlo directamente a internet.** Ponerlo detrás de un reverse proxy (Nginx, Traefik).
- **Usar una API Key robusta** (mínimo 32 caracteres, alfanumérica random).
- **Usar credenciales de admin seguras** (no las de desarrollo).
- **Limitar el acceso por IP** desde el firewall o el reverse proxy.
</details>

<details>
<summary><strong>¿Puedo agregar mis propios templates?</strong></summary>

Sí. Creá un archivo `.html` en `src/main/resources/templates/mail/` usando sintaxis Thymeleaf, y luego **registralo en la whitelist** desde Settings (`/admin/settings` → `allowed_templates`). Consultá la sección [Cómo Crear Templates Personalizados](#-cómo-crear-templates-personalizados).
</details>

<details>
<summary><strong>¿Funciona con Gmail para desarrollo?</strong></summary>

Sí, pero Gmail requiere una **contraseña de aplicación** (no tu contraseña normal). Es útil para desarrollo pero no se recomienda para producción por los límites de envío.

```bash
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=tu-email@gmail.com
SPRING_MAIL_PASSWORD=xxxx-xxxx-xxxx-xxxx   # App password
SPRING_MAIL_AUTH=true
SPRING_MAIL_STARTTLS=true
```
</details>

<details>
<summary><strong>¿Cómo accedo al panel de administración?</strong></summary>

Navegá a `http://localhost:8081/admin/login` e ingresá las credenciales.

- **Dev:** `admin` / `admin123`
- **Prod:** Las que configures en `ADMIN_USERNAME` y `ADMIN_PASSWORD`

Desde ahí podés ver estadísticas, gestionar logs, reintentar emails y modificar la configuración.
</details>

<details>
<summary><strong>¿Los cambios en Settings requieren reinicio?</strong></summary>

**No.** Los cambios en Settings aplican inmediatamente. Los valores se guardan en PostgreSQL y la caché en memoria se invalida automáticamente al actualizar.
</details>

---

## 📁 Estructura del Proyecto

```
src/main/java/com/mailservice/
├── MailServiceApplication.java          # Entry point + @EnableRetry
├── config/
│   ├── AsyncConfig.java                 # ThreadPool para workers async
│   └── SecurityConfig.java              # Dual security: API Key + HTTP Basic
├── controller/
│   ├── AdminController.java             # Panel admin: dashboard, logs, settings
│   ├── CustomErrorController.java       # Página de error personalizada
│   ├── MailController.java              # Endpoint REST /api/mail/send
│   └── MailLogController.java           # API REST de logs y reintentos
├── dto/
│   ├── MailRequest.java                 # Request body con validaciones
│   └── MailResponse.java               # Respuesta estandarizada
├── entity/
│   ├── MailLog.java                     # Entidad JPA de auditoría de emails
│   ├── MailStatus.java                  # Enum: PENDING, SENT, FAILED, RETRYING
│   └── ServiceConfig.java              # Entidad de configuración dinámica
├── exception/
│   └── GlobalExceptionHandler.java      # Manejo centralizado de errores
├── repository/
│   ├── MailLogRepository.java           # Queries de logs con filtros y stats
│   └── ServiceConfigRepository.java    # CRUD de configuración
└── service/
    ├── ConfigService.java               # Interfaz de configuración dinámica
    ├── ConfigServiceImpl.java           # Implementación con caché en memoria
    ├── MailService.java                 # Interfaz del servicio de mail
    └── MailServiceImpl.java             # Implementación async + retry + logs

src/main/resources/
├── application.yml                      # Configuración base
├── application-dev.yml                  # Configuración de desarrollo
├── application-prod.yml                 # Configuración de producción
├── templates/
│   ├── admin/
│   │   ├── dashboard.html               # Dashboard de administración
│   │   ├── login.html                   # Página de login
│   │   ├── logs.html                    # Lista de logs paginada
│   │   ├── log-detail.html              # Detalle de un email
│   │   └── settings.html               # Configuración dinámica
│   ├── error.html                       # Página de error personalizada
│   └── mail/
│       ├── welcome.html                 # Template de bienvenida
│       ├── password-reset.html          # Template de reseteo de contraseña
│       └── order-confirmation.html      # Template de confirmación de pedido
```

---

## 📝 Template de Archivo `.env` para Producción

Crea un archivo `.env` en la raíz de tu proyecto (o en el servidor donde desplegarás) con este contenido:

```bash
# ======================================================
# MAILING SERVICE - Configuracion de Produccion
# ======================================================

# --- API Key (OBLIGATORIA) ---
# Genera una clave segura con: ./generate-api-key.ps1
# NUNCA uses 'dev-api-key-change-me' en produccion
API_KEY=tu-api-key-generada-aqui

# --- Admin UI Credentials ---
ADMIN_USERNAME=mi-admin
ADMIN_PASSWORD=mi-password-segura

# --- Database ---
DB_USER=mailuser
DB_PASSWORD=tu-password-de-db-segura
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/mailservice
SPRING_DATASOURCE_USERNAME=mailuser
SPRING_DATASOURCE_PASSWORD=tu-password-de-db-segura

# --- Configuracion SMTP ---
# Ejemplo con Brevo (gratuito hasta 300 emails/dia)
SPRING_MAIL_HOST=smtp-relay.brevo.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=tu-login@brevo.com
SPRING_MAIL_PASSWORD=tu-smtp-key-de-brevo
SPRING_MAIL_AUTH=true
SPRING_MAIL_STARTTLS=true

# --- Remitente ---
MAIL_FROM=no-reply@tudominio.com
MAIL_FROM_NAME=Mi Aplicacion

# --- Perfil de Spring (opcional) ---
# En Docker se fuerza automaticamente a 'prod'
# SPRING_PROFILES_ACTIVE=prod
```

### Uso con Docker Compose

Si usas Docker Compose, referencia este archivo en tu `docker-compose.yml`:

```yaml
services:
  mail-service:
    image: mail-service:latest
    env_file: .env  # <-- Lee las variables del archivo .env
    ports:
      - "8081:8081"
    restart: unless-stopped
```

> **⚠️ IMPORTANTE:** Agrega `.env` a tu `.gitignore` para no subir credenciales al repositorio:
> ```bash
> echo ".env" >> .gitignore
> ```

---

## 📄 Licencia

Este proyecto está bajo la licencia **MIT**. Consultá el archivo [LICENSE](LICENSE) para más detalles.

---

<p align="center">
  Hecho con ☕ y Spring Boot 
</p>
