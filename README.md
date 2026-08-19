# SmartGym API (Documentación)

Este repositorio contiene la API REST del proyecto **SmartGym**, una aplicación de ejemplo construida con **Spring Boot** para gestionar clientes, entrenadores, reservas (*bookings*), rutinas, accesos y progreso físico.

- **Repositorio:** `smart-gym-oop`  
- **Stack principal:** Java 17, Spring Boot 3, Maven, Springdoc OpenAPI (Scalar)

---

### Descripción
SmartGym ofrece endpoints para:
- Gestión de clientes (creación, consulta)
- Gestión de entrenadores
- Reservas (*bookings*) con validaciones de conflicto y tiempo
- Gestión de rutinas y asignaciones por cliente
- Registro de accesos (*attendance*)
- Registro y consulta de progreso físico

---

### Requisitos
- **Java 17** (el proyecto usa `<java.version>17</java.version>` en el `pom.xml`)
- **Maven** (se incluye el wrapper `./mvnw`)
- **Puerto por defecto:** `8080`

> Si no tienes Java 17 instalado, instala una JDK compatible (OpenJDK 17+).

---

## Estructura principal (resumen)
Proyecto Java estándar, organizado por capas.  
A continuación, una vista rápida —ruta ➜ propósito ➜ clases de ejemplo:

| Ruta | Propósito | Clases / ejemplos |
| --- | --- | --- |
| `src/main/java/com/smartgym/api/controller` | Controladores REST (endpoints) | `AccessController`, `BookingController`, `CustomerController`, `IdentityController`, `ProgressController`, `RoutineController`, `TrainerController` |
| `src/main/java/com/smartgym/api/advice` | Manejo global de errores y adaptadores de respuesta | `ApiExceptionHandler`, `ApiErrorAdvice` |
| `src/main/java/com/smartgym/api/common` | Clases utilitarias y modelos de respuesta estándar | `ApiResponse`, `ApiError`, `ApiResponses` |
| `src/main/java/com/smartgym/model` | Modelos / entidades del dominio | `Customer`, `Trainer`, `Booking`, ... |
| `src/main/java/com/smartgym/service` | Lógica de negocio y orquestación | `SmartGymService` (y servicios relacionados) |
| `src/main/java/com/smartgym/domain` | Objetos de dominio especializados | `Routine`, `ProgressRecord`, `AttendanceRecord` |

**Consejos rápidos:**
- Busca `@RestController` o `@RequestMapping` para listar rápidamente todas las rutas.
- Los DTOs y validaciones están en `src/main/java/com/smartgym/api/dto`.
- El formato de respuesta y manejo global de errores están en `api/advice` y `api/common`.

---

### Cómo ejecutar (local)

1) **Construir el proyecto:**
```bash
./mvnw -DskipTests package
```

2) Ejecutar la aplicación:

```bash
./mvnw spring-boot:run
```

o bien:

```bash
java -jar target/smartgym-0.0.1-SNAPSHOT.jar
```

> La aplicación estará disponible en http://localhost:8080.

---

### Documentación OpenAPI / UI

- **UI (Scalar):** http://localhost:8080/docs
- **OpenAPI JSON:** http://localhost:8080/api-docs

> Estas rutas están habilitadas si la dependencia springdoc-openapi-starter-webmvc-scalar está activa (ver pom.xml).

---

### Base de datos H2 (modo archivo por defecto)

La aplicación usa **H2 en modo archivo (persistente)**. Archivo: `data/smartgymdb.mv.db`.

Configuración (extracto de `application.yml`):

```yaml
spring:
  profiles:
    active: h2
  datasource:
    url: jdbc:h2:file:./data/smartgymdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
  jpa:
    hibernate:
      ddl-auto: update
```

