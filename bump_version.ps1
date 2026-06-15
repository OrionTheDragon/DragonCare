# bump_version.ps1
# Master Version management script for DragonCare mod

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "        DragonCare Version Manager" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Step 1: Select Game Version" -ForegroundColor White
Write-Host "1. 1.20.1" -ForegroundColor Gray
Write-Host "2. 1.21.1" -ForegroundColor Gray
Write-Host "3. Exit" -ForegroundColor Gray
Write-Host ""

$gameChoice = ""
while ($gameChoice -notmatch "^[1-3]$") {
    $gameChoice = Read-Host "Your choice [1-3]"
}

if ($gameChoice -eq "3") {
    Write-Host "Exited." -ForegroundColor Yellow
    exit
}

$targetDir = ""
$gameSuffix = ""
$mcVersion = ""

if ($gameChoice -eq "1") {
    $targetDir = "Addon 1.20.1"
    $gameSuffix = "1.20.1v"
    $mcVersion = "1.20.1"
} else {
    $targetDir = "Addon"
    $gameSuffix = "1.21.1v"
    $mcVersion = "1.21.1"
}

$propertiesPath = Join-Path (Join-Path $PSScriptRoot $targetDir) "gradle.properties"

if (-not (Test-Path $propertiesPath)) {
    Write-Host "Error: gradle.properties not found at $propertiesPath!" -ForegroundColor Red
    exit
}

# Read file
$lines = Get-Content $propertiesPath
$versionLineIndex = -1
$archivesNameLineIndex = -1
$currentFullVersion = ""

for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match "^mod_version\s*=\s*(.+)$") {
        $versionLineIndex = $i
        $currentFullVersion = $Matches[1].Trim()
    }
    if ($lines[$i] -match "^archives_name\s*=\s*(.+)$") {
        $archivesNameLineIndex = $i
    }
}

if ($versionLineIndex -eq -1) {
    Write-Host "Error: Could not find mod_version in gradle.properties!" -ForegroundColor Red
    exit
}

# Extract just the addon version number (e.g. 1.0.0 from 1.0.0 - 1.20.1v)
$currentNumber = $currentFullVersion
if ($currentFullVersion -match "^(.*?)\s*-\s*$gameSuffix$") {
    $currentNumber = $Matches[1].Trim()
}

Write-Host ""
Write-Host "Selected Game Version: $mcVersion" -ForegroundColor Green
Write-Host "Current Addon Version: $currentNumber" -ForegroundColor Yellow
Write-Host ""
Write-Host "Step 2: Select how to bump the version" -ForegroundColor White

$nextPatch = ""
$nextMinor = ""
$nextMajor = ""

if ($currentNumber -match "^(\d+)\.(\d+)\.(\d+)$") {
    $major = [int]$Matches[1]
    $minor = [int]$Matches[2]
    $patch = [int]$Matches[3]
    
    $nextPatch = "$major.$minor.$($patch + 1)"
    $nextMinor = "$major.$($minor + 1).0"
    $nextMajor = "$($major + 1).0.0"
}

if ($nextPatch) {
    Write-Host "1. Patch (-> $nextPatch)" -ForegroundColor Gray
    Write-Host "2. Minor (-> $nextMinor)" -ForegroundColor Gray
    Write-Host "3. Major (-> $nextMajor)" -ForegroundColor Gray
    Write-Host "4. Custom input" -ForegroundColor Gray
    Write-Host ""
    
    $addonNumber = ""
    while ($addonNumber -eq "") {
        $choice = Read-Host "Your choice [1-4]"
        if ($choice -eq "1") { $addonNumber = $nextPatch }
        elseif ($choice -eq "2") { $addonNumber = $nextMinor }
        elseif ($choice -eq "3") { $addonNumber = $nextMajor }
        elseif ($choice -eq "4") {
            while ($addonNumber -eq "") {
                $addonNumber = Read-Host "Enter custom version (e.g. 1.0.1)"
                $addonNumber = $addonNumber.Trim()
            }
        }
    }
} else {
    Write-Host "Could not parse current version format. Proceeding to custom input." -ForegroundColor Yellow
    $addonNumber = ""
    while ($addonNumber -eq "") {
        $addonNumber = Read-Host "Enter new Addon Version (e.g. 1.0.1)"
        $addonNumber = $addonNumber.Trim()
    }
}

$newFullVersion = "$addonNumber - $gameSuffix"

# Update lines
$lines[$versionLineIndex] = "mod_version=$newFullVersion"
if ($archivesNameLineIndex -ne -1) {
    $lines[$archivesNameLineIndex] = "archives_name=Ice and Fire - Dragon Care"
}

# Write back as UTF-8 (No BOM)
[System.IO.File]::WriteAllLines($propertiesPath, $lines, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ""
Write-Host "Success! Updated gradle.properties in $targetDir" -ForegroundColor Green
Write-Host "New Full Version: Ice and Fire - Dragon Care - $newFullVersion" -ForegroundColor Green
Write-Host ""

# Ask to compile
$buildChoice = Read-Host "Do you want to build the mod now? [Y/n]"
if ($buildChoice -eq "" -or $buildChoice.ToLower() -eq "y" -or $buildChoice.ToLower() -eq "yes") {
    Write-Host "Launching build process..." -ForegroundColor Cyan
    
    $targetDirPath = Join-Path $PSScriptRoot $targetDir
    Set-Location $targetDirPath
    
    if (Test-Path "gradlew.bat") {
        & .\gradlew.bat build -x test
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host ""
            Write-Host "=============================================" -ForegroundColor Green
            Write-Host "BUILD SUCCESSFUL!" -ForegroundColor Green
            
            # Find built jar
            $buildLibsDir = Join-Path $targetDirPath "build\libs"
            if (Test-Path $buildLibsDir) {
                # Rename the file to ensure EXACT spacing
                $originalName = "Ice and Fire - Dragon Care-$newFullVersion.jar"
                $exactName = "Ice and Fire - Dragon Care - $newFullVersion.jar"
                
                $originalPath = Join-Path $buildLibsDir $originalName
                $exactPath = Join-Path $buildLibsDir $exactName
                
                if (Test-Path $originalPath) {
                    Rename-Item -Path $originalPath -NewName $exactName -Force
                }
                
                if (Test-Path $exactPath) {
                    Write-Host "Output file:" -ForegroundColor White
                    Write-Host "  $exactPath" -ForegroundColor Yellow
                } else {
                    $jars = Get-ChildItem $buildLibsDir -Filter "*.jar" | Where-Object { -not $_.Name.Contains("-sources") -and -not $_.Name.Contains("-dev") }
                    foreach ($jar in $jars) {
                        Write-Host "  $($jar.FullName)" -ForegroundColor Yellow
                    }
                }
            }
            Write-Host "=============================================" -ForegroundColor Green
        } else {
            Write-Host ""
            Write-Host "Error: Build failed with exit code $LASTEXITCODE." -ForegroundColor Red
        }
    } else {
        Write-Host "Error: gradlew.bat not found in $targetDir!" -ForegroundColor Red
    }
}
