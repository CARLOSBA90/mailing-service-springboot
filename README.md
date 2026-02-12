<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4.13-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Java-17-E11F21?logo=openjdk&logoColor=white" alt="Java 17">
  <img src="https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white" alt="Docker">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT">
</p>

# Mailing Service 

Microservicio **independiente, ligero y listo para producción** que expone una API REST para el envío de correos electrónicos con templates HTML. Diseñado para integrarse con cualquier backend (Spring Boot, Node.js, Django, Laravel, etc.) mediante una simple petición HTTP.

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

### ¿Por qué es un microservicio?
 
1. **Responsabilidad única:** Solo envía emails.
2. **Despliegue independiente:** Se construye, despliega y escala por separado.
3. **Comunicación vía red:** Los demás servicios se comunican con él mediante HTTP (API REST).
4. **Base de datos propia:** No comparte estado con otros servicios (en este caso, ni siquiera necesita base de datos).

> Podés tenerlo en la misma máquina, en otro VPS, en un clúster de Kubernetes, o en cualquier lugar con conectividad de red. Mientras la URL y la API Key sean accesibles, funciona.

---

##  Características Principales

| Característica | Descripción |
|---|---|
| **API REST** | Endpoint `POST /api/mail/send` para enviar correos |
| **Templates HTML** | Motor Thymeleaf para emails profesionales y responsive |
| **Procesamiento Async** | Los emails se encolan y procesan en background sin bloquear la respuesta HTTP |
| **Reintentos Automáticos** | Hasta 3 intentos con backoff exponencial (2s → 4s → 8s) ante fallos SMTP |
| **Seguridad API Key** | Autenticación por header `X-API-Key` para comunicación inter-servicios |
| **Health Check** | Endpoint `/actuator/health` para monitoreo y balanceadores de carga |
| **Dockerizado** | Imagen ligera basada en `eclipse-temurin:17-jre-alpine` |
| **Validación** | Validación automática del request (email, campos obligatorios) |
| **Manejo de Errores** | Respuestas consistentes con `GlobalExceptionHandler` |

---

## Arquitectura

```
┌──────────────────────┐         HTTP POST           ┌──────────────────────────┐
│                      │    (JSON + X-API-Key)        │    MAILING SERVICE       │
│  Tu Backend          │ ───────────────────────────► │                          │
│  (cualquier lenguaje)│                              │  ┌──────────────────┐    │
│                      │ ◄─── 202 Accepted ────────── │  │  MailController   │    │
└──────────────────────┘                              │  └────────┬─────────┘    │
                                                      │           │              │
                                                      │  ┌────────▼─────────┐    │
                                                      │  │  SecurityFilter   │    │
                                                      │  │  (API Key auth)   │    │
                                                      │  └────────┬─────────┘    │
                                                      │           │              │
                                                      │  ┌────────▼─────────┐    │     ┌─────────────┐
                                                      │  │  MailServiceImpl  │────│────►│  SMTP Relay │
                                                      │  │  (Async + Retry)  │    │     │  (Brevo,    │
                                                      │  └────────┬─────────┘    │     │  SendGrid,  │
                                                      │           │              │     │  Postfix…)  │
                                                      │  ┌────────▼─────────┐    │     └─────────────┘
                                                      │  │  Thymeleaf       │    │
                                                      │  │  (HTML Templates)│    │
                                                      │  └──────────────────┘    │
                                                      └──────────────────────────┘
```

### Flujo de un Email

1. Tu backend envía un `POST /api/mail/send` con el header `X-API-Key`.
2. El `SecurityFilter` valida la API Key. Si es inválida → `401 Unauthorized`.
3. El `MailController` valida el body del request. Si falla → `400 Bad Request`.
4. El request se acepta inmediatamente con `202 Accepted` (non-blocking).
5. `MailServiceImpl` procesa el email en un **thread separado** (pool de 2 a 5 workers).
6. Thymeleaf renderiza el template HTML con las variables dinámicas.
7. El email se envía a través del relay SMTP configurado.
8. Si falla, se reintenta automáticamente hasta 3 veces con backoff exponencial.

