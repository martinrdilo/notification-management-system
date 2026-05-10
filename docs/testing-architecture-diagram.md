# Testing Architecture — Diagramas y Funcionamiento

---

## 1. Flujo de Ejecución de un Test de Integración

Paso a paso: qué pasa desde que corrés `./gradlew test` hasta que el test termina.

```mermaid
flowchart TD
    A["./gradlew test"] --> B["JVM carga AbstractIntegrationTest"]
    
    B --> C{"static block<br/>se ejecuta UNA sola vez"}
    
    C --> D["Docker levanta<br/>PostgreSQL 16-alpine<br/>puerto RANDOM ej: 54321"]
    C --> E["WireMock se levanta<br/>puerto RANDOM ej: 8743"]
    
    D --> F["@DynamicPropertySource"]
    E --> F
    
    F --> G["Override de propiedades Spring:<br/>datasource.url = localhost:54321/test_db<br/>base-url = localhost:8743"]
    
    G --> H["@SpringBootTest<br/>Spring Boot arranca completo"]
    
    H --> I["Hibernate crea tablas<br/>ddl-auto: create-drop"]
    
    I --> J["Tests corren contra<br/>infra real pero AISLADA"]
    
    J --> K{"Mas test classes?"}
    K -->|Si| L["Reutiliza MISMO<br/>container y WireMock<br/>singleton"]
    L --> J
    K -->|No| M["JVM termina<br/>Docker destruye container"]

    style D fill:#336791,color:#fff
    style E fill:#e4572e,color:#fff
    style F fill:#f5a623,color:#000
    style J fill:#2ecc71,color:#000
    style M fill:#95a5a6,color:#000
```

---

## 2. Aislamiento: Base de Datos de App vs Base de Test

Dos mundos completamente separados. Los tests NUNCA tocan la base de la app.

```mermaid
flowchart LR
    subgraph APP["App en desarrollo"]
        A1["application.yml"] --> A2["localhost:5433<br/>notification_db"]
        A2 --> A3[("PostgreSQL<br/>notification-db<br/>Docker compose")]
    end

    subgraph TEST["Tests"]
        T1["application-test.yml<br/>+ DynamicPropertySource"] --> T2["localhost:RANDOM<br/>test_db"]
        T2 --> T3[("PostgreSQL 16-alpine<br/>Testcontainers<br/>Docker auto")]
    end

    A3 -.-x|"NUNCA se conectan"| T3

    style A3 fill:#336791,color:#fff
    style T3 fill:#2ecc71,color:#000
    style APP fill:#fff3e0,color:#000
    style TEST fill:#e8f5e9,color:#000
```

---

## 3. Flujo de Mocking con WireMock - URL Agnostic

Cómo el `ExternalMediaClient` pega a WireMock en vez de a la API real.

```mermaid
sequenceDiagram
    participant Test as Test Class
    participant WH as WireMockHelper
    participant WM as WireMock Server
    participant Client as ExternalMediaClient
    participant Spring as Spring Context

    Note over Test: BeforeEach
    Test->>WH: stubGetPostsByUser(WIREMOCK, 1L, json)
    WH->>WM: Registra stub GET /posts userId=1

    Note over Test: Ejecuta el test
    Test->>Spring: GET /notifications
    Spring->>Client: getPhotoById(id)
    
    Note over Client: base-url apunta a WireMock
    Client->>WM: GET localhost:8743/photos/1
    
    Note over WM: Matchea por PATH solamente
    WM-->>Client: 200 OK + JSON mock
    
    Client-->>Spring: ExternalPhotoResponse
    Spring-->>Test: Response con datos mock
    
    Note over Test: Assertions sobre el response

    rect rgb(255, 230, 230)
        Note over WM: La API real NUNCA es llamada
    end
```

---

## 4. URL-Agnostic Matching

La clave de que los tests no se rompan si cambia la URL base.

### Forma INCORRECTA: urlEqualTo

```mermaid
flowchart LR
    M1["Stub: urlEqualTo<br/>http://jsonplaceholder.com/posts"] --> M3{"Match?"}
    M2["Request: GET<br/>http://localhost:8743/posts"] --> M3
    M3 -->|NO| M4["TEST FALLA<br/>URL completa no coincide"]
    
    style M4 fill:#e74c3c,color:#fff
```

### Forma CORRECTA: urlPathEqualTo

```mermaid
flowchart LR
    B1["Stub: urlPathEqualTo<br/>/posts + queryParam userId"] --> B4{"Match?"}
    B2["Request desde localhost:8743"] --> B4
    B3["Request desde api.production.com"] --> B4
    B4 -->|SI| B5["TEST PASA<br/>Solo importa el path /posts"]
    
    style B5 fill:#2ecc71,color:#000
```

---

## 5. Arquitectura de Clases de Test

Cómo se relacionan las clases implementadas.