Acceso rápido:
- Consola: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:file:./data/smartgymdb`
- Usuario: `sa` (sin password)
- Modo memoria opcional: `jdbc:h2:mem:smartgymdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE` (datos se pierden al reiniciar)

Entidades y restricciones clave:
- `Booking` único por (`trainer_email`,`date`,`time`)
- `ProgressRecord` único por (`customer_email`,`date`)
- `IdentityLink` vincula DNI→email

### Seed de desarrollo (`data.sql`)
Se incluye un seed idempotente en `src/main/resources/data.sql` que:
- Limpia entradas previas relevantes (`DELETE FROM bookings`, baseline trainer/customer/identity) para evitar colisiones residuales.
- Inserta un entrenador base (`mike@smartgym.com`), un cliente (`alice@example.com`) y su DNI vinculado (`11111111`).

Uso:
1. Asegúrate de que `spring.jpa.hibernate.ddl-auto` permita crear/actualizar tablas.
2. Arranca la aplicación; Spring ejecutará automáticamente `data.sql` al inicializar el contexto.
3. Si deseas un entorno totalmente limpio, elimina el archivo `data/smartgymdb.mv.db` antes de iniciar.

Ventajas:
- Facilita pruebas manuales y la suite smoke sin pasos previos de creación.
- Reduce flakiness en reservas al partir de un estado conocido.

Nota: No se versionan los archivos `.mv.db` / `.trace.db`; sólo el seed.

## Modelo de Datos

```
Customer (email PK) 1 ───< Booking >─── 1 Trainer (email PK)
                           │    ▲
                           │    │ unique (trainer_email, date, time)
                           ▼    │
                       Schedule (embedded: date, time)

Customer 1 ───< Routine (plan Map<DayOfWeek,String>, created_at)
Customer 1 ───< ProgressRecord (unique: customer_email + date)

AttendanceRecord (*email, role, timestamp*)
IdentityLink (dni PK → email)
```

- `Booking.customer` / `Booking.trainer` son `@ManyToOne`.
- `Booking.schedule` es `@Embeddable`.
- `Routine.plan` usa `@ElementCollection`.
- `IdentityLink` persiste el mapeo DNI→email.

---

### Endpoints principales (resumen)
Las rutas pueden incluir el prefijo /api/v1.
Ejemplos:

- Clientes (Customer)
	- POST /api/v1/customers — Crear cliente
	- GET /api/v1/customers/{email} — Consultar por email
	- POST /api/v1/identity/customer — Vincular identidad

- Entrenadores (Trainer)
	- POST /api/v1/trainers — Crear entrenador
	- GET /api/v1/trainers/{email} — Obtener entrenador

- Reservas (Booking)
	- POST /api/v1/bookings — Crear reserva (fecha fija = hoy, cliente no envía date)
	- GET /api/v1/bookings — Listar reservas
	- DELETE /api/v1/bookings/{id} — Cancelar reserva

- Rutinas (Routine)
	- POST /api/v1/routines/assign — Asignar rutina a un cliente
	- GET /api/v1/routines/active/{dni}?day={monday..sunday} — Ver rutina activa
	- GET /api/v1/routines/history/{dni} — Historial de rutinas

- Acceso / Asistencia (Access / Attendance)
	- POST /api/v1/access — Registrar acceso
	- GET /api/v1/attendance/{dni} — Historial de asistencia

- Progreso (Progress)
	- POST /api/v1/progress — Registrar progreso
	- GET /api/v1/progress/{dni} — Consultar progreso

- Salud / Utilidad
	- GET /api/v1/health — Estado rápido y uptime

- Identidad extendida
	- GET /api/v1/identity/{dni} — Resolver identidad (dni→email)
	- GET /api/v1/customers/by-dni/{dni} — Obtener cliente vía DNI

> Confirma rutas exactas y parámetros en src/main/java/com/smartgym/api/controller.

---

### Formato de respuesta estándar

```json
{
  "success": true,
  "data": { /* payload */ },
  "message": "Customer created successfully",
  "timestamp": "2025-11-10T05:35:54Z",
  "path": "/api/v1/customers",
  "requestId": "b6c9e9e4-8f7b-4e2a-91d0-7f2e3a4f1c11"
}
```

Cada respuesta incluye además el header `X-Request-Id` y el campo `requestId` para trazabilidad.

---

### Validaciones importantes
- **Email:** debe ser válido.
- **DNI:** requerido para rutinas, accesos y progreso.
- **Reservas:** no se permiten fechas pasadas ni solapamientos del mismo entrenador.

Consulta las reglas específicas en los DTOs (src/main/java/com/smartgym/api/dto). La creación de reservas ignora cualquier campo "date" enviado y usa siempre la fecha del sistema (hoy); sólo se envía la hora.

---

### Scripts útiles


## 🔬 Suite de Tests de API (Smoke & Resistencia)

El script `smartgym_api_smoketest.sh` ejecuta ahora 34 escenarios etiquetados (negativos, límites, fuzz ligero, concurrencia, flujos positivos y verificación de nuevos endpoints). Usa `curl` y valida códigos de estado esperados para asegurar que:

- Validaciones estructurales retornan `400` (JSON malformado, tipos incorrectos, patrones inválidos, XSS básica).
- Reglas de negocio retornan `422` (fechas pasadas, DNI no vinculado, valores fuera de rango, enums inválidos, entidades inexistentes en operaciones semánticas).
- Colisiones / unicidad retornan `409` (email duplicado, progreso mismo día, slot de reserva ocupado por entrenador).
- Eliminaciones idempotentes: `204` primera eliminación, luego `422` si se repite.
- Endpoints utilitarios (`/health`, resolución de identidad y cliente por DNI) retornan `200` consistentes.
- No se producen `5xx` ante entradas inválidas esperadas.

Resumen última ejecución:

```
Total aserciones evaluadas: 33
PASS: 33 | FAIL: 0 | Pass rate: 100%
```

Notas:
- El test de concurrencia (label 18) es informativo (`MIX:201+409`) y no contabiliza en las aserciones.
- Los escenarios 19–24 (mass create, headers largos, método no soportado, 404, header injection, media type) son observacionales y algunos no ejecutan `assert_status` para mantener el enfoque en casos críticos.
- Nuevos endpoints verificados: labels 32 (health), 33 (identity resolve), 34 (customer by DNI).

### Taxonomía de Códigos de Estado

| Código | Significado | Ejemplos en la API |
|--------|-------------|--------------------|
| 200 | Consulta exitosa | GET bookings, progreso, rutina activa |
| 201 | Creación exitosa | POST customers, trainers, bookings, progress |
| 204 | Eliminación sin contenido | DELETE booking existente |
| 400 | Validación estructural / JSON inválido | Campos vacíos, tipos erróneos, formato email inválido |
| 404 | Recurso no encontrado / ruta inválida | Endpoint inexistente |
| 405 | Método no permitido | PATCH /customers |
| 409 | Conflicto de unicidad | Email duplicado, slot de booking ya ocupado, progreso mismo día |
| 415 | Content-Type no soportado | Enviar XML a endpoint JSON |
| 422 | Regla de negocio violada | Fecha pasada, DNI no vinculado, valores fuera de rango |

### Cómo ejecutar la suite

```bash
chmod +x smartgym_api_smoketest.sh
./smartgym_api_smoketest.sh
```

Requiere que el servidor esté levantado en `http://localhost:8080` y opcionalmente `jq` para formato de salida.

