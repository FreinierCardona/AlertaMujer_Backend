# AlertaMujer Backend

Backend monolítico de AlertaMujer construido con Java y Spring Boot. Este
repositorio contiene el arranque de la aplicación, su configuración base y
pruebas de contexto; aún no incluye endpoints ni lógica de negocio.

La aplicación consume exclusivamente el esquema PostgreSQL ya migrado por
`AlertaMujer_Database`. Todas las configuraciones usan variables de entorno,
`alertamujer_app`, `spring.liquibase.enabled=false` y
`spring.jpa.hibernate.ddl-auto=validate`: el Backend no ejecuta migraciones ni
crea, actualiza o elimina objetos de base de datos.

## Estructura modular

La aplicación usa una arquitectura **By Module + N capas**: cada módulo
funcional concentra sus capas `controller`, `dto`, `service`, `repository` y
`model`; `shared` concentra preocupaciones transversales. No se crean módulos
de negocio alternos ni capas genéricas.

La HU-API-003 establece este árbol bajo `com.alertamujer.backend`. Cada
directorio de capa contiene un `.gitkeep` hasta que incorpore su primera clase;
la estructura no contiene endpoints, entidades ni persistencia ficticia.

```text
src/main/java/com/alertamujer/
├── AlertaMujerApplication.java
└── backend/
    ├── shared/
    │   ├── config/
    │   ├── security/
    │   ├── errors/
    │   ├── validation/
    │   ├── audit/
    │   └── observability/
    ├── identity/
    │   ├── controller/
    │   ├── dto/request/
    │   ├── dto/response/
    │   ├── service/
    │   ├── service/impl/
    │   ├── repository/
    │   └── model/
    ├── contacts/
    │   ├── controller/
    │   ├── dto/request/
    │   ├── dto/response/
    │   ├── service/
    │   ├── service/impl/
    │   ├── repository/
    │   └── model/
    ├── emergency/
    │   ├── controller/
    │   ├── dto/request/
    │   ├── dto/response/
    │   ├── service/
    │   ├── service/impl/
    │   ├── repository/
    │   └── model/
    ├── notification/
    │   ├── controller/
    │   ├── dto/request/
    │   ├── dto/response/
    │   ├── service/
    │   ├── service/impl/
    │   ├── repository/
    │   ├── model/
    │   └── fcm/
    ├── evidence/
    │   ├── controller/
    │   ├── dto/request/
    │   ├── dto/response/
    │   ├── service/
    │   ├── service/impl/
    │   ├── repository/
    │   ├── model/
    │   └── storage/
    ├── chat/
    │   ├── controller/
    │   ├── dto/request/
    │   ├── dto/response/
    │   ├── service/
    │   ├── service/impl/
    │   ├── repository/
    │   ├── model/
    │   └── websocket/
    └── administration/
        ├── controller/
        ├── dto/request/
        ├── dto/response/
        ├── service/
        ├── service/impl/
        ├── repository/
        └── model/
```

| Paquete | Schemas y datos PostgreSQL que consumirá |
| --- | --- |
| `backend/shared` | `configuration.system_configuration` y `audit.audit_logs` para configuración y auditoría transversales. |
| `backend/identity` | `identity.registration_requests`, `users`, `user_credentials`, `user_verification_codes`, `user_sessions`; `profile.user_emergency_settings`. |
| `backend/contacts` | `contacts.emergency_contacts`. |
| `backend/emergency` | `emergency.emergencies`, `emergency_locations`, `emergency_status_history`. |
| `backend/notification` | `notification.user_device_tokens`, `emergency.emergency_notification_attempts`. |
| `backend/evidence` | `emergency.emergency_evidences`; el archivo queda fuera de PostgreSQL. |
| `backend/chat` | `emergency.emergency_chat_messages`. |
| `backend/administration` | Consultas autorizadas sobre `identity`, `emergency` y `audit.audit_logs`; no posee tablas propias. |

Responsabilidades obligatorias: un `controller` adapta HTTP y depende de un
`service`, nunca de un `repository`; un `repository` solo consulta PostgreSQL
y no decide permisos o transiciones; `service/impl` valida autorización y
estado, y delimita la transacción. Las entidades JPA no se exponen en DTOs.
Esta HU no crea rutas, entidades JPA, repositorios ni migraciones.

## Requisitos

- Java 21
- Acceso a Internet la primera vez que se ejecuta Maven Wrapper

El proyecto usa Spring Boot 4.1.1 y Maven Wrapper, por lo que no es necesario
instalar Maven de forma global.

## Ejecutar

En PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Para compilar y ejecutar las pruebas:

```powershell
.\mvnw.cmd clean verify
```

La configuración local exige una conexión PostgreSQL ya migrada. Liquibase está
deshabilitado y Hibernate valida el esquema existente al arrancar. No se
incluyen secretos: use `.env.example` solo como referencia y mantenga los
valores reales fuera del repositorio.

Para ejecutar la validación real de persistencia, suministre las tres variables
de datasource de `alertamujer_app` antes de ejecutar Maven. La prueba arranca
el contexto contra esa base, confirma los siete schemas funcionales y verifica
que el rol no puede crear objetos ni actualizar `databasechangelog`; no crea
fixtures ni altera datos de integración.

## Docker

El contenedor ejecuta exclusivamente el artefacto Backend. PostgreSQL y
Liquibase se inician y migran desde `AlertaMujer_Database`; este repositorio no
incluye ni inicia servicios de Base de Datos.

1. Aplique y valide primero la release de Base de Datos mediante su
   procedimiento aprobado.
2. Copie `.env.example` como `.env` y reemplace todos los marcadores. Use
   siempre `alertamujer_app` como `SPRING_DATASOURCE_USERNAME`.
3. Declare una URL JDBC alcanzable desde el contenedor. Para la configuración
   local por defecto, PostgreSQL se publica en el host y se usa
   `host.docker.internal:5434`.
4. Inicie solo el Backend:

```powershell
docker compose up --build
```

La salud técnica está disponible en `http://localhost:8080/actuator/health`.
El healthcheck de Compose comprueba esa ruta. La imagen falla antes de arrancar
si falta una variable obligatoria o si el usuario JDBC no es
`alertamujer_app`.

La ruta `EVIDENCE_STORAGE_PATH` se monta sobre un volumen nombrado persistente.
No se expone como ruta HTTP; el soporte de carga y descarga de evidencia se
incorporará en su HU correspondiente.

### Base de Datos en Docker

El Compose principal no presupone una red de Base de Datos. Si ambos
repositorios se ejecutan en Docker, use la red externa creada por el repositorio
de Base de Datos y una URL JDBC explícita:

```powershell
$env:DATABASE_NETWORK_NAME = 'alertamujer_db_network'
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://postgres:5433/alertamujer_db'
docker compose -f docker-compose.yml -f docker-compose.database-network.yml up --build
```

`DATABASE_NETWORK_NAME` debe coincidir con `POSTGRES_NETWORK_NAME` de
`AlertaMujer_Database`. Este comando no crea ni migra una base de datos.
