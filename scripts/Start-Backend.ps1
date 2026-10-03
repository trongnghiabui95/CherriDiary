param([switch]$StartDatabase)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $projectRoot '.env'
$allowedKeys = @('DB_URL', 'DB_USER', 'DB_PASSWORD', 'JWT_SECRET', 'ADMIN_USERNAME', 'ADMIN_PASSWORD', 'UPLOAD_DIR', 'SPRING_PROFILES_ACTIVE')
if (Test-Path -LiteralPath $configPath) {
    foreach ($line in Get-Content -LiteralPath $configPath -Encoding UTF8) {
        $clean = $line.Trim()
        if ($clean -eq '' -or $clean.StartsWith('#')) { continue }
        $separator = $clean.IndexOf('=')
        if ($separator -lt 1) { throw 'Invalid .env entry' }
        $key = $clean.Substring(0, $separator).Trim()
        if ($key -notin $allowedKeys) { throw "Unsupported .env key: $key" }
        $value = $clean.Substring($separator + 1).Trim().Trim([char]34).Trim([char]39)
        if (-not [Environment]::GetEnvironmentVariable($key, 'Process')) {
            [Environment]::SetEnvironmentVariable($key, $value, 'Process')
        }
    }
}
if (-not $env:DB_PASSWORD -or -not $env:JWT_SECRET) { throw 'Set DB_PASSWORD and JWT_SECRET, or run Initialize-DevConfig.ps1 first.' }
if (-not $env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE = 'local' }
$backendPort = 8080
if ($env:SERVER_PORT) { $backendPort = [int]$env:SERVER_PORT }
$serverListeners = @(Get-NetTCPConnection -LocalPort $backendPort -State Listen -ErrorAction SilentlyContinue)
if ($serverListeners.Count -gt 0) {
    $listenerIds = ($serverListeners | Select-Object -ExpandProperty OwningProcess -Unique) -join ', '
    throw "Port $backendPort is already in use by PID $listenerIds. If the backend is already running, use that instance. To restart, stop its terminal with Ctrl+C first."
}
Push-Location $projectRoot
try {
    if ($StartDatabase) {
        if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker is required for -StartDatabase. Alternatively configure an existing PostgreSQL database.' }
        & docker compose up -d --wait postgres-db
        if ($LASTEXITCODE -ne 0) { throw 'Database startup failed' }
    }
    & (Join-Path $projectRoot 'gradlew.bat') ':backend:bootRun' '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed. See the Spring Boot ERROR / Caused by message above for the root cause.' }
} finally { Pop-Location }
