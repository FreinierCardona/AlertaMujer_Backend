param([string]$DatabasePath = (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'AlertaMujer_Database'))
$ErrorActionPreference = 'Stop'
$taskDb = (Resolve-Path -LiteralPath $DatabasePath).Path
$taskBackend = Split-Path $PSScriptRoot -Parent
$taskProject = 'am-stage1-' + [Guid]::NewGuid().ToString('N').Substring(0, 10)
$taskVars = @{
  COMPOSE_PROJECT_NAME = $taskProject
  POSTGRES_CONTAINER_NAME = "$taskProject-postgres"
  POSTGRES_VOLUME_NAME = "$taskProject-data"
  POSTGRES_NETWORK_NAME = "$taskProject-network"
  POSTGRES_USER = 'postgres'
  POSTGRES_DB = 'alertamujer'
  POSTGRES_PASSWORD = [Guid]::NewGuid().ToString('N')
  POSTGRES_CONTAINER_PORT = '5433'
  POSTGRES_PORT = '0'
  APP_DB_USER = 'alertamujer_app'
  APP_DB_PASSWORD = [Guid]::NewGuid().ToString('N')
  MIGRATOR_DB_USER = 'alertamujer_migrator'
  MIGRATOR_DB_PASSWORD = [Guid]::NewGuid().ToString('N')
  ADMIN_BOOTSTRAP_ENABLED = 'false'
}
$taskPrevious = @{}
foreach ($key in @($taskVars.Keys) + @('SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD', 'SPRING_PROFILES_ACTIVE')) {
  $taskPrevious[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}
foreach ($entry in $taskVars.GetEnumerator()) { [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process') }
Push-Location $taskDb
try {
  docker compose --project-name $taskProject up -d postgres liquibase
  if ($LASTEXITCODE -ne 0) { throw 'Isolated database startup failed.' }
  $taskLiquibase = (docker compose --project-name $taskProject ps -a -q liquibase).Trim()
  $taskMigrationCode = docker wait $taskLiquibase
  if ($taskMigrationCode -ne '0') { throw 'Liquibase migration failed in the isolated database.' }
  $taskPort = (docker compose --project-name $taskProject port postgres 5433).Trim().Split(':')[-1]
  $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:$taskPort/alertamujer"
  $env:SPRING_DATASOURCE_USERNAME = 'alertamujer_app'
  $env:SPRING_DATASOURCE_PASSWORD = $taskVars.APP_DB_PASSWORD
  $env:SPRING_PROFILES_ACTIVE = 'test'
  Push-Location $taskBackend
  try {
    & .\mvnw.cmd -q test '-Dtest=RegistrationRequestPostgreSqlIntegrationTest,EmergencyPostgreSqlIntegrationTest,ChatPostgreSqlIntegrationTest,AdministrationHttpPostgreSqlIntegrationTest,ContactAlertHttpPostgreSqlIntegrationTest'
    $taskTestExit = $LASTEXITCODE
  } finally { Pop-Location }
  Write-Output "Isolated integration exit code: $taskTestExit"
  if ($taskTestExit -ne 0) { throw 'Integration tests failed.' }
} finally {
  # Solo se eliminan el proyecto recién creado y su volumen temporal identificado explícitamente.
  try { docker compose --project-name $taskProject down --volumes } finally {
    Pop-Location
    foreach ($entry in $taskPrevious.GetEnumerator()) { [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process') }
  }
}
