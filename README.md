# SmartGym API

API REST para gestionar clientes, entrenadores, reservas, identidad por DNI, accesos al gimnasio, rutinas semanales y progreso físico.

## Stack

- Java 17
- Spring Boot 3.5.7
- Maven
- Spring Web
- Spring Data JPA
- Bean Validation
- H2 Database
- Springdoc OpenAPI + Scalar

## Funcionalidades soportadas

- Clientes: crear clientes, consultar por email y consultar por DNI vinculado.
- Entrenadores: crear entrenadores y consultar por email.
- Identidad: vincular DNI de 8 digitos a email de cliente o entrenador, y resolver DNI a email.
- Reservas: crear reservas, listar todas, listar por entrenador y fecha, y cancelar por `id`.
- Acceso y asistencia: registrar acceso por DNI y consultar historial de asistencia.
- Rutinas: asignar rutina semanal aleatoria, consultar rutina activa por dia e historial.
- Progreso fisico: registrar metricas diarias y consultar historial con totales y promedios.
- Observabilidad basica: health check y trazabilidad con `X-Request-Id`.
- Documentacion: Scalar UI y OpenAPI JSON.
- Pruebas: test de arranque Spring Boot y smoke test de API.
- Diagramas: PlantUML y PNG en `docs/uml`.

## Estructura del proyecto

| Ruta | Proposito |
| --- | --- |
| `src/main/java/com/smartgym/SmartGymApplication.java` | Entrada de la aplicacion Spring Boot |
| `src/main/java/com/smartgym/api/controller` | Controladores REST |
| `src/main/java/com/smartgym/api/dto` | DTOs de entrada y salida |
| `src/main/java/com/smartgym/api/common` | Envelope de respuesta, errores y request id |
| `src/main/java/com/smartgym/api/advice` | Manejo global de excepciones HTTP |
| `src/main/java/com/smartgym/application` | Funciones extendidas del dominio |
| `src/main/java/com/smartgym/service` | Servicio principal de negocio |
| `src/main/java/com/smartgym/model` | Entidades principales: cliente, entrenador y reserva |
| `src/main/java/com/smartgym/domain` | Entidades y value objects adicionales |
| `src/main/java/com/smartgym/repository` | Repositorios JPA |
| `src/main/resources/application.yml` | Configuracion de aplicacion |
| `src/main/resources/data.sql` | Seed de desarrollo |
| `docs/uml` | Diagramas PlantUML y exportaciones PNG |

## Requisitos

- JDK 17+
- Maven o wrapper `./mvnw`
- Puerto `8080` disponible

## Ejecucion local

Construir:

```bash
./mvnw -DskipTests package
```

Ejecutar:

```bash
./mvnw spring-boot:run
```

Tambien puedes ejecutar el JAR generado:

```bash
java -jar target/smartgym-0.0.1-SNAPSHOT.jar
```

La API queda disponible en:

```text
http://localhost:8080
```

## Seguridad (Clerk + JWT)

La API es un *resource server* OAuth2 stateless: valida el session token (JWT) que emite Clerk.
No maneja usuarios ni contraseñas; el login y el registro los hace Clerk desde el frontend.

- **Público:** `/api/v1/health`, `/docs`, `/v3/api-docs`, `/h2-console`. Todo lo demás de `/api/v1/**` exige `Authorization: Bearer <jwt>`.
- **Validación:** firma con el JWKS de Clerk (`<issuer>/.well-known/jwks.json`), expiración y `iss`.
- **Claims usados:** `role` (`cliente` | `entrenador` | `admin`; si falta o es desconocido se trata como `cliente`) y `email`. Se agregan en Clerk: *Sessions -> Customize session token*:

```json
{ "role": "{{user.public_metadata.role}}", "email": "{{user.primary_email_address}}" }
```

- **Errores:** 401 (`UNAUTHORIZED`, sin token o inválido) y 403 (`FORBIDDEN`) usan el mismo formato `ApiResponse`. Un token sin `email` produce 403 con el mensaje `The token does not include the email; configure the session token in Clerk`.
- **CORS:** orígenes permitidos en `smartgym.cors.allowed-origins` (por defecto `http://localhost:4200`), métodos GET/POST/PUT/DELETE/OPTIONS, sin cookies.

Propiedades (se sobreescriben con variables de entorno):

| Propiedad | Variable | Valor por defecto |
|---|---|---|
| `clerk.issuer` | `CLERK_ISSUER` | `https://brave-sawfish-4330.clerk.accounts.dev` |
| `smartgym.cors.allowed-origins` | `SMARTGYM_CORS_ALLOWED_ORIGINS` | `http://localhost:4200` |

Llamar con token:

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/customers/alice@example.com
```

Los tests usan el perfil `test` (BD en memoria, sin `data.sql`) y un `JwtDecoder` de prueba; no necesitan red ni Clerk.

## Documentacion OpenAPI

- Scalar UI: `http://localhost:8080/docs`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Base de datos

