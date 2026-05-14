# Decisiones Técnicas

Este documento detalla las decisiones arquitectónicas y de diseño tomadas en el proyecto. Para un resumen ejecutivo, ver [Architecture Highlights](../README.md#architecture-highlights) en el README.

---

## 1. Autenticación JWT Stateless

Spring Security está configurado con un filtro JWT sin sesión del lado del servidor (`SessionCreationPolicy.STATELESS`). Cada request incluye el token en el header `Authorization: Bearer <token>`. El `JwtAuthFilter` lo extrae y valida antes de que llegue a los controllers, seteando el `SecurityContext` con el email del usuario.

**Por qué**: las REST APIs no deberían mantener sesiones del lado del servidor. JWT permite escalado horizontal sin sticky sessions. Además, el statelessness simplifica el modelo mental: no hay estado compartido entre requests que se pueda desincronizar.

Para más detalle sobre el flujo completo de autenticación, ver [`01-authentication.md`](./01-authentication.md).

---

## 2. Strategy Pattern para Channel Delivery

La lógica de envío por canal usa el patrón Strategy con auto-descubrimiento de Spring. Una interfaz `ChannelSender` define dos métodos (`send`, `getChannel`) y cada canal tiene su propia implementación anotada con `@Component`. El `ChannelDispatcher` recibe `List<ChannelSender>` vía constructor injection y construye un `Map<Channel, ChannelSender>` para dispatch en O(1).

El envío es **simulado** (solo logging) — el challenge pide simular los pasos, no integrar APIs reales. Cada canal aplica su propia validación y formato:

- **Email**: valida que `user.getEmail()` no sea null, formatea un template, loguea "Email sent to {email}"
- **SMS**: trunca el contenido a 160 caracteres, loguea "SMS sent at {timestamp}"
- **Push**: construye un payload JSON con title y content, loguea "Push notification dispatched: {payload}"

**Por qué**: el requisito clave del challenge es _"la lógica debe estar diseñada de modo que agregar un nuevo canal no requiera modificar la lógica existente"_. Con este diseño, agregar un nuevo canal (ej. WhatsApp) requiere solo dos pasos: (1) agregar `WHATSAPP` al enum `Channel`, (2) crear `WhatsAppChannelSender implements ChannelSender` como `@Component`. Spring lo auto-detecta y el `ChannelDispatcher` lo incorpora sin tocar una sola línea de código existente. Esto satisface el Open/Closed Principle (OCP).

### Cómo agregar un nuevo canal

1. Agregar el nuevo valor al enum `Channel` (`src/main/java/.../enums/Channel.java`)
2. Crear una clase que implemente `ChannelSender`:

```java
@Component
public class WhatsAppChannelSender implements ChannelSender {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppChannelSender.class);

    @Override
    public void send(Notification notification) {
        // Validar requisitos específicos del canal
        // Formatear payload
        log.info("WhatsApp message sent to user {}", notification.getUser().getId());
    }

    @Override
    public Channel getChannel() {
        return Channel.WHATSAPP;
    }
}
```

Eso es todo. Spring registra automáticamente el nuevo `@Component` y el `ChannelDispatcher` lo incorpora sin tocar ninguna otra clase.

Para más detalle sobre la implementación del Strategy Pattern, ver [`02-channel-sending-and-crud.md`](./02-channel-sending-and-crud.md).

---

## 3. Ownership Enforcement (protección IDOR)

Todos los endpoints que acceden a una notificación por ID (`GET /{id}`, `PUT /{id}`, `DELETE /{id}`) validan que la notificación pertenezca al usuario autenticado mediante el helper `findOwnNotification(id)`. Este método compara `notification.user.email` con el email del `SecurityContext`. Si no coinciden, devuelve **403 Forbidden**. Si la notificación no existe, devuelve **404 Not Found**.

**Por qué**: los endpoints que exponen IDs en la URL son vulnerables a Insecure Direct Object Reference (IDOR). Sin esta validación, un usuario podría acceder a notificaciones de otro usuario simplemente iterando IDs.

---

## 4. GET /notifications sin userId en la URL

El endpoint para listar notificaciones propias (`GET /notifications`) deriva el usuario del `SecurityContext`, sin requerir un `userId` en la URL. Esto evita exponer IDs de usuario en la API y sigue el principio de que el contexto de autenticación determina los datos devueltos.

**Por qué**: `GET /notifications/user/{userId}` permite que cualquier usuario autenticado especifique un ID arbitrario. Aunque el backend valide ownership, exponer IDs en la URL es mala práctica REST. El endpoint correcto usa el contexto de auth implícitamente.

---

## 5. Constructor Injection

Toda la aplicación usa constructor injection (sin `@Autowired`). Esto hace que las dependencias sean explícitas, los objetos inmutables post-construcción, y el unit testing trivial sin un Spring context.

**Por qué**: es la práctica recomendada por el equipo de Spring desde 2014. Hace que testear con Mockito (`@ExtendWith(MockitoExtension.class)`) sea directo sin necesidad de levantar el contexto completo.

---

## 6. Canal inmutable en PUT

El endpoint de actualización (`PUT /notifications/{id}`) usa `NotificationUpdateRequest`, un DTO que **excluye el campo `channel`**. Una vez que una notificación se crea y se despacha por un canal, ese canal es inmutable.

**Por qué**: cambiar el canal post-dispatch no tiene sentido de negocio (la lógica de Email ya se ejecutó — no se puede "des-enviar"). El DTO de update es explícito sobre qué campos son modificables.

---

## 7. Tests de integración con infraestructura real

Los tests de integración usan PostgreSQL real via Testcontainers y WireMock para simular la API externa de fotos. La base de datos nunca se mockea. `AbstractIntegrationTest` provee infraestructura compartida (contenedor PostgreSQL singleton, WireMock en puerto dinámico, `WebTestClient` para requests HTTP, limpieza FK-safe entre tests).

**Por qué**: testear contra una base de datos real detecta problemas que H2 en modo MySQL/PostgreSQL no detecta (diferencias de dialecto SQL, constraints reales, secuencias de IDs). El overhead de Testcontainers es aceptable por la confianza que da.

Para más detalle sobre la infraestructura de testing, ver:
- [`03-testing-infrastructure.md`](./03-testing-infrastructure.md)
- [`04-testing-architecture-diagram.md`](./04-testing-architecture-diagram.md)
