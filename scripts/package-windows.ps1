$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    if (Test-Path -LiteralPath 'dist/1.0.0/RestoFlow') {
        throw 'dist/1.0.0/RestoFlow sudah ada. Pindahkan paket lama sebelum membuat paket baru.'
    }
    & "$PSScriptRoot/maven.ps1" clean verify
    $moduleDir = Join-Path $projectRoot 'target/package-input/javafx'
    New-Item -ItemType Directory -Path $moduleDir -Force | Out-Null
    Get-ChildItem -LiteralPath (Join-Path $projectRoot 'target/package-input') -Filter 'javafx-*-win.jar' | ForEach-Object {
        Move-Item -LiteralPath $_.FullName -Destination $moduleDir
    }
    & jpackage --type app-image --name RestoFlow --app-version 1.0.0 `
        --input target/package-input --main-jar restoflow-1.0.0.jar `
        --java-options '--module-path=$APPDIR/javafx' --java-options '--add-modules=javafx.controls' `
        --main-class id.ac.restoflow.Launcher --dest dist/1.0.0 --vendor 'RestoFlow'
    if ($LASTEXITCODE -ne 0) { throw "jpackage gagal, exit code $LASTEXITCODE." }
    Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/SUPABASE_SETUP.md') -Destination 'dist/1.0.0/RestoFlow/SUPABASE_SETUP.md'
    Copy-Item -LiteralPath (Join-Path $projectRoot 'supabase/migrations') -Destination 'dist/1.0.0/RestoFlow/database' -Recurse
    Copy-Item -LiteralPath (Join-Path $projectRoot 'supabase/verify.sql') -Destination 'dist/1.0.0/RestoFlow/database/verify.sql'
    Copy-Item -LiteralPath (Join-Path $projectRoot 'README.md') -Destination 'dist/1.0.0/RestoFlow/BACA-SAYA.md'
    Copy-Item -LiteralPath (Join-Path $projectRoot 'TESTING.md') -Destination 'dist/1.0.0/RestoFlow/TESTING.md'
    Write-Host 'Selesai: dist/1.0.0/RestoFlow/RestoFlow.exe. Bagikan SELURUH folder RestoFlow.'
} finally { Pop-Location }