### Próximas mejoras sugeridas
- Ajustar el test de concurrencia para registrar explícitamente cuántos `201` y `409` ocurren.
- Integrar la suite en CI (GitHub Actions) y publicar reporte.
- Añadir pruebas positivas de historial (attendance / routine history).

Si quieres contribuir:

1. Haz fork y crea una rama feature/nueva-funcionalidad
2. Añade tests para tu cambio
3. Envía un pull request describiendo claramente el cambio

---

### Licencia y autor
Autores: Keyberth Rengel

---
## Estado versión 1.0
Checklist principal completado:
- Fecha de reserva ahora fija (servidor) — cliente sólo envía hora.
- Unificación de manejo de errores (handler central) y mensajes consistentes.
- `requestId` en cuerpo y header `X-Request-Id` para trazabilidad.
- Minimización de datos sensibles (`PaymentMethod` guarda sólo últimos 4 dígitos).
- Suite smoke robusta (34 escenarios, 0 FAIL) y seed estable `data.sql`.
- Documentación actualizada (este README) reflejando flujos y formato de respuesta.

Listo para etiquetar `v1.0.0`. Comandos sugeridos:
```bash
git add .gitignore src/main/resources/data.sql README.md smartgym_api_smoketest.sh
git commit -m "release: prepare v1.0.0 (seed, README, smoke stability)"
git tag v1.0.0
git push origin main --tags
```

