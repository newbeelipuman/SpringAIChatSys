param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env.local')
)

$ErrorActionPreference = 'Stop'
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$resolvedEnvFile = Resolve-Path $EnvFile

Get-Content -LiteralPath $resolvedEnvFile | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith('#')) {
        return
    }

    $parts = $line.Split('=', 2)
    if ($parts.Length -ne 2) {
        return
    }

    $name = $parts[0].Trim()
    $value = $parts[1].Trim()
    if ($name) {
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}

Set-Location $projectRoot
mvn spring-boot:run "-Dspring-boot.run.profiles=deepseek"
