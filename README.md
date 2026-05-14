# Notification Management System

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/martinrdilo/notification-management-system/tree/main.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/martinrdilo/notification-management-system/tree/main)
[![Coverage Status](https://coveralls.io/repos/github/martinrdilo/notification-management-system/badge.svg?branch=main)](https://coveralls.io/github/martinrdilo/notification-management-system?branch=main)

REST API for notification management with JWT authentication and simulated multi-channel delivery (Email, SMS, Push). Built as a take-home technical challenge.

## Features

- **User registration & login** with JWT authentication (stateless)
- **CRUD for notifications**: create, read, update, delete
- **Multi-channel simulated delivery**: Email, SMS, and Push — each with channel-specific validation and formatting
- **Ownership enforcement**: users can only access their own notifications (IDOR protection)
- **Open/Closed Principle**: adding a new channel requires zero changes to existing code
- **Swagger UI**: interactive API documentation at `/swagger-ui.html`

## Stack

| Layer | Technology |
|-------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.0 |
| Security | Spring Security + JJWT 0.12.6 (stateless) |
| Persistence | Spring Data JPA + PostgreSQL 16 |
| API Docs | SpringDoc OpenAPI 2.8.5 (Swagger UI) |
| Build | Gradle 9.2.1 |
| Tests | JUnit 5, Testcontainers, WireMock, WebTestClient |

## Prerequisites

- Java 21
- Docker (for local PostgreSQL and integration tests)

## Quick Start

```bash
# 1. Clone the repository
git clone <repo-url>
cd backend-challenge

# 2. Build and start the app + PostgreSQL
./run-app.sh

# Or manually:
docker compose up -d
./gradlew bootRun
```

The API will be available at `http://localhost:8080`. Swagger UI at `http://localhost:8080/swagger-ui.html`.

## Live Demo

A running instance is deployed on Railway — no setup required:

**[https://feisty-gratitude-production-a5d2.up.railway.app/swagger-ui/index.html](https://feisty-gratitude-production-a5d2.up.railway.app/swagger-ui/index.html)**

How to test:
1. **Register**: `POST /auth/register` with a username, email, and password
2. **Login**: `POST /auth/login` with the same credentials — copy the JWT token from the response
3. **Authorize**: Click the 🔒 **Authorize** button in Swagger, paste the token, and click Authorize
4. **Try it out**: all authenticated endpoints are now available — create notifications, list them, update, delete

## API Endpoints

### Authentication (`/auth`)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/auth/register` | Register a new user (username, email, password) | No |
| POST | `/auth/login` | Log in, returns JWT | No |

### Notifications (`/notifications`)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/notifications` | List authenticated user's notifications | Bearer |
| GET | `/notifications/{id}` | Get notification by ID (own only) | Bearer |
| POST | `/notifications` | Create notification (triggers simulated channel delivery) | Bearer |
| PUT | `/notifications/{id}` | Update notification (title, content, attachmentIds) | Bearer |
| DELETE | `/notifications/{id}` | Delete notification (own only) | Bearer |

### Create Notification — Request

```json
POST /notifications
{
  "title": "System Alert",
  "content": "Server requires scheduled maintenance.",
  "channel": "EMAIL",
  "attachmentIds": [1, 2]
}
```

### Response

```json
{
  "id": 1,
  "title": "System Alert",
  "content": "Server requires scheduled maintenance.",
  "channel": "EMAIL",
  "status": "SENT",
  "createdAt": "2026-05-10T01:30:00",
  "userId": 1,
  "attachments": [
    { "id": 1, "title": "photo-1", "url": "https://..." }
  ]
}
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | HTTP port |
| `DB_URL` | — | PostgreSQL JDBC URL |
| `DB_USERNAME` | — | Database user |
| `DB_PASSWORD` | — | Database password |
| `JPA_DDL_AUTO` | `update` | Hibernate DDL strategy |
| `JWT_SECRET` | — | Secret key for signing JWT tokens |
| `JWT_EXPIRATION_MS` | `86400000` | Token expiration (24h default) |
| `EXTERNAL_API_BASE_URL` | `https://jsonplaceholder.typicode.com` | External API for photo enrichment |

> See `.env.example` for a ready-to-use template.

## Tests

```bash
# Run all tests with Docker
./run-tests.sh

# Or manually:
./gradlew test
```

**Current coverage**: 101 tests (unit + integration), 0 failures.

- **Unit**: `MockitoExtension`, no Spring context. Each sender and service tested in isolation.
- **Integration**: `Testcontainers` (real PostgreSQL) + `WireMock` (external API) + `WebTestClient` (HTTP).

## Documentation

Detailed architecture and testing docs are available in [`docs/`](docs/):

- [`01-authentication.md`](docs/01-authentication.md) — JWT flow and security configuration
- [`02-channel-sending-and-crud.md`](docs/02-channel-sending-and-crud.md) — Strategy pattern for channel dispatch + CRUD operations
- [`03-testing-infrastructure.md`](docs/03-testing-infrastructure.md) — Testcontainers, WireMock, and test architecture
- [`04-testing-architecture-diagram.md`](docs/04-testing-architecture-diagram.md) — Visual overview of the test setup

## Technical Decisions

### 1. Stateless JWT Authentication

Spring Security is configured with a JWT filter and no server-side session (`SessionCreationPolicy.STATELESS`). Every request includes the token in the `Authorization: Bearer <token>` header. The `JwtAuthFilter` extracts and validates the token before it reaches controllers, setting the `SecurityContext` with the user's email.

**Why**: REST APIs should not maintain server-side sessions. JWT enables horizontal scaling without sticky sessions.

### 2. Strategy Pattern for Channel Delivery

Channel delivery logic uses the Strategy pattern with Spring's auto-discovery. A `ChannelSender` interface defines two methods (`send`, `getChannel`) and each channel has its own `@Component` implementation. The `ChannelDispatcher` receives `List<ChannelSender>` via constructor injection and builds a `Map<Channel, ChannelSender>` for O(1) dispatch.

Delivery is **simulated** (logging only) — the challenge asks to simulate steps, not integrate real APIs. Each channel applies its own validation and formatting:

- **Email**: validates `user.getEmail()` is not null, formats a template, logs "Email sent to {email}"
- **SMS**: truncates content to 160 characters, logs "SMS sent at {timestamp}"
- **Push**: builds a JSON payload with title and content, logs "Push notification dispatched: {payload}"

**Why**: the key challenge requirement is _"the logic must be designed so that adding a new channel does not require modifying existing logic."_ With this design, adding a new channel (e.g., WhatsApp) requires just two steps: (1) add `WHATSAPP` to the `Channel` enum, (2) create `WhatsAppChannelSender implements ChannelSender` as a `@Component`. Spring auto-detects it and the `ChannelDispatcher` picks it up without touching a single line of existing code. This satisfies the Open/Closed Principle (OCP).

### 3. Ownership Enforcement

Every endpoint that accesses a notification by ID (`GET /{id}`, `PUT /{id}`, `DELETE /{id}`) validates that the notification belongs to the authenticated user via the `findOwnNotification(id)` helper. This method compares `notification.user.email` with the email from the `SecurityContext`. If they don't match, it returns **403 Forbidden**. If the notification doesn't exist, it returns **404 Not Found**.

**Why**: endpoints that expose IDs in the URL are vulnerable to Insecure Direct Object Reference (IDOR). Without this check, a user could access another user's notifications by simply iterating IDs.

### 4. GET /notifications Without userId in Path

The endpoint for listing own notifications (`GET /notifications`) derives the user from the `SecurityContext`, without requiring a `userId` in the URL. This avoids exposing user IDs in the API and follows the principle that the auth context determines the returned data.

**Why**: `GET /notifications/user/{userId}` allows any authenticated user to specify an arbitrary ID. Even though the backend validates ownership, exposing IDs in the URL is a poor REST practice. The correct endpoint uses the auth context implicitly.

### 5. Constructor Injection

The entire application uses constructor injection (no `@Autowired`). This makes dependencies explicit, objects immutable after construction, and unit testing trivial without a Spring context.

**Why**: this has been the Spring team's recommended practice since 2014. It makes testing with Mockito (`@ExtendWith(MockitoExtension.class)`) straightforward without booting the full context.

### 6. Immutable Channel on PUT

The update endpoint (`PUT /notifications/{id}`) uses `NotificationUpdateRequest`, a DTO that **excludes the `channel` field**. Once a notification is created and dispatched through a channel, that channel is immutable.

**Why**: changing the channel post-dispatch makes no business sense (the Email delivery logic already ran — you can't "un-send"). The update DTO is explicit about which fields are modifiable.

### 7. Integration Tests with Real Infrastructure

Integration tests use real PostgreSQL via Testcontainers and WireMock to simulate the external photo API. The database is never mocked. `AbstractIntegrationTest` provides shared infrastructure (singleton PostgreSQL container, WireMock on a dynamic port, `WebTestClient` for HTTP requests, FK-safe database cleanup between tests).

**Why**: testing against a real database catches issues that H2 in MySQL/PostgreSQL mode misses (SQL dialect differences, real constraints, ID sequences). The Testcontainers overhead is acceptable for the confidence it provides.

## Adding a New Channel

The system is designed for extensibility without modifying existing logic:

1. Add the new value to the `Channel` enum (`src/main/java/.../enums/Channel.java`)
2. Create a class implementing `ChannelSender`:
   ```java
   @Component
   public class WhatsAppChannelSender implements ChannelSender {
       private static final Logger log = LoggerFactory.getLogger(WhatsAppChannelSender.class);

       @Override
       public void send(Notification notification) {
           // Validate channel-specific requirements
           // Format payload
           log.info("WhatsApp message sent to user {}", notification.getUser().getId());
       }

       @Override
       public Channel getChannel() {
           return Channel.WHATSAPP;
       }
   }
   ```

That's it. Spring auto-registers the new `@Component` and the `ChannelDispatcher` picks it up without touching any other class.

## Areas to Improve

These are tradeoffs and improvements I'd make with more time:

- **Database migrations**: replace `ddl-auto=update` with Flyway or Liquibase for production-grade schema versioning
- **Error handling**: adopt RFC 7807 Problem Details (`application/problem+json`) for structured, machine-readable error responses
- **Seed data**: add a seed migration or data initializer so the app starts with sample users and notifications
- **CI/CD**: ~add a GitHub Actions pipeline~ ✅ Tests run on every push to `main` via CircleCI. Deployment remains to be set up
- **Rate limiting**: protect auth endpoints against brute-force attacks
- **Deployment**: ~deploy to a cloud provider~ ✅ Live demo deployed on Railway (see [Live Demo](#live-demo) above)

## Known Issues

None at this time.
