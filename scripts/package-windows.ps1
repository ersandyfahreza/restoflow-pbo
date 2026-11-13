$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    $projectPom = [xml](Get-Content -LiteralPath 'pom.xml' -Raw)
    $appVersion = [string]$projectPom.project.version
    if ($appVersion -notmatch '^\d+\.\d+\.\d+$') { throw 'Versi aplikasi harus angka major.minor.patch.' }
    $destination = "dist/$appVersion"
    $packageRoot = "$destination/RestoFlow"
    if (Test-Path -LiteralPath $packageRoot) { throw "$packageRoot sudah ada. Pindahkan paket lama sebelum membuat paket baru." }
    & "$PSScriptRoot/maven.ps1" clean verify
    $moduleDir = Join-Path $projectRoot 'target/package-input/javafx'
    New-Item -ItemType Directory -Path $moduleDir -Force | Out-Null
    Get-ChildItem -LiteralPath (Join-Path $projectRoot 'target/package-input') -Filter 'javafx-*-win.jar' | ForEach-Object {
        Move-Item -LiteralPath $_.FullName -Destination $moduleDir
    }
    & jpackage --type app-image --name RestoFlow --app-version $appVersion `
        --input target/package-input --main-jar "restoflow-$appVersion.jar" `
        --java-options '--module-path=$APPDIR/javafx' --java-options '--add-modules=javafx.controls' `
        --main-class id.ac.restoflow.Launcher --dest $destination --vendor 'RestoFlow'
    if ($LASTEXITCODE -ne 0) { throw "jpackage gagal, exit code $LASTEXITCODE." }
    Copy-Item -LiteralPath 'docs/SUPABASE_SETUP.md' -Destination "$packageRoot/SUPABASE_SETUP.md"
    Copy-Item -LiteralPath 'supabase/migrations' -Destination "$packageRoot/database" -Recurse
    Copy-Item -LiteralPath 'supabase/verify.sql' -Destination "$packageRoot/database/verify.sql"
    Copy-Item -LiteralPath 'README.md' -Destination "$packageRoot/BACA-SAYA.md"
    Copy-Item -LiteralPath 'TESTING.md' -Destination "$packageRoot/TESTING.md"
    New-Item -ItemType Directory -Path "$packageRoot/docs" -Force | Out-Null
    Copy-Item -LiteralPath 'docs/UI_VERIFICATION.md' -Destination "$packageRoot/docs/UI_VERIFICATION.md"
    Copy-Item -LiteralPath 'DESIGN.md' -Destination "$packageRoot/DESIGN.md"
    Write-Host "Selesai: $packageRoot/RestoFlow.exe. Bagikan SELURUH folder RestoFlow."
} finally { Pop-Location }
