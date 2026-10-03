param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $configPath) {
    Write-Output '.env already exists; no changes made.'
    exit 0
}
function New-LocalSecret {
    param([int]$ByteCount = 32)
    $bytes = New-Object byte[] $ByteCount
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [Convert]::ToBase64String($bytes)
}
$databasePassword = '123456'
$tokenSecret = New-LocalSecret
$adminPassword = New-LocalSecret
$config = "DB_URL=jdbc:postgresql://localhost:5432/cherridiary_db`nDB_USER=admin`nDB_PASSWORD=$databasePassword`nJWT_SECRET=$tokenSecret`nADMIN_USERNAME=admin`nADMIN_PASSWORD=$adminPassword`n"
[System.IO.File]::WriteAllText($configPath, $config, [System.Text.UTF8Encoding]::new($false))
Write-Output 'Created .env. Read ADMIN_PASSWORD from that file to sign in. Secrets were not printed.'
