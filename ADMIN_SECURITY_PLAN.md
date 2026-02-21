# Admin UI — Robustez y Seguridad

Panel admin actualmente funcional pero con gaps de seguridad críticos: rutas públicas sin autenticación, CSRF deshabilitado en formularios POST, sin validación de inputs, y sin manejo de errores.

## User Review Required

> [!CAUTION]
> **Las rutas `/admin/**` son actualmente públicas (permitAll).** Cualquiera con acceso a la red puede ver logs de emails, y lo más crítico, **reintentar envíos masivos** sin autenticación. Esto debe resolverse antes de deploy a producción.

> [!IMPORTANT]
> **CSRF está deshabilitado globalmente.** Esto es correcto para la API REST (stateless + API Key), pero los formularios POST del admin (retry, retry-all) quedan expuestos a ataques CSRF. La solución es separar la seguridad en dos cadenas.

---

## Propuesta de Cambios

### 1. Seguridad — HTTP Basic Auth + CSRF selectivo

#### [MODIFY] [SecurityConfig.java](file:///c:/Dev/mailing-service-springboot/src/main/java/com/mailservice/config/SecurityConfig.java)

Separar en **dos SecurityFilterChain**:

```diff
+ // Cadena 1: Admin UI — con sesiones, CSRF y HTTP Basic
+ @Bean @Order(1)
+ SecurityFilterChain adminChain(HttpSecurity http) → {
+   securityMatcher("/admin/**", "/css/**")
+   httpBasic, CSRF habilitado, sessionManagement IF_REQUIRED
+   formLogin disabled
+ }

+ // Cadena 2: API REST — stateless, API Key, sin CSRF  
+ @Bean @Order(2)
+ SecurityFilterChain apiChain(HttpSecurity http) → {
+   csrf disabled, stateless, API Key filter
+ }
```

- El usuario/password se configura vía properties (`admin.username` / `admin.password`)
- En dev: valores hardcodeados en `application-dev.yml`
- En prod: variables de entorno `ADMIN_USERNAME`, `ADMIN_PASSWORD`

#### [MODIFY] [application-dev.yml](file:///c:/Dev/mailing-service-springboot/src/main/resources/application-dev.yml)

```yaml
admin:
  username: admin
  password: admin123
```

#### [MODIFY] [application-prod.yml](file:///c:/Dev/mailing-service-springboot/src/main/resources/application-prod.yml)

```yaml
admin:
  username: ${ADMIN_USERNAME}
  password: ${ADMIN_PASSWORD}
```

---

### 2. Robustez — Validación y Error Handling

#### [MODIFY] [AdminController.java](file:///c:/Dev/mailing-service-springboot/src/main/java/com/mailservice/controller/AdminController.java)

- **Limitar `size`**: cap a máximo 100 para evitar queries masivas
- **Validar `page`**: asegurar que no sea negativo
- **Validar `status`**: catch `IllegalArgumentException` de `MailStatus.valueOf()` para evitar 500 si mandan un status inválido
- **Manejo de errores global** con `@ExceptionHandler`

#### [NEW] [error.html](file:///c:/Dev/mailing-service-springboot/src/main/resources/templates/error.html)

Página de error genérica de Thymeleaf para 404/500. Spring Boot la detecta automáticamente.

---

### 3. Templates — CSRF tokens en formularios

#### [MODIFY] [logs.html](file:///c:/Dev/mailing-service-springboot/src/main/resources/templates/admin/logs.html)

Thymeleaf con Spring Security inyecta automáticamente `_csrf` en `<form>` con `th:action`. Los formularios ya usan `th:action`, así que **solo verificar** que todos los POST usen `th:action` y no `action` hardcodeado.

Cambio: el form de retry-all usa `action` hardcodeado → cambiar a `th:action`.

#### [MODIFY] [log-detail.html](file:///c:/Dev/mailing-service-springboot/src/main/resources/templates/admin/log-detail.html)

Mismo fix: verificar `th:action` en el form de retry.

---

## Verificación

### Automated Tests

Agregar tests al `SecurityConfigTest.java` existente:

```
mvn test -pl . -Dtest=SecurityConfigTest -q
```

Tests nuevos:
- `shouldRequireAuthForAdminDashboard()` — GET `/admin` sin credenciales → 401
- `shouldAllowAdminWithValidCredentials()` — GET `/admin` con Basic Auth → 200
- `shouldRejectAdminWithBadCredentials()` — GET `/admin` con password incorrecto → 401