El proyecto usa H2 en modo archivo persistente.

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/smartgymdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: update
  h2:
    console:
      enabled: true
      path: /h2-console
```

Consola H2:

```text
http://localhost:8080/h2-console
```

Datos de conexion:

- JDBC URL: `jdbc:h2:file:./data/smartgymdb`
- User: `sa`
- Password: vacio

El seed `src/main/resources/data.sql` crea datos base para desarrollo y pruebas manuales:

- Entrenador: `mike@smartgym.com`
- Cliente: `alice@example.com`
- DNI vinculado: `11111111 -> alice@example.com`

Como la base es persistente, puedes borrar `data/smartgymdb.mv.db` si necesitas arrancar desde cero.

## Modelo de datos

```text
Customer (email PK) 1 ---< Booking >--- 1 Trainer (email PK)
                           |
                           | unique(trainer_email, date, time)
                           v
                         Schedule(date, time)

Customer 1 ---< Routine
Customer 1 ---< ProgressRecord

IdentityLink(dni PK -> email)
AttendanceRecord(email, role, timestamp)
PaymentMethod(card_number con ultimos 4 digitos)
```

Restricciones principales:

- `Booking`: unico por `trainer_email`, `date`, `time`.
- `ProgressRecord`: unico por `customer_email`, `date`.
- `IdentityLink`: DNI unico.
- `Routine`: el plan semanal se guarda como `Map<DayOfWeek, String>`.

## Contrato JSON

La aplicacion usa `SNAKE_CASE` como estrategia global de Jackson.

Ejemplo de respuesta exitosa:

```json
{
  "success": true,
  "data": {
    "email": "alice@example.com",
    "name": "Alice",
    "age": 28
  },
  "message": "Customer retrieved successfully",
  "timestamp": "2026-09-06T12:00:00Z",
  "path": "/api/v1/customers/alice@example.com",
  "request_id": "8db65c42-6f24-44bd-8af2-28d2adf9a817"
}
```

Ejemplo de error:

```json
{
  "success": false,
  "message": "Validation failed",
  "error": {
    "code": "BAD_REQUEST",
    "details": [
      {
        "field": "email",
        "error": "must be a well-formed email address"
      }
    ]
  },
  "timestamp": "2026-09-06T12:00:00Z",
  "path": "/api/v1/customers",
  "request_id": "8db65c42-6f24-44bd-8af2-28d2adf9a817"
}
```

Cada respuesta incluye el header `X-Request-Id`.

## Endpoints

Base URL:

```text
http://localhost:8080/api/v1
```

### Clientes

Crear cliente:

```http
POST /customers
Content-Type: application/json

{
  "email": "ana@example.com",
  "name": "Ana",
  "age": 30
}
```

Consultar cliente por email:

```http
GET /customers/{email}
```

Consultar cliente por DNI:

```http
GET /customers/by-dni/{dni}
```

### Entrenadores

Crear entrenador:

```http
POST /trainers
Content-Type: application/json

{
  "email": "coach@example.com",
  "name": "Coach",
  "age": 35,
  "specialty": "Strength"
}
```

Consultar entrenador por email:

```http
GET /trainers/{email}
```

### Usuario actual (`/me`)

El rol y el correo salen del token de Clerk (ver *Seguridad*). Solo los clientes pueden hacer onboarding.

```http
GET /me
Authorization: Bearer <jwt>
```

Respuesta (`data`):

```json
{
  "role": "cliente",
  "email": "carlos@example.com",
  "name": "Carlos Mendoza",
  "dni": "74582136",
  "profile_complete": true,
  "profile": { "email": "carlos@example.com", "name": "Carlos Mendoza", "age": 28 }
}
```

- Cliente: `profile_complete` = existe el cliente **y** tiene un DNI vinculado. Entrenador: existe el entrenador (el perfil incluye `specialty`). Admin: siempre `true` y `profile` nulo. Los datos que faltan se devuelven como `null`.
- Sin `email` en el token: 403 `The token does not include the email; configure the session token in Clerk`.

Completar el perfil (crea el cliente y vincula el DNI en una sola transacción):

```http
POST /me/onboarding
Authorization: Bearer <jwt>
Content-Type: application/json

{ "name": "Carlos Mendoza", "age": 28, "dni": "74582136" }
```

- `201` si se creó el cliente o el vínculo; `200` si ya estaba todo igual (idempotente); mismo cuerpo que `GET /me`.
- `409` si el DNI ya pertenece a otro correo, o si esta cuenta ya tiene otro DNI (no se crea nada).
- `400` con `error.details` por campo (nombre sin `<>` y máx. 120, edad >= 0, DNI de 8 dígitos). `403` para entrenador y admin.

### Identidad

Vincular DNI a cliente:

```http
POST /identity/customer
Content-Type: application/json

{
  "dni": "12345678",
  "email": "ana@example.com"
}
```

Vincular DNI a entrenador:

```http
POST /identity/trainer
Content-Type: application/json

