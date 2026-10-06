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
