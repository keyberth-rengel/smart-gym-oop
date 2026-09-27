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
| `clerk.secret-key` | `CLERK_SECRET_KEY` | vacía (invitaciones desactivadas) |
| `clerk.invitation-redirect-url` | `CLERK_INVITATION_REDIRECT_URL` | `http://localhost:4200/auth/sign-up` |
| `clerk.api-base` | (fija) | `https://api.clerk.com` |

**Invitar entrenadores (`CLERK_SECRET_KEY`).** La clave secreta de Clerk (`sk_...`, Dashboard de Clerk -> API keys) solo se usa
en el backend para invitar entrenadores y nunca debe ir al repositorio ni al frontend: se pasa como variable de entorno
(`CLERK_SECRET_KEY=sk_test_... ./mvnw spring-boot:run`). Sin la variable, el alta de entrenadores funciona igual y la
invitacion se omite (`invitation.status = SKIPPED`). La clave no se escribe en logs ni en respuestas.

### Autorización por rol

Además de exigir un token válido, cada endpoint aplica reglas por rol. Las reglas se evalúan **antes** de mirar si el recurso existe: para un usuario sin permiso, un recurso ajeno y uno inexistente responden el mismo `403`, así no se filtra qué datos existen.

- **Dueño:** el correo del recurso (o el correo vinculado al DNI) es el del token.
- **Cliente del entrenador:** existe al menos una reserva entre ambos.
- Los correos se comparan sin distinguir mayúsculas.

| Endpoint | Admin | Cliente | Entrenador |
|---|---|---|---|
| `GET /bookings` | todas | solo las suyas | solo las suyas |
| `POST /bookings` | cualquier cliente | solo a su nombre | 403 |
| `DELETE /bookings/{id}` | sí | 403 | 403 |
| `POST /customers`, `POST /trainers` | sí | 403 | 403 |
| `GET /customers` | sí | 403 | 403 |
| `GET /customers/{email}`, `GET /customers/by-dni/{dni}` | sí | solo el suyo | solo sus clientes |
| `GET /trainers`, `GET /trainers/{email}`, `GET /trainers/{email}/availability` | sí | sí | sí |
| `GET /trainers/{email}/customers`, `GET /trainers/{email}/bookings` | sí | 403 | solo el suyo |
| `POST /identity/customer`, `POST /identity/trainer` | sí | 403 | 403 |
| `GET /identity/{dni}` | sí | solo su DNI | solo su DNI |
| `POST /progress` | cualquier DNI | solo su DNI | 403 |
| `GET /progress/{dni}`, `GET /progress/by-email/{email}` | sí | solo el suyo | solo sus clientes |
| `POST /routines/assign` | cualquier cliente | 403 | solo sus clientes |
| `GET /routines/history/{dni}`, `/routines/active/{dni}` y sus variantes `/routines/by-email/{email}/...` | sí | solo el suyo | solo sus clientes |
| `POST /access` | cualquier DNI | solo su DNI | solo su DNI |
| `GET /attendance/{dni}` | sí | solo su DNI | solo su DNI |
| `POST /me/onboarding` | 403 | sí | 403 |
| `GET /me`, `GET /health` | sí | sí | sí (`/health` es público) |

Las respuestas de cliente y entrenador son DTO (`email`, `name`, `age` y, en entrenador, `specialty`); nunca incluyen datos de pago ni el historial de reservas.

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

- Entrenador: `mike+clerk_test@smartgym.com`
- Cliente: `alice+clerk_test@example.com`

### Cuentas de prueba (Clerk)

Para probar el login end-to-end (frontend + backend) por rol se crearon estas cuentas en la instancia de desarrollo
de Clerk, reutilizando los correos ya sembrados arriba para entrenador y cliente. Los tres usan el sufijo
`+clerk_test`, que acepta siempre el código de verificación **`424242`** al iniciar sesión desde un navegador nuevo:

| Rol | Correo | Contraseña |
|---|---|---|
| Cliente | `alice+clerk_test@example.com` | `Smartgym2026Test!` |
| Entrenador | `mike+clerk_test@smartgym.com` | `Smartgym2026Test!` |
| Admin | `admin+clerk_test@smartgym.com` | `Smartgym2026Test!` |

> Instancia de **desarrollo** de Clerk, no de producción. Antes de publicar este repo hay que rotar/eliminar estas
> cuentas y regenerar `CLERK_SECRET_KEY` (compartida durante el desarrollo; ver `PLAN.md` del frontend → Pendiente).

`spring.jpa.defer-datasource-initialization=true` hace que `data.sql` corra despues de que Hibernate cree el esquema, asi la app arranca igual con BD en archivo (por defecto) o en memoria (`--spring.datasource.url=jdbc:h2:mem:x`), sin necesidad de `--spring.sql.init.mode=never`.
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

Crear cliente (solo `admin`; la respuesta es `email`, `name`, `age`). `dni` es opcional (8 digitos) y se vincula en la misma
transaccion; un DNI de otro correo da 409 sin crear nada. No se invita a nadie (el cliente se registra solo en Clerk):

```http
POST /customers
Content-Type: application/json

{
  "email": "ana@example.com",
  "name": "Ana",
  "age": 30,
  "dni": "87654321"
}
```

Listar clientes (solo rol `admin`; ordenados por nombre; devuelve `email`, `name`, `age` sin datos de pago ni historial):

```http
GET /customers
```

Consultar cliente por email (admin, el propio cliente o su entrenador; ver la matriz de autorización):

```http
GET /customers/{email}
```

Consultar cliente por DNI:

```http
GET /customers/by-dni/{dni}
```

### Entrenadores

