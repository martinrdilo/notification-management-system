# Testing Infrastructure — Decisiones y Funcionamiento

## Visión General

La infraestructura de testing usa **Testcontainers** (base de datos PostgreSQL real en Docker) y **WireMock** (servidor HTTP falso para mockear APIs externas). El objetivo es que los tests sean fieles al entorno de producción sin tocar datos reales ni hacer llamadas a servicios externos.

---

## 1. Base de Datos de Testing (Testcontainers)

### Qué es Testcontainers

Testcontainers es una librería que levanta contenedores Docker desde el código Java. En nuestro caso, levanta un **PostgreSQL real** (misma imagen que desarrollo: `postgres:16-alpine`) exclusivamente para los tests.

### Por qué PostgreSQL real y no H2

| Aspecto | H2 en memoria | PostgreSQL real (Testcontainers) |
|---|---|---|
| Fidelidad | Baja — diferencias de sintaxis SQL, tipos de datos, funciones | Alta — misma base que producción |
| Falsos positivos | Tests pueden pasar en H2 y fallar en producción | Si pasa en el test, pasa en producción |
| Dialecto SQL | Hay que mantener compatibilidad dual (H2 + PostgreSQL) | Un solo dialecto, sin workarounds |
| Migraciones | A veces Flyway/Liquibase fallan porque usan SQL nativo de PostgreSQL | Las migraciones corren exactamente igual |
| Velocidad | Muy rápido (~ms de startup) | Más lento (~3-5s la primera vez, después se reutiliza) |
| Requisito | Solo agregar dependencia | Necesita Docker corriendo |

**Decisión**: Priorizamos **fidelidad** sobre velocidad. Preferimos que un test falle en desarrollo (donde es barato arreglarlo) a que falle en producción.

### Patrón Singleton

```java
static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("notification_test_db")
                .withUsername("test")
                .withPassword("test");

static {
    POSTGRES.start();
}
```

El bloque `static {}` se ejecuta **una sola vez** cuando la JVM carga la clase. Todos los tests que hereden de `AbstractIntegrationTest` comparten el mismo contenedor. Si levantáramos un container por cada clase de test, con 20 clases tardaríamos 1-2 minutos solo en startup.

### Aislamiento entre tests

Hibernate está configurado con `ddl-auto: create-drop`, lo que significa que las tablas se crean al iniciar el contexto de Spring y se eliminan al cerrarlo. Dentro de una misma ejecución, el método `cleanDatabase()` de `AbstractIntegrationTest` elimina registros en orden FK-safe (notificaciones primero, usuarios después) antes de cada test.

---

## 2. Mock de APIs Externas (WireMock)

### Qué es WireMock

WireMock es un **servidor HTTP falso** que se levanta en un puerto random. Le decís: "Cuando recibas un GET a `/posts?userId=1`, respondé con este JSON". Así el `ExternalMediaClient` hace la request HTTP real, pero le pega a WireMock en vez de a la API externa.

### Por qué WireMock y no Mockito para el cliente HTTP

- **Mockito** mockea a nivel de código Java (el objeto `RestClient`). No testea la serialización/deserialización HTTP real.
- **WireMock** mockea a nivel de **red HTTP**. El `ExternalMediaClient` hace una request HTTP real a `localhost:puerto`, y WireMock responde. Esto verifica que headers, status codes, y parsing de JSON funcionen correctamente.

Usamos Mockito para tests unitarios (sin red) y WireMock para tests de integración (con red simulada).

### Concepto de "Stub"

Un stub es una regla: "Si recibís **esta** request, respondé con **esto**".

```java
server.stubFor(WireMock.get(urlPathEqualTo("/posts"))
        .withQueryParam("userId", WireMock.equalTo("1"))
        .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[{\"userId\":1, \"id\":1, \"title\":\"...\", \"body\":\"...\"}]")));
```

Esto dice: "Si alguien hace `GET /posts?userId=1`, respondé 200 con este JSON."

### URL-Agnostic Matching — La decisión clave

Existen dos formas de matchear en WireMock:

- **`urlEqualTo("http://jsonplaceholder.com/posts?userId=1")`** — matchea la URL **completa**. Si cambiás la base URL, el stub se rompe.
- **`urlPathEqualTo("/posts")`** — matchea **solo el path** después del `/`. No le importa si la base es `localhost:8743` o `api.production.com`.

**Decisión**: Usamos `urlPathEqualTo` (path only) + `withQueryParam` para los query params. Esto hace que los tests no dependan de la URL base. Si mañana cambiás la API externa por otra en otra URL, los stubs siguen funcionando mientras el path sea el mismo.

---

## 3. @DynamicPropertySource — El mecanismo de override