---

##  Stack Tecnológico

| Tecnología | Propósito |
|---|---|
| **Spring Boot 3.4** | Framework base |
| **Spring Mail** | Envío de correos vía SMTP |
| **Thymeleaf** | Motor de templates HTML |
| **Spring Security** | Autenticación por API Key |
| **Spring Retry** | Reintentos automáticos con backoff |
| **Spring Actuator** | Health checks y monitoreo |
| **Lombok** | Reducción de boilerplate |
| **Docker** | Contenerización |
| **Java 17** | Runtime |

---

##  Requisitos Previos

- **Java 17+** (para desarrollo local)
- **Maven 3.8+** (para compilar)
- **Docker** (opcional, para despliegue contenerizado)
- **Un servidor SMTP** (Brevo, SendGrid, Postfix local, Amazon SES, Mailgun, etc.)

---

##  Instalación y Configuración

### 1. Clonar el repositorio

```bash
git clone https://github.com/tu-usuario/mailing-service-springboot.git
cd mailing-service-springboot
```

### 2. Configurar variables de entorno

El servicio se configura **completamente** mediante variables de entorno. No es necesario tocar archivos de código:

| Variable | Descripción | Default |
|---|---|---|
| `SPRING_MAIL_HOST` | Host del servidor SMTP | `localhost` |
| `SPRING_MAIL_PORT` | Puerto SMTP | `25` |
| `SPRING_MAIL_USERNAME` | Usuario SMTP (si aplica) | _(vacío)_ |
| `SPRING_MAIL_PASSWORD` | Contraseña SMTP (si aplica) | _(vacío)_ |
| `SPRING_MAIL_AUTH` | ¿Requiere autenticación SMTP? | `false` |
| `SPRING_MAIL_STARTTLS` | ¿Usar TLS? | `false` |
| `API_KEY` | Clave API para autenticar requests | `dev-api-key-change-me` |
| `MAIL_FROM` | Email del remitente | `no-reply@tudominio.com` |
| `MAIL_FROM_NAME` | Nombre visible del remitente | `Mi Tienda` |

> [!CAUTION]
> **Cambiá el `API_KEY` por defecto antes de desplegar a producción.** Usá un valor largo y aleatorio.

### 3. Compilar y ejecutar

```bash
# Compilar
mvn clean package -DskipTests

# Ejecutar
java -jar target/mail-service-1.0.0-SNAPSHOT.jar
```

El servicio se levanta en **http://localhost:8081**.

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
  "error": "to: El destinatario es obligatorio, subject: El asunto es obligatorio"
}
```

**API Key inválida** (`401 Unauthorized`):
```json
{
  "error": "API Key inválida o ausente"
}
```

### Campos del Request

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `to` | `String` | ✅ | Email del destinatario (se valida formato) |
| `subject` | `String` | ✅ | Asunto del correo |
| `template` | `String` | ✅ | Nombre del template (sin extensión ni ruta) |
| `variables` | `Map<String, Object>` | ❌ | Variables dinámicas para inyectar en el template |

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

3. Enviá un request referenciando tu template:

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

### Docker Compose (ejemplo)

```yaml
version: "3.8"

services:
  mail-service:
    build: .
    container_name: mail-service
    ports:
      - "8081:8081"
    environment:
      API_KEY: ${API_KEY}
      SPRING_MAIL_HOST: ${SMTP_HOST}
      SPRING_MAIL_PORT: ${SMTP_PORT}
      SPRING_MAIL_USERNAME: ${SMTP_USERNAME}
      SPRING_MAIL_PASSWORD: ${SMTP_PASSWORD}
      SPRING_MAIL_AUTH: "true"
      SPRING_MAIL_STARTTLS: "true"
      MAIL_FROM: ${MAIL_FROM}
      MAIL_FROM_NAME: ${MAIL_FROM_NAME}
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
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
- Procesar envíos de forma asíncrona y con reintentos.
- Proteger el acceso mediante API Key.

