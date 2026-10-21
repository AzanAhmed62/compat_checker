#Requires -Version 5.1
<#
.SYNOPSIS
  Rebuilds a disposable PostgreSQL database and runs the SQL/RLS test suite.

.DESCRIPTION
  This is the Docker-free path for validating the Supabase migrations. It
  creates a throwaway database, applies the plain-PostgreSQL Supabase shim, the
  ordered migrations, the deterministic seed, and every SQL test file.

  Never point this script at a remote or production database.

.PARAMETER Database
  Disposable database name. Default: compat_test.

.PARAMETER Port
  Local PostgreSQL port. Default: 55432 (or $env:PGPORT / 5432).

.PARAMETER User
  PostgreSQL superuser used to apply migrations. Default: postgres.

.PARAMETER HostName
  PostgreSQL host. Default: localhost.

.PARAMETER Keep
  Keep the database after a successful run for inspection.
#>
[CmdletBinding()]
param(
  [string]$Database = "compat_test",
  [int]$Port = $(if ($env:PGPORT) { [int]$env:PGPORT } else { 55432 }),
  [string]$User = $(if ($env:PGUSER) { $env:PGUSER } else { 'postgres' }),
  [string]$HostName = $(if ($env:PGHOST) { $env:PGHOST } else { 'localhost' }),
  [switch]$Keep
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$migrationDir = Join-Path $repoRoot 'supabase/migrations'
$testDir = Join-Path $repoRoot 'supabase/tests'

if ($Database -notmatch '^compat_[a-z0-9_]+$') {
  throw "Refusing unsafe database name '$Database'. Use a compat_* disposable database."
}

function Resolve-Psql {
  if ($env:PSQL -and (Test-Path $env:PSQL)) { return $env:PSQL }
  $cmd = Get-Command psql -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  $candidate = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
  if (Test-Path $candidate) { return $candidate }
  throw 'psql was not found. Install PostgreSQL client tools or set $env:PSQL.'
}

$psql = Resolve-Psql
$dropdb = Join-Path (Split-Path $psql) 'dropdb.exe'
$createdb = Join-Path (Split-Path $psql) 'createdb.exe'
if (-not (Test-Path $dropdb)) { $dropdb = 'dropdb' }
if (-not (Test-Path $createdb)) { $createdb = 'createdb' }

$common = @('-X', '-q', '-v', 'ON_ERROR_STOP=1', '-h', $HostName, '-p', $Port, '-U', $User)
$log = Join-Path $env:TEMP "compat-db-test-$([guid]::NewGuid().ToString('N')).log"
$completed = $false

function Invoke-PsqlFile {
  param([string]$Path, [string]$DatabaseName)
  $previous = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try {
    $output = & $psql @common -d $DatabaseName -f $Path 2>&1
    $code = $LASTEXITCODE
  }
  finally {
    $ErrorActionPreference = $previous
  }
  Add-Content -LiteralPath $log -Value $output
  if ($code -ne 0) {
    Write-Host "`n--- output ($([IO.Path]::GetFileName($Path))) ---" -ForegroundColor DarkGray
    $output | Select-Object -Last 25 | ForEach-Object { Write-Host $_ }
    throw "SQL failed: $([IO.Path]::GetRelativePath($repoRoot, $Path))"
  }
}

Write-Host "Rebuilding disposable database '$Database' on $HostName`:$Port ..." -ForegroundColor Cyan
$previous = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
  & $dropdb @('-h', $HostName, '-p', $Port, '-U', $User, '--if-exists', '--force', $Database) 2>&1 | Out-Null
  $dropCode = $LASTEXITCODE
  if ($dropCode -eq 0) {
    & $createdb @('-h', $HostName, '-p', $Port, '-U', $User, $Database) 2>&1 | Out-Null
    $createCode = $LASTEXITCODE
  }
} finally {
  $ErrorActionPreference = $previous
}
if ($dropCode -ne 0) { throw 'dropdb failed' }
if ($createCode -ne 0) { throw 'createdb failed' }

try {
  Invoke-PsqlFile (Join-Path $testDir '00_shim.sql') $Database

  $migrations = Get-ChildItem -LiteralPath $migrationDir -Filter '*.sql' | Sort-Object Name
  if ($migrations.Count -eq 0) { throw 'No migrations found.' }
  foreach ($migration in $migrations) {
    Write-Host "  apply $($migration.Name)" -ForegroundColor DarkGray
    Invoke-PsqlFile $migration.FullName $Database
  }

  Write-Host '  apply seed.sql' -ForegroundColor DarkGray
  Invoke-PsqlFile (Join-Path $repoRoot 'supabase/seed.sql') $Database

  Invoke-PsqlFile (Join-Path $testDir '01_setup.sql') $Database

  $tests = Get-ChildItem -LiteralPath $testDir -Filter '*.sql' |
    Where-Object { $_.Name -match '^\d\d_' -and $_.Name -notin @('00_shim.sql', '01_setup.sql') } |
    Sort-Object Name
  if ($tests.Count -eq 0) { throw 'No tests found.' }

  $failed = 0
  foreach ($test in $tests) {
    Write-Host "  test  $($test.Name)" -ForegroundColor Yellow
    try {
      Invoke-PsqlFile $test.FullName $Database
      Write-Host "        PASS" -ForegroundColor Green
    }
    catch {
      $failed++
      Write-Host "        FAIL" -ForegroundColor Red
    }
  }

  if ($failed -gt 0) {
    Write-Host "`n$failed test file(s) failed. Full transcript: $log" -ForegroundColor Red
    exit 1
  }

  Write-Host "`nAll $($tests.Count) SQL test files passed." -ForegroundColor Green
  $completed = $true
}
finally {
  if ($completed -and -not $Keep) {
    $dropErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
      & $dropdb @('-h', $HostName, '-p', $Port, '-U', $User, '--if-exists', '--force', $Database) 2>&1 | Out-Null
    } finally {
      $ErrorActionPreference = $dropErrorPreference
    }
  }
  else {
    Write-Host "Database '$Database' kept for inspection. Full transcript: $log" -ForegroundColor Cyan
  }
}