```java
@DynamicPropertySource
static void overrideProperties(DynamicPropertyRegistry registry) {
    // Database
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    
    // External API
    registry.add("external.api.jsonplaceholder.base-url",
            () -> "http://localhost:" + WIREMOCK.port());
}
```

Tanto Testcontainers como WireMock usan **puertos dinámicos** (random). No sabemos el puerto hasta que se levantan. `@DynamicPropertySource` le dice a Spring:

> "Olvidate de lo que dice `application.yml`. Usá **estas** propiedades en su lugar."

- `spring.datasource.url` pasa de `jdbc:postgresql://localhost:5433/notification_db` (base real) a `jdbc:postgresql://localhost:54321/notification_test_db` (container)
- `external.api.jsonplaceholder.base-url` pasa de `https://jsonplaceholder.typicode.com` (API real) a `http://localhost:8743` (WireMock)

**La base de desarrollo NUNCA se toca. La API real NUNCA se llama.**

---

## 4. Estructura de Directorios

```
src/test/
├── java/io/backend/notifications/
│   ├── integration/                       # Tests con Spring context completo
│   │   ├── base/
│   │   │   └── AbstractIntegrationTest    # Testcontainers + WireMock + WebTestClient
│   │   ├── controller/
│   │   │   ├── AuthControllerIntegrationTest
│   │   │   ├── UserControllerIntegrationTest
│   │   │   └── NotificationControllerIntegrationTest
│   │   └── repository/
│   │       └── UserRepositoryIntegrationTest
│   ├── unit/                              # Tests SIN Spring context
│   │   ├── security/
│   │   │   └── JwtServiceUnitTest
│   │   └── service/
│   │       └── UserServiceUnitTest
│   └── fixture/                           # Datos de prueba (separados de lógica)
│       ├── entity/
│       │   ├── UserBuilder                # Builder para User + UserRequest + RegisterRequest
│       │   └── NotificationBuilder        # Builder para Notification
│       └── wiremock/
│           └── WireMockHelper             # Stubs reutilizables
└── resources/
    └── application-test.yml               # Config del profile "test"
```

### Por qué separar fixtures de tests

- **Reutilización**: Un `UserBuilder` se usa en tests de repository, controller y service. Si los datos estuvieran inline en cada test, habría duplicación.
- **Mantenimiento**: Si cambia la entidad `User` (nuevo campo obligatorio), se actualiza el builder en un solo lugar.
- **Legibilidad**: El test muestra la **lógica** (qué se testea y qué se espera), no los detalles de construcción de datos.

---

## 5. Flujo Completo de un Test de Integración

```
1. JVM carga AbstractIntegrationTest
2. static {} → Docker levanta PostgreSQL en puerto random (ej: 54321)
3. static {} → WireMock se levanta en puerto random (ej: 8743)
4. @DynamicPropertySource → Spring usa estos puertos en vez de los de application.yml
5. @SpringBootTest → Spring Boot arranca completo, conecta a notification_test_db en puerto 54321
6. Hibernate crea las tablas (ddl-auto: create-drop)
7. ExternalMediaClient se configura con base-url = localhost:8743
8. @BeforeEach → cleanDatabase() elimina todos los datos y WireMockHelper.reset() limpia stubs
9. El test configura stubs en WireMock si los necesita (ej: GET /photos/1 → responde JSON)
10. El test ejecuta la acción (ej: llama al endpoint del controller con token JWT)
11. El controller llama al service, el service consulta el SecurityContext para obtener el usuario
12. El client hace HTTP GET a localhost:8743/photos/1
13. WireMock responde con el JSON configurado en el stub
14. El test verifica el resultado
15. Al terminar TODOS los tests, Docker destruye el container
```

---

## 6. Dependencias Agregadas

```groovy
// Testcontainers — PostgreSQL real en Docker
testImplementation 'org.testcontainers:testcontainers:2.0.3'
testImplementation 'org.testcontainers:testcontainers-postgresql:2.0.3'
testImplementation 'org.testcontainers:testcontainers-junit-jupiter:2.0.3'

// WireMock — mock de APIs externas
testImplementation 'org.wiremock:wiremock-standalone:3.10.0'

// Seguridad — tests con contexto de autenticación
testImplementation 'org.springframework.security:spring-security-test'

// WebFlux — WebTestClient para tests de endpoints
testImplementation 'org.springframework.boot:spring-boot-starter-webflux'
```

- **testcontainers**: Librería base de Testcontainers para gestión de contenedores Docker desde Java.
- **testcontainers-postgresql**: Soporte específico para contenedores PostgreSQL (`PostgreSQLContainer`).
- **testcontainers-junit-jupiter**: Integración con JUnit 5 (`@Testcontainers`, `@Container`).
- **wiremock-standalone**: WireMock sin dependencias de servidor adicionales (incluye Jetty embebido).
- **spring-security-test**: Utilidades para tests con Spring Security. Permite crear requests con contextos de autenticación mockeados (`@WithMockUser`, `SecurityMockMvcRequestPostProcessors`). En este proyecto se usa el flujo real de JWT en vez de mocks de seguridad.
- **spring-boot-starter-webflux**: Provee `WebTestClient`, cliente HTTP reactivo usado para hacer requests a los endpoints en los tests de integración.

