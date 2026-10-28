[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$environmentFile = Join-Path $repositoryRoot '.env'

if (-not (Test-Path -LiteralPath $environmentFile)) {
    throw 'Missing .env. Copy .env.example and set the application datasource values first.'
}

$configuration = @{}
Get-Content -LiteralPath $environmentFile | ForEach-Object {
    if ($_ -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $configuration[$matches[1]] = $matches[2]
    }
}

foreach ($name in @('SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace($configuration[$name])) {
        throw "Missing $name in .env."
    }
}

# Docker accede a PostgreSQL mediante host.docker.internal. Maven se ejecuta en el equipo anfitrión.
$env:SPRING_DATASOURCE_URL = $configuration['SPRING_DATASOURCE_URL'].Replace('host.docker.internal', '127.0.0.1')
$env:SPRING_DATASOURCE_USERNAME = $configuration['SPRING_DATASOURCE_USERNAME']
$env:SPRING_DATASOURCE_PASSWORD = $configuration['SPRING_DATASOURCE_PASSWORD']

# Esta clave exclusiva de pruebas evita que una clave local de Docker altere el perfil de pruebas.
$env:JWT_SECRET = 'test-only-hs256-secret-with-at-least-32-bytes'

Push-Location $repositoryRoot
try {
    & .\mvnw.cmd verify '-Dmaven.compiler.useIncrementalCompilation=false'
    if ($LASTEXITCODE -ne 0) { throw "Maven verify failed with exit code $LASTEXITCODE." }
} finally {
    Pop-Location
}
