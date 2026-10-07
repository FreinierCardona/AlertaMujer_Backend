# Convención de commits

Este backend usa Conventional Commits con la historia de usuario como alcance. La estructura obligatoria es:

```text
<tipo>(hu-api-NNN): <resumen en inglés>
```

Reglas:

- Usa un solo propósito lógico por commit y no mezcles historias de usuario.
- El `tipo` permitido es: `feat`, `fix`, `refactor`, `test`, `docs`, `build` o `chore`.
- El alcance de una HU siempre es `hu-api-NNN`, en minúsculas y con tres dígitos.
- Escribe el resumen en inglés, en modo imperativo, minúsculas y sin punto final.
- Mantén el asunto en 72 caracteres o menos cuando sea posible.
- No uses `update`, `changes` o mensajes genéricos como resumen.

Ejemplos:

```text
feat(hu-api-009): implement authentication and revocable sessions
fix(hu-api-009): bound refresh token for bcrypt
refactor(hu-api-009): organize identity repository queries
test(hu-api-009): verify refresh rotation against PostgreSQL
docs(hu-api-009): document authentication endpoints
```

Para trabajo técnico que no pertenezca a una HU, usa el mismo formato con uno de estos alcances fijos:

```text
build(platform): configure backend build tooling
chore(platform): update development configuration
docs(backend): clarify local setup
```

Antes de crear un commit, confirma que los archivos incluidos pertenecen al mismo propósito y ejecuta las validaciones pertinentes. El texto de un PR debe describir el alcance, las validaciones ejecutadas y cualquier elemento fuera de alcance; no debe inventar commits ni resultados no realizados.
