# AlertaMujer Backend

Backend monolítico de AlertaMujer construido con **Java 21** y **Spring Boot
4.1.1**. Expone API REST, eventos WebSocket/STOMP y los flujos de identidad,
contactos, emergencias, evidencias, chat, notificaciones y administración.

Este repositorio consume el esquema PostgreSQL ya migrado por
`AlertaMujer_Database`. El backend usa el rol de mínimo privilegio
`alertamujer_app`, con `spring.liquibase.enabled=false` y
`spring.jpa.hibernate.ddl-auto=validate`: no ejecuta migraciones ni crea,
actualiza o elimina objetos de base de datos.

## Guía rápida

| Necesito... | Punto de partida |
| --- | --- |
| Entender la organización del código | [Estructura modular](#estructura-modular) y [Arquitectura y reglas de diseño](#arquitectura-y-reglas-de-diseño) |
| Ejecutar contra PostgreSQL local | [Ejecución local](#ejecución-local) |
| Iniciar la imagen | [Docker](#docker) |
| Conectar el frontend | [Integración con frontend](#integración-con-frontend) |
| Configurar OTP por correo o FCM | [Servicios externos](#servicios-externos) |
| Validar una entrega | [Pruebas y validación](#pruebas-y-validación) |

La especificación funcional y los contratos OpenAPI viven en la documentación
del producto. Este README describe el comportamiento implementado en este
repositorio y no sustituye esos contratos.

## Estructura modular

La aplicación usa una arquitectura **By Module + N capas**: cada módulo
funcional concentra sus capas `controller`, `dto`, `service`, `repository` y
`model`; `shared` concentra preocupaciones transversales. No se crean módulos
de negocio alternos ni capas genéricas.

El árbol bajo `com.alertamujer.backend` mantiene los límites de cada módulo.
No cree módulos de negocio alternos ni capas genéricas para evitar duplicar
responsabilidades.

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

## Requisitos

- Java 21
- Acceso a Internet la primera vez que se ejecuta Maven Wrapper

El proyecto usa Spring Boot 4.1.1 y Maven Wrapper, por lo que no es necesario
instalar Maven de forma global.

## Ejecución local

La aplicación local exige una base PostgreSQL ya migrada por
`AlertaMujer_Database`, normalmente publicada en `127.0.0.1:5434`. Hibernate
valida el esquema al arrancar; no lo crea ni lo migra.

1. Copie `.env.example` como `.env` y complete los secretos fuera de Git.
2. Use `alertamujer_app` como `SPRING_DATASOURCE_USERNAME`.
3. Cargue `.env` en la sesión de PowerShell. Spring Boot no lo carga de forma
   automática al ejecutar Maven:

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        Set-Item -Path "Env:$($matches[1])" -Value $matches[2]
    }
}

# Maven se ejecuta en Windows; host.docker.internal es solo para contenedores.
$env:SPRING_DATASOURCE_URL = $env:SPRING_DATASOURCE_URL.Replace(
    'host.docker.internal',
    '127.0.0.1'
)
```

4. Inicie el backend:

```powershell
.\mvnw.cmd spring-boot:run
```

La API queda en `http://localhost:8080/api/v1` y la salud técnica en
`http://localhost:8080/actuator/health`.

`JWT_SECRET` debe tener al menos 32 bytes. Cambiarlo invalida los access tokens
emitidos anteriormente. Para OTP por correo, el nombre correcto del remitente
es `SMTP_FROM`; use una contraseña de aplicación, no la contraseña normal de
Gmail, cuando el proveedor sea Gmail.

Una respuesta `503` de `/actuator/health` no implica necesariamente que REST o
PostgreSQL estén caídos: el indicador SMTP participa en la salud agregada. Un
SMTP inaccesible puede marcarla como `DOWN` aunque el backend haya iniciado y
la base esté conectada.

## Pruebas y validación

Para compilar y ejecutar las pruebas disponibles:

```powershell
.\mvnw.cmd verify '-Dmaven.compiler.useIncrementalCompilation=false'
```

En Windows/OneDrive, `clean` puede fallar si algún proceso bloquea `target`.
Cierre la instancia que lo usa antes de emplearlo; `verify` es una alternativa
útil para diagnosticar sin borrar el directorio.

Para repetir la evidencia de integración contra la base migrada, con fixtures
sintéticas y limpieza controlada:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-integration.ps1
```

El script lee solamente las tres variables de datasource de `.env`, adapta
`host.docker.internal` a `127.0.0.1` para Windows y usa un JWT efímero de
prueba. No modifica `.env`, no ejecuta Liquibase ni carga secretos SMTP, FCM o
de almacenamiento. Antes de entregar cambios, ejecute también:

```powershell
git diff --check
```

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
No se expone como directorio HTTP: las evidencias se sirven únicamente por los
endpoints autorizados del módulo `evidence`.

> **SMTP en Docker:** el `docker-compose.yml` actual no inyecta variables
> `SMTP_*` al contenedor. Para validar OTP por correo se puede usar la
> ejecución local anterior; un despliegue en contenedor debe proporcionar esas
> variables explícitamente al proceso. La presencia de `SMTP_*` en `.env` no
> las entrega automáticamente a la aplicación dentro del contenedor.

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

## Arquitectura y reglas de diseño

La aplicación usa una arquitectura por módulo funcional y capas:

```text
HTTP / STOMP
     │
     ▼
controller o adaptador WebSocket
     │  DTOs públicos; no expone entidades JPA
     ▼
service / service.impl
     │  autorización, reglas y transacciones
     ├───────────────────┬───────────────────┐
     ▼                   ▼                   ▼
repository          SMTP / FCM / WS      almacenamiento
PostgreSQL                               de evidencias
```

Respete estos límites al ampliar el producto:

- Los controllers adaptan el transporte; no consultan repositorios.
- Los services validan autorización, estados y reglas de negocio.
- Los repositories hacen persistencia; no resuelven permisos ni transiciones.
- Los DTO forman el contrato de la API. Entidades, hashes, OTP y secretos no
  se devuelven al cliente.
- Los eventos WebSocket se publican después del `commit`, no antes.
- DDL, Liquibase, grants y cambios de esquema pertenecen a
  `AlertaMujer_Database`, nunca a este repositorio.

### Módulos implementados

| Módulo | Responsabilidad principal |
| --- | --- |
| `identity` | Registro, OTP, login, refresh, logout, recuperación y perfil. |
| `contacts` | Directorio, invitaciones y contactos de emergencia. |
| `emergency` | SOS, recuperación de emergencia abierta, ubicaciones, heartbeat y cierre. |
| `chat` | Historial de mensajes y eventos STOMP por emergencia. |
| `notification` | Tokens de dispositivo, intentos de aviso y cliente FCM. |
| `evidence` | Metadatos PostgreSQL y contenido privado en disco. |
| `administration` | Dashboard, usuarios, atención de emergencias y auditoría. |
| `shared` | Seguridad, errores, configuración, auditoría y observabilidad. |

## Integración con frontend

Todas las rutas REST comienzan en `/api/v1`. El contrato OpenAPI es la fuente
para cuerpos, restricciones y códigos HTTP; este resumen sirve para ubicar las
áreas del backend:

| Área | Rutas principales | Acceso |
| --- | --- | --- |
| Identidad | `/registration-requests`, `/auth/login`, `/auth/refresh`, `/auth/logout`, recuperación de contraseña | Registro, login, refresh y recuperación son públicos; otras acciones dependen del flujo. |
| Perfil | `/me`, términos, contacto y preferencias SOS | JWT. |
| Contactos | `/directory`, `/contacts`, `/contact-invitations` | JWT. |
| Emergencias | `/emergencies`, `active`, `heartbeats`, `locations`, `finish`, `/me/emergencies` | JWT y pertenencia a la emergencia. |
| Chat | `/emergencies/{emergencyId}/messages` | JWT y autorización sobre la emergencia. |
| Evidencias | rutas de evidencia bajo emergencia y `/evidences/{evidenceId}/content` | JWT y autorización. |
| Dispositivos | `POST /me/device-tokens` | JWT. |
| Administración | `/admin/**` | Rol `ENTITY_ADMIN`. |

En rutas protegidas, el frontend debe enviar:

```http
Authorization: Bearer <access-token>
```

El backend valida la firma HS256, issuer, audiencia y el estado vigente de la
sesión en PostgreSQL. Un token firmado no concede acceso si su sesión fue
revocada. Las respuestas de error tienen `code`, `message`, `requestId` y,
cuando corresponde, `fields`. Envíe un UUID en `X-Request-Id` para correlacionar
una interacción con logs; si no se envía, el backend genera uno.

### URLs de desarrollo

| Cliente | URL base REST |
| --- | --- |
| Web en el mismo equipo | `http://localhost:8080/api/v1` |
| Emulador Android | `http://10.0.2.2:8080/api/v1` |
| Teléfono físico | `http://<IP-LAN-DEL-EQUIPO>:8080/api/v1` |

Para tiempo real, el endpoint es `ws://<host>:8080/ws`. El cliente STOMP usa
`/app/emergencies/{emergencyId}/messages` para enviar mensajes y se suscribe a
`/topic/emergencies/{emergencyId}` para mensajes y cambios de estado ya
confirmados.

### CORS

El backend aplica `CORS_ALLOWED_ORIGINS` a todas las rutas. La lista se separa
por comas y permite únicamente los orígenes configurados; no usa comodines ni
credenciales cross-site. Se aceptan los métodos REST habituales y preflight,
y se autorizan los encabezados `Authorization`, `Content-Type` y
`X-Request-Id`. Este último se expone al navegador para correlacionar errores.

La configuración local incluida corresponde al frontend actual:

```text
http://localhost:5173
http://127.0.0.1:5173
http://localhost:4173
http://127.0.0.1:4173
```

Las aplicaciones móviles nativas no dependen de CORS. Para otro entorno web,
agregue su origen exacto —esquema, host y puerto— a la variable sin usar `*`.

## Servicios externos

### SMTP y OTP

El backend usa `JavaMailSender` para OTP de registro, recuperación y cambios
de contacto. El envío es síncrono: si el proveedor rechaza autenticación o
conexión, el endpoint devuelve error. Una respuesta exitosa confirma que SMTP
aceptó el mensaje; la entrega final debe comprobarse en la bandeja de entrada
o spam del destinatario.

Para Gmail use `smtp.gmail.com`, puerto `587`, STARTTLS y una contraseña de
aplicación tras activar la verificación en dos pasos. No se necesita dominio o
servidor propio. El email de recuperación responde de forma genérica aunque
no exista la cuenta, para evitar revelar identidades registradas.

### Firebase Cloud Messaging

FCM se integra mediante HTTP v1 y un JSON de cuenta de servicio. El token real
del dispositivo se registra con `POST /me/device-tokens`; nunca debe ir a Git,
logs o documentación. El Compose monta la credencial como solo lectura en
`/run/secrets/alertamujer-fcm.json`.

Un estado `SENT_TO_FCM` o `FCM_ACCEPTED` significa que Firebase aceptó el
mensaje, no que la persona lo leyó. La validación completa requiere un token
de un dispositivo real y verificar la notificación visible.

### Evidencias

Los metadatos se guardan en PostgreSQL y los binarios en
`EVIDENCE_STORAGE_PATH`. Mantenga esa ruta fuera de directorios públicos. La
limpieza de archivos ocurre después del `commit` para evitar borrar evidencia
si la transacción de base de datos falla.

## Operación administrativa local

El reemplazo del único `ENTITY_ADMIN` no tiene endpoint. En un arranque con
perfil `local`, defina temporalmente `ADMIN_REPLACEMENT_TARGET_USER_ID` con el
UUID de una cuenta `USER` habilitada y verificada. El proceso revoca sesiones
administrativas, degrada al administrador saliente y promueve el destino en una
transacción. Retire la variable al terminar.

## Notas de seguridad y contribución

- No registre `.env`, contraseñas de aplicación, JWT, tokens FCM, tokens de
  sesión ni el JSON de Firebase.
- Use siempre `alertamujer_app`; no conecte como propietario, superusuario o
  usuario de migración.
- Los códigos OTP permanecen protegidos; no se imprimen ni se exponen por API.
- Mantenga cambios limitados a la historia de usuario y al módulo que los
  requiere. Evite rediseñar contratos o agregar abstracciones no solicitadas.
- Formatee SQL verticalmente por flujo de negocio y conserve las restricciones
  de concurrencia que ya ofrece PostgreSQL.
- Los commits asociados a una HU usan
  `<type>(hu-api-NNN): <English imperative lowercase summary>`.