### Dependencias de seguridad en runtime

```groovy
// Spring Security — filtros, contexto de autenticación, password encoder
implementation 'org.springframework.boot:spring-boot-starter-security'

// JJWT — generación y validación de tokens JWT
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
```

- **spring-boot-starter-security**: Autoconfigura Spring Security. Incluye filtros de autenticación, `SecurityFilterChain`, `BCryptPasswordEncoder`, y el `AuthenticationManager`.
- **jjwt-api**: API pública de JJWT 0.12.x. Define las interfaces `Jwts`, `Claims`, `JwtBuilder`. Es la única dependencia que necesitás en tiempo de compilación.
- **jjwt-impl**: Implementación interna de JJWT. Se declara como `runtimeOnly` porque no se usa directamente en código — JJWT lo carga via ServiceLoader.
- **jjwt-jackson**: Soporte de serialización JSON para JJWT usando Jackson. Necesario para parsear y generar el payload del token. También `runtimeOnly`.

---

## 7. Configuración del Profile de Test

**Archivo**: `src/test/resources/application-test.yml`

```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true

external:
  api:
    jsonplaceholder:
      base-url: http://localhost:0

jwt:
  secret: dGVzdHNlY3JldGtleWZvcnRlc3Rpbmd3aXRoZW5vdWdoYnl0ZXM=
  expiration-ms: 86400000

springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

- `ddl-auto: create-drop` — Hibernate crea las tablas al inicio y las elimina al final.
- Las propiedades de datasource (`url`, `username`, `password`) son **placeholder** porque se sobreescriben con `@DynamicPropertySource`.
- `external.api.jsonplaceholder.base-url: http://localhost:0` — placeholder; se sobreescribe con el puerto real de WireMock via `@DynamicPropertySource`.
- `jwt.secret` — clave fija en Base64 para tests. Valor conocido y estable; no depende de variables de entorno. La misma clave se usa en `JwtServiceUnitTest` para tests unitarios.
- `jwt.expiration-ms: 86400000` — 24 horas. Suficiente para que los tokens generados durante los tests no expiren entre pasos del mismo test.
- Swagger desactivado en tests (no necesario, ahorra tiempo de startup).

---

## 8. Helper de Autenticación en Tests

Los tests de controller que acceden a endpoints protegidos necesitan un token JWT válido. `AbstractIntegrationTest` provee dos métodos helper para obtenerlo sin duplicar lógica en cada test.

### `obtainToken(email, password)`

Hace un `POST /auth/login` y devuelve el token del response. Asume que el usuario ya existe en la base de datos.

```java
protected String obtainToken(String email, String password) {
    AuthResponse response = webTestClient()
            .post().uri("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new LoginRequest(email, password))
            .exchange()
            .expectStatus().isOk()
            .returnResult(AuthResponse.class)
            .getResponseBody()
            .blockFirst();

    return response != null ? response.token() : null;
}
```

### `registerAndLogin(UserBuilder)`

Registra un usuario via `POST /auth/register` y luego llama a `obtainToken()` para devolver el token. Es el helper más usado en los tests de controller porque encapsula el setup completo en una sola línea.

```java
protected String registerAndLogin(UserBuilder builder) {
    webTestClient()
            .post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(builder.buildRegisterRequest())
            .exchange()
            .expectStatus().isCreated();

    return obtainToken(builder.getEmail(), builder.getPassword());
}
```

### Por qué existen estos métodos

Los endpoints de usuarios y notificaciones requieren autenticación JWT. Sin estos helpers, cada test tendría que repetir el flujo de registro + login + extracción del token — tres bloques de código por test. Los helpers reducen eso a una sola línea:

```java
// En cualquier test de controller
UserBuilder builder = UserBuilder.aUser();
String token = registerAndLogin(builder);

webTestClient().get()
        .uri("/users")
        .header("Authorization", "Bearer " + token)
        .exchange()
        .expectStatus().isOk();
```

### Por qué no se mockea la autenticación con `@WithMockUser`

Se eligió hacer el flujo real de autenticación JWT (registro → login → token) en vez de usar `@WithMockUser` de Spring Security Test. Esto garantiza que:

1. El `JwtAuthFilter` se ejecuta realmente en cada request de test.
2. La generación y validación del token pasan por `JwtService` real.
3. Los tests detectan regresiones en la cadena completa de seguridad, no solo en la lógica de negocio.