Crear entrenador (solo `admin`). `dni` es opcional (8 digitos): si viene, se vincula al correo en la misma transaccion, y un DNI
que ya pertenece a otro correo da 409 sin crear nada. Tras crear el entrenador se le invita en Clerk con el rol `entrenador`
(o, si ese correo ya tiene cuenta, se le asigna el rol). El entrenador se crea aunque la invitacion falle o se omita:

```http
POST /trainers
Content-Type: application/json

{
  "email": "coach@example.com",
  "name": "Coach",
  "age": 35,
  "specialty": "Strength",
  "dni": "12345678"
}
```

```json
{ "success": true,
  "data": { "email": "coach@example.com", "name": "Coach", "age": 35, "specialty": "Strength", "dni": "12345678",
            "invitation": { "status": "INVITED", "message": "invitation_sent" } } }
```

`invitation.status` es `INVITED`, `ROLE_UPDATED` (el correo ya tenia cuenta y se le asigno el rol; nunca se degrada a un admin),
`SKIPPED` (`clerk_not_configured`) o `FAILED` (`clerk_unreachable`, `clerk_error_<http>`, `clerk_user_not_found`,
`clerk_existing_admin`). `message` es un codigo estable, no un texto para mostrar.

Reintentar la invitacion de un entrenador existente (solo `admin`; siempre 200 si el entrenador existe, revisar `status`):

```http
POST /trainers/{email}/invite
```

```json
{ "success": true, "data": { "status": "FAILED", "message": "clerk_error_500" } }
```

Listar entrenadores (cualquier usuario autenticado; los clientes lo usan para reservar; ordenados por nombre; devuelve `email`, `name`, `age`, `specialty`):

```http
GET /trainers
```

Consultar entrenador por email:

```http
GET /trainers/{email}
```

Clientes de un entrenador (admin: cualquiera; entrenador: solo los suyos; cliente: 403). Devuelve los clientes
distintos con al menos una reserva, con su total de reservas y la mas reciente, de la mas reciente a la mas antigua:

```http
GET /trainers/{email}/customers
```

```json
{ "data": [ { "email": "carlos@example.com", "name": "Carlos Mendoza", "age": 28, "sessions": 4,
              "last_booking_date": "2026-09-18", "last_booking_time": "17:30" } ] }
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

Listar reservas de un entrenador (admin: cualquiera; entrenador: solo las suyas; cliente: 403):

```http
GET /trainers/{email}/bookings                          # todas, por fecha y hora
GET /trainers/{email}/bookings?date=2026-09-06          # un dia (tiene prioridad)
GET /trainers/{email}/bookings?from=2026-09-01&to=2026-09-30   # rango inclusivo; cada limite es opcional
```

`from` posterior a `to` responde `422`; un formato de fecha invalido responde `400`.

Disponibilidad de un entrenador (cualquier usuario autenticado; solo horas ocupadas, sin datos personales;
`date` por defecto es hoy). Los clientes usan este endpoint para mostrar los horarios libres:

```http
GET /trainers/{email}/availability?date=2026-09-06
```

```json
{ "data": { "date": "2026-09-06", "booked_times": ["09:30", "18:00"] } }
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

Asignar rutina (se envía **exactamente uno** de `dni` o `customer_email`; ninguno o ambos responde `400` con un error en cada campo; los valores vacíos cuentan como ausentes). El entrenador conoce correos y no DNI, por eso existen ambas formas:

```http
POST /routines/assign
Content-Type: application/json

{
  "dni": "11111111"
}
```

```http
POST /routines/assign
Content-Type: application/json

{
  "customer_email": "ana@example.com"
}
```

Consultar rutina activa por dia:

```http
GET /routines/active/{dni}?day=monday
```

Dias aceptados: `monday`, `tuesday`, `wednesday`, `thursday`, `friday`, `saturday`, `sunday`.
La rutina semanal cubre lunes a sabado: un dia sin bloque (el domingo) responde 200 con `{"day":"sunday","block":null}` (descanso), no un error.

Consultar historial:

```http
GET /routines/history/{dni}
```

Variantes por correo del cliente (misma respuesta y mismas reglas de acceso; pensadas para el entrenador):

```http
GET /routines/by-email/{email}/history
GET /routines/by-email/{email}/active?day=monday
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
GET /progress/by-email/{email}
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
| `404` | Recurso inexistente o DNI no vinculado (`error.code = NOT_FOUND`; mensajes como `Customer not found: ...`, `DNI not linked`, `No active routine`), o ruta inexistente. Un `403` por rol se evalua antes que el `404` |
| `405` | Metodo HTTP no permitido |
| `409` | Conflicto de unicidad o estado duplicado |
| `415` | `Content-Type` no soportado |
| `422` | Regla de negocio violada (p. ej. `Bookings in the past are not allowed.`, `from` posterior a `to`, dia de la semana invalido); ya no se usa para "no encontrado" |
| `500` | Error inesperado |

## Pruebas

Ejecutar tests Maven:

```bash
./mvnw test
```

Ejecutar smoke test de API:

```bash
chmod +x smartgym_api_smoketest.sh
CLERK_SECRET_KEY="sk_test_..." ./smartgym_api_smoketest.sh
```

El smoke test requiere que la aplicacion este levantada en `http://localhost:8080` y, como la API exige JWT (salvo
`/health`), un `CLERK_SECRET_KEY` para mintear un token de admin fresco antes de cada llamada (usa la cuenta
`admin+clerk_test@smartgym.com` de la sección de cuentas de prueba; se puede cambiar con `ADMIN_EMAIL`). Alternativa manual:
`TOKEN="<jwt>"` con un JWT ya obtenido (puede expirar a mitad de la suite, ya que dura ~60 s). `jq` es opcional y
solo mejora el formato de salida.

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