### 🔄 Posibles Upgrades

Este servicio está diseñado como una base sólida y minimalista. Algunas mejoras que podrían implementarse en el futuro:

- **Persistencia de emails:** Guardar historial de envíos en base de datos (PostgreSQL, MongoDB) para auditoría y reenvíos.
- **Colas avanzadas:** Integración con RabbitMQ o Kafka para mayor resiliencia y escalabilidad.
- **Rate limiting integrado:** Implementar throttling a nivel de servicio con Bucket4j o Redis.
- **Email marketing:** Soporte para envíos masivos, segmentación de audiencias y campañas programadas.
- **Panel de administración:** Dashboard web para visualizar métricas, logs y gestionar templates.
- **Métricas avanzadas:** Integración con Prometheus/Grafana para monitoreo detallado.
- **Webhooks:** Notificaciones de eventos (email enviado, rebotado, abierto, click en links).
- **Adjuntos:** Soporte para archivos adjuntos (PDFs, imágenes, etc.).

###  Finalidad

Servir como **servicio base de mailing** para cualquier proyecto que necesite enviar correos electrónicos de forma desacoplada, sin importar el lenguaje o framework del backend principal. 

---

## FAQ

<details>
<summary><strong>¿Puedo usar este servicio sin Docker?</strong></summary>

Sí. Compilá con Maven y ejecutá el JAR directamente:

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

El servicio reintenta automáticamente hasta **3 veces** con backoff exponencial:
- 1er reintento: 2 segundos después.
- 2do reintento: 4 segundos después.
- 3er reintento: 8 segundos después.

Si los 3 intentos fallan, se loguea el error. Para mayor resiliencia, podrías integrar un sistema de colas (RabbitMQ, Redis) en un futuro.
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

El servicio está protegido por API Key, pero se recomienda:
- **No exponerlo directamente a internet.** Ponerlo detrás de un reverse proxy (Nginx, Traefik).
- **Usar una API Key robusta** (mínimo 32 caracteres, alfanumérica random).
- **Limitar el acceso por IP** desde el firewall o el reverse proxy.
</details>

<details>
<summary><strong>¿Puedo agregar mis propios templates?</strong></summary>

Sí. Solo creá un archivo `.html` en `src/main/resources/templates/mail/` usando sintaxis Thymeleaf. El servicio lo detecta automáticamente por nombre. Consultá la sección [Cómo Crear Templates Personalizados](#-cómo-crear-templates-personalizados).
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

---

## 📁 Estructura del Proyecto

```
src/main/java/com/mailservice/
├── MailServiceApplication.java       # Entry point + @EnableRetry
├── config/
│   ├── AsyncConfig.java              # ThreadPool para workers async
│   └── SecurityConfig.java           # Filtro API Key + Spring Security
├── controller/
│   └── MailController.java           # Endpoint REST /api/mail/send
├── dto/
│   ├── MailRequest.java              # Request body con validaciones
│   └── MailResponse.java             # Respuesta estandarizada
├── exception/
│   └── GlobalExceptionHandler.java   # Manejo centralizado de errores
└── service/
    ├── MailService.java              # Interfaz del servicio
    └── MailServiceImpl.java          # Implementación async + retry

src/main/resources/
├── application.yml                   # Configuración externalizable
└── templates/mail/
    ├── welcome.html                  # Template de bienvenida
    ├── password-reset.html           # Template de reseteo de contraseña
    └── order-confirmation.html       # Template de confirmación de pedido
```

---

## 📄 Licencia

Este proyecto está bajo la licencia **MIT**. Consultá el archivo [LICENSE](LICENSE) para más detalles.

---

<p align="center">
  Hecho con ☕ y Spring Boot 
</p>
