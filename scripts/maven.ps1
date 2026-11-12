param([Parameter(ValueFromRemainingArguments = $true)][string[]]$MavenArgs)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolsDir = Join-Path $projectRoot '.tools'
$version = '3.9.11'
$mavenExe = Join-Path $toolsDir "apache-maven-$version/bin/mvn.cmd"
if (-not (Test-Path -LiteralPath $mavenExe)) {
    New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
    $archive = Join-Path $toolsDir "apache-maven-$version-bin.zip"
    $url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version/apache-maven-$version-bin.zip"
    Write-Host 'Mengunduh Maven resmi dan memverifikasi SHA-512...'
    Invoke-WebRequest -Uri $url -OutFile $archive
    $expected = (Invoke-WebRequest -Uri "$url.sha512").Content.Trim().Split(' ')[0]
    $actual = (Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash
    if ($actual -ine $expected) { throw 'Checksum Maven tidak cocok. Unduhan tidak akan diekstrak.' }
    Expand-Archive -LiteralPath $archive -DestinationPath $toolsDir -Force
}
Push-Location $projectRoot
try {
    & $mavenExe --batch-mode --no-transfer-progress "-Dmaven.repo.local=$toolsDir/repository" @MavenArgs
    if ($LASTEXITCODE -ne 0) { throw "Maven gagal, exit code $LASTEXITCODE." }
} finally { Pop-Location }