```mermaid
classDiagram
    class AbstractIntegrationTest {
        PostgreSQLContainer POSTGRES
        WireMockServer WIREMOCK
        webTestClient() WebTestClient
        overrideProperties()
        cleanDatabase()
        obtainToken(email, password) String
        registerAndLogin(builder) String
    }

    class WireMockHelper {
        stubGetPostsByUser()
        stubGetPostsByUserEmpty()
        stubGetPostsByUserError()
        stubGetPostsByUserTimeout()
        reset()
    }

    class UserBuilder {
        aUser() UserBuilder
        withUsername() UserBuilder
        withEmail() UserBuilder
        withPassword() UserBuilder
        build() User
        buildRequest() UserRequest
        buildRegisterRequest() RegisterRequest
        getEmail() String
        getPassword() String
    }

    class NotificationBuilder {
        aNotification() NotificationBuilder
        withUser() NotificationBuilder
        withTitle() NotificationBuilder
        withContent() NotificationBuilder
        withChannel() NotificationBuilder
        withStatus() NotificationBuilder
        build() Notification
    }

    class UserRepositoryIntegrationTest {
    }
    class UserControllerIntegrationTest {
    }
    class AuthControllerIntegrationTest {
    }
    class NotificationControllerIntegrationTest {
    }
    class UserServiceUnitTest {
    }
    class JwtServiceUnitTest {
    }

    AbstractIntegrationTest <|-- UserRepositoryIntegrationTest : extends
    AbstractIntegrationTest <|-- UserControllerIntegrationTest : extends
    AbstractIntegrationTest <|-- AuthControllerIntegrationTest : extends
    AbstractIntegrationTest <|-- NotificationControllerIntegrationTest : extends

    UserRepositoryIntegrationTest ..> UserBuilder : crea datos
    UserControllerIntegrationTest ..> UserBuilder : crea datos
    AuthControllerIntegrationTest ..> UserBuilder : crea datos
    NotificationControllerIntegrationTest ..> UserBuilder : crea datos
    NotificationControllerIntegrationTest ..> WireMockHelper : usa stubs
    UserServiceUnitTest ..> UserBuilder : crea datos
    UserServiceUnitTest ..> NotificationBuilder : crea datos
```

---

## 6. Estructura de Directorios

```
src/test/
├── java/io/backend/notifications/
│   │
│   ├── integration/                              Tests CON Spring context
│   │   ├── base/
│   │   │   └── AbstractIntegrationTest           Testcontainers + WireMock + WebTestClient
│   │   ├── controller/
│   │   │   ├── AuthControllerIntegrationTest     Tests de register y login
│   │   │   ├── UserControllerIntegrationTest     Tests de endpoints de usuarios
│   │   │   └── NotificationControllerIntegrationTest  Tests con auth JWT
│   │   └── repository/
│   │       └── UserRepositoryIntegrationTest     Tests de queries a DB
│   │
│   ├── unit/                                     Tests SIN Spring context
│   │   ├── security/
│   │   │   └── JwtServiceUnitTest                Tests de generacion y validacion de tokens
│   │   └── service/
│   │       └── UserServiceUnitTest               Tests con Mockito puro
│   │
│   └── fixture/                                  DATOS separados de LOGICA
│       ├── entity/
│       │   ├── UserBuilder                       Crea User + UserRequest + RegisterRequest
│       │   └── NotificationBuilder               Crea Notification
│       └── wiremock/
│           └── WireMockHelper                    Stubs reutilizables
│
└── resources/
    └── application-test.yml                      Profile "test"
```

---

## 7. Tipos de Test y Cuándo Usar Cada Uno

```mermaid
flowchart TD
    Q{"Que queres testear?"}
    
    Q -->|"Logica de negocio<br/>sin DB, sin HTTP"| UNIT["TEST UNITARIO<br/>ExtendWith MockitoExtension<br/>Rapido - Mockea repos y clients<br/>No levanta Spring"]
    
    Q -->|"Query a la DB<br/>repositorio JPA"| REPO["TEST DE REPOSITORY<br/>extends AbstractIntegrationTest<br/>Testcontainers PostgreSQL<br/>Verifica queries reales"]
    
    Q -->|"Endpoint completo<br/>controller - service - DB"| CTRL["TEST DE CONTROLLER<br/>extends AbstractIntegrationTest<br/>WebTestClient<br/>Testcontainers + WireMock"]
    
    Q -->|"JWT: generar<br/>y validar tokens"| SEC["TEST DE SEGURIDAD<br/>Sin Spring context<br/>ReflectionTestUtils para @Value<br/>Mockito puro"]
    
    style UNIT fill:#3498db,color:#fff
    style REPO fill:#336791,color:#fff
    style CTRL fill:#2ecc71,color:#000
    style SEC fill:#8e44ad,color:#fff
```

---

## 8. Resumen: Que Garantiza Esta Arquitectura

| Garantia | Como se logra |
|---|---|
| Tests nunca tocan la DB real | `@DynamicPropertySource` apunta a Testcontainers |
| Tests nunca llaman a la API externa | `base-url` apunta a WireMock |
| Cambiar la URL base no rompe tests | `urlPathEqualTo` matchea solo el path |
| Datos de test no se mezclan con logica | Builders en `fixture/`, tests en `integration/` y `unit/` |
| Tests son repetibles | Testcontainers crea DB limpia, WireMock se resetea en cada test |
| Tests son rapidos | Singleton: 1 container para TODOS los tests |
| Tests reflejan produccion | PostgreSQL real, misma imagen: 16-alpine |
| Tests de auth son independientes | `JwtServiceUnitTest` sin Spring context, `ReflectionTestUtils` para inyectar secreto |
