# AlertaMujer Backend

Backend monolí­tico de AlertaMujer construido con Java y Spring Boot. Este
repositorio contiene el arranque de la aplicación, su configuración base y
pruebas de contexto; aún no incluye endpoints ni lógica de negocio.

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

La configuración local inicia sin conexión a base de datos. Liquibase está
deshabilitado y Hibernate queda configurado para validar el esquema cuando se
incorpore una conexión PostgreSQL ya migrada. No se incluyen secretos:
use `.env.example` solo como referencia y mantenga los valores reales fuera
del repositorio.

## Docker

El contenedor ejecuta exclusivamente el artefacto Backend. PostgreSQL y
Liquibase se inician y migran desde `AlertaMujer_Database`; este repositorio no
incluye ni inicia servicios de Base de Datos.

1. Aplique y valide primero la release de Base de Datos mediante su
   procedimiento aprobado.
2. Copie `.env.example` como `.env` y reemplace todos los marcadores. Use
   siempre `alertamujer_app` como `SPRING_DATASOURCE_USERNAME`.
3. Declare una URL JDBC alcanzable desde el contenedor. Para la configuraciÃ³n
   local por defecto, PostgreSQL se publica en el host y se usa
   `host.docker.internal:5434`.
4. Inicie solo el Backend:

```powershell
docker compose up --build
```

La salud tÃ©cnica estÃ¡ disponible en `http://localhost:8080/actuator/health`.
El healthcheck de Compose comprueba esa ruta. La imagen falla antes de arrancar
si falta una variable obligatoria o si el usuario JDBC no es
`alertamujer_app`.

La ruta `EVIDENCE_STORAGE_PATH` se monta sobre un volumen nombrado persistente.
No se expone como ruta HTTP; el soporte de carga y descarga de evidencia se
incorporarÃ¡ en su HU correspondiente.

### Base de Datos en Docker

El Compose principal no presupone una red de Base de Datos. Si ambos
repositorios se ejecutan en Docker, use la red externa creada por el repositorio
de Base de Datos y una URL JDBC explÃ­cita:

```powershell
$env:DATABASE_NETWORK_NAME = 'alertamujer_db_network'
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://postgres:5433/alertamujer_db'
docker compose -f docker-compose.yml -f docker-compose.database-network.yml up --build
```

`DATABASE_NETWORK_NAME` debe coincidir con `POSTGRES_NETWORK_NAME` de
`AlertaMujer_Database`. Este comando no crea ni migra una base de datos.