{
  "dni": "87654321",
  "email": "coach@example.com"
}
```

Si el DNI ya está vinculado a **otro** correo responde `409` y no se sobrescribe; repetir el mismo vínculo es idempotente.

Resolver DNI:

```http
GET /identity/{dni}
```

### Reservas

Crear reserva:

```http
POST /bookings
Content-Type: application/json

{
  "customer_email": "alice@example.com",
  "trainer_email": "mike@smartgym.com",
  "time": "16:30",
  "note": "Upper body session"
}
```

Tambien se aceptan aliases camelCase para los emails:

```json
{
  "customerEmail": "alice@example.com",
  "trainerEmail": "mike@smartgym.com",
  "time": "16:30"
}
```

Notas de soporte:

- La fecha se fija en el servidor con `LocalDate.now()`.
- No se envia `date` en el request actual.
- `time` debe tener formato `HH:mm` de 24 horas.
- `note` es opcional y acepta hasta 250 caracteres.

Listar reservas:

```http
GET /bookings
```

Listar reservas de un entrenador por fecha:

```http
GET /trainers/{email}/bookings?date=2026-09-06
```

Cancelar reserva:

```http
DELETE /bookings/{id}
```

Respuesta esperada: `204 No Content`.

### Acceso y asistencia

Registrar acceso:

```http
POST /access
Content-Type: application/json

{
  "dni": "11111111"
}
```

Listar asistencia:

```http
GET /attendance/{dni}
```

### Rutinas

Asignar rutina:

```http
POST /routines/assign
Content-Type: application/json

{
  "dni": "11111111"
}
```

Consultar rutina activa por dia:

```http
GET /routines/active/{dni}?day=monday
```

Dias aceptados: `monday`, `tuesday`, `wednesday`, `thursday`, `friday`, `saturday`, `sunday`.

Consultar historial:

```http
GET /routines/history/{dni}
```

### Progreso

Registrar progreso:

```http
POST /progress
Content-Type: application/json

{
  "dni": "11111111",
  "weightKg": 74.5,
  "bodyFatPct": 18.2,
  "musclePct": 42.1
}
```

Consultar historial de progreso:

```http
GET /progress/{dni}
```

La respuesta incluye:

- `items`
- `total`
- `avg_weight_kg`
- `avg_body_fat_pct`
- `avg_muscle_pct`

### Health

```http
GET /health
```

## Validaciones y errores

Validaciones estructurales:

- Emails validos y no vacios.
- Nombres no vacios, maximo 120 caracteres y sin `<` ni `>`.
- Edad minima `0`.
- DNI de identidad con patron de 8 digitos.
- Hora de reserva con formato `HH:mm` en 24 horas.
- Nota de reserva maximo 250 caracteres.
- Metricas de progreso obligatorias.

Validaciones de dominio:

- DNI debe estar vinculado para accesos, rutinas y progreso.
- El email vinculado debe existir como cliente o entrenador para registrar acceso.
- El progreso solo aplica a clientes.
- Peso debe estar entre `0.1` y `400`.
- Porcentaje de grasa y musculo entre `0` y `100`.
- No se permite duplicar reserva para el mismo entrenador en la misma fecha y hora.
- No se permite duplicar progreso para el mismo cliente en la misma fecha.

Taxonomia HTTP:

| Codigo | Uso |
| --- | --- |
| `200` | Consulta u operacion exitosa |
| `201` | Recurso creado |
| `204` | Eliminacion exitosa sin cuerpo |
| `400` | JSON malformado, campos invalidos o parametros requeridos faltantes |
| `404` | Ruta o recurso no encontrado cuando el controlador lo devuelve explicitamente |
| `405` | Metodo HTTP no permitido |
| `409` | Conflicto de unicidad o estado duplicado |
| `415` | `Content-Type` no soportado |
| `422` | Regla de negocio violada |
| `500` | Error inesperado |

## Pruebas

Ejecutar tests Maven:

```bash
./mvnw test
```

Ejecutar smoke test de API:

```bash
chmod +x smartgym_api_smoketest.sh
./smartgym_api_smoketest.sh
```

El smoke test requiere que la aplicacion este levantada en `http://localhost:8080`. `jq` es opcional y solo mejora el formato de salida.

La suite cubre, entre otros casos:

- JSON malformado.
- Campos requeridos faltantes.
- Emails invalidos.
- Payloads grandes.
- Intentos basicos de XSS/header injection.
- DNI invalido o no vinculado.
- Valores fuera de rango.
- Reservas duplicadas.
- Cancelacion de reservas.
- Concurrencia sobre el mismo slot de entrenador.
- Health check.
- Resolucion de identidad.
- Consulta de cliente por DNI.

## Diagramas UML

Los diagramas viven en `docs/uml`:

- `domain.puml` / `domain.png`
- `controladores_dtos.puml` / `controladores_dtos.png`
- `repositorios_servicio.puml` / `repositorios_servicio.png`
- `secuencia_booking.puml` / `secuencia_booking.png`

Para regenerarlos:

```bash
./docs/uml/export_diagrams.sh
```

## Autor

Keyberth Rengel
