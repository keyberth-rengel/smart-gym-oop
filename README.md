# 🏋️‍♂️ SmartGym — API (Documentación)

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
	- POST /api/v1/bookings — Crear reserva
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

> Confirma rutas exactas y parámetros en src/main/java/com/smartgym/api/controller.

---

### Formato de respuesta estándar

```json
{
	"success": true,
	"data": { /* payload */ },
	"message": "Operación exitosa",
	"timestamp": "2025-11-10T05:35:54Z",
	"path": "/api/v1/customers",
	"request_id": "uuid"
}
```

Cada respuesta incluye además el header X-Request-Id para trazabilidad.

---

### Validaciones importantes
- **Email:** debe ser válido.
- **DNI:** requerido para rutinas, accesos y progreso.
- **Reservas:** no se permiten fechas pasadas ni solapamientos del mismo entrenador.

Consulta las reglas específicas en los DTOs (src/main/java/com/smartgym/api/dto).

---

### Scripts útiles
- `./smartgym_api_smoketest.sh` — Verificación rápida / smoke tests: errores 4xx consistentes, sin 500s, sin corrupción de estado.

---

### Contribuir
Si quieres contribuir:

1. Haz fork y crea una rama feature/nueva-funcionalidad
2. Añade tests para tu cambio
3. Envía un pull request describiendo claramente el cambio

---

### Licencia y autor
Autores: Keyberth Rengel, Nelver Vigos, Eduardo Ruiz
