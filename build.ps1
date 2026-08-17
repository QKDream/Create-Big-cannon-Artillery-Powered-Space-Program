$ErrorActionPreference = "Stop"
$javaBin = "D:\.minecraft\runtime\java-runtime-delta\bin"
$javac = Join-Path $javaBin "javac.exe"
$jar = Join-Path $javaBin "jar.exe"
$libs = "D:\.minecraft\libraries"
$ws = Split-Path -Parent $MyInvocation.MyCommand.Path

# Locate the game instance that contains both CBCMW and CBCMS jars (avoids Chinese literals).
$instanceDir = $null
foreach ($dir in (Get-ChildItem "D:\.minecraft\versions" -Directory)) {
    $modsDir = Join-Path $dir.FullName "mods"
    if (-not (Test-Path $modsDir)) { continue }
    $cbcJarProbe = Get-ChildItem $modsDir -Filter "*.jar" | Where-Object { $_.Name -like "*createbigcannons*5.11.7*" } | Select-Object -First 1
    if ((Test-Path (Join-Path $modsDir "cbcmodernwarfare-0.0.6v+mc.1.21.1-neoforge.jar")) -and
        (Test-Path (Join-Path $modsDir "CBC-Military-Supplement-1.21.1-2.1.0.jar")) -and
        (Test-Path (Join-Path $modsDir "big_cannons_aeronautics_fix-0.1-Alpha.jar")) -and
        ($null -ne $cbcJarProbe)) {
        $instanceDir = $dir.FullName
        $modsFolder = $modsDir
        break
    }
}
if (-not $instanceDir) { throw "Could not find the game instance with cbcmodernwarfare-0.0.6v and CBC-Military-Supplement" }
Write-Host "Instance: $instanceDir"

$cp = @()

# 1. NeoForge
$nfDir = "$libs\net\neoforged\neoforge\21.1.233"
foreach ($f in @("neoforge-21.1.233-client.jar", "neoforge-21.1.233-universal.jar")) {
    $p = Join-Path $nfDir $f
    if (Test-Path $p) { $cp += $p }
}

# 2. Minecraft client jars
$mcDir = Get-ChildItem "$libs\net\minecraft\client" -Directory | Where-Object { $_.Name -like "1.21.1*" } | Select-Object -First 1
if ($mcDir) {
    Get-ChildItem $mcDir.FullName -Filter "*.jar" | ForEach-Object { $cp += $_.FullName }
}

# 3. FML loader + bus + distmarker + serialization
foreach ($p in @("$libs\net\neoforged\fancymodloader\loader\4.0.42\loader-4.0.42.jar",
                 "$libs\net\neoforged\bus\8.0.5\bus-8.0.5.jar",
                 "$libs\net\neoforged\mergetool\2.0.0\mergetool-2.0.0-api.jar",
                 "$libs\com\mojang\datafixerupper\6.0.6\datafixerupper-6.0.6.jar",
                 "$libs\com\mojang\brigadier\1.3.10\brigadier-1.3.10.jar",
                 "$libs\org\slf4j\slf4j-api\2.0.17\slf4j-api-2.0.17.jar",
                 "$libs\net\fabricmc\sponge-mixin\0.15.2+mixin.0.8.7\sponge-mixin-0.15.2+mixin.0.8.7.jar")) {
    if (Test-Path $p) { $cp += $p }
}

# 4. Mods needed for compilation
$createJar = Get-ChildItem $modsFolder -Filter "*.jar" | Where-Object { $_.Name -like "*create-1.21.1*.jar" -and $_.Name -notlike "*bigcannons*" } | Select-Object -First 1
$cbcJar = Get-ChildItem $modsFolder -Filter "*.jar" | Where-Object { $_.Name -like "*createbigcannons*5.11.7*" } | Select-Object -First 1
$cbcmsJar = Get-ChildItem $modsFolder -Filter "*.jar" | Where-Object { $_.Name -like "CBC-Military-Supplement*" } | Select-Object -First 1
$cbcmwJar = Get-ChildItem $modsFolder -Filter "*.jar" | Where-Object { $_.Name -like "cbcmodernwarfare-0.0.6v*" } | Select-Object -First 1
$rplJar = Get-ChildItem $modsFolder -Filter "*.jar" | Where-Object { $_.Name -like "ritchiesprojectilelib*" } | Select-Object -First 1
foreach ($j in @($createJar, $cbcJar, $cbcmsJar, $cbcmwJar, $rplJar)) {
    if ($j) { $cp += $j.FullName }
}

# 5. Registrate and Ponder are jar-in-jar'd inside Create. Extract and unpack them into
#    directories on the classpath (directory entries avoid javac zipfs close issues on Windows).
$flatBase = Join-Path $ws "build\flatlibs"
$flatClasses = Join-Path $ws "build\flatclasses"
New-Item -ItemType Directory -Force $flatBase | Out-Null

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipArchive = [System.IO.Compression.ZipFile]::OpenRead($createJar.FullName)
try {
    foreach ($nestedName in @("META-INF/jarjar/Registrate-MC1.21-1.3.0+67.jar", "META-INF/jarjar/ponder-neoforge-1.0.82+mc1.21.1.jar")) {
        $entry = $zipArchive.GetEntry($nestedName)
        if ($entry) {
            $flatName = Split-Path $nestedName -Leaf
            $flatPath = Join-Path $flatBase $flatName
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $flatPath, $true)
            Set-ItemProperty -Path $flatPath -Name IsReadOnly -Value $false
            $unpackDir = Join-Path $flatClasses ($flatName + "_unpacked")
            if (Test-Path $unpackDir) { Remove-Item -Recurse -Force $unpackDir }
            [System.IO.Compression.ZipFile]::ExtractToDirectory($flatPath, $unpackDir)
            $cp += $unpackDir
        }
    }
} finally {
    $zipArchive.Dispose()
}

$classpath = ($cp | Select-Object -Unique) -join ";"
Write-Host "Classpath entries: $($cp.Count)"

$srcDir = Join-Path $ws "src\main\java"
$outDir = Join-Path $ws "build\classes"
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Force $outDir | Out-Null

$javaFiles = Get-ChildItem -Path $srcDir -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
Write-Host "Compiling $($javaFiles.Count) Java files..."
$javacArgs = @("-d", $outDir, "-cp", $classpath, "-encoding", "UTF-8", "-proc:none") + $javaFiles
& $javac @javacArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "COMPILATION FAILED!"
    exit 1
}
Write-Host "Compilation OK!"

$jarTmp = Join-Path $ws "build\jar_tmp"
if (Test-Path $jarTmp) { Remove-Item -Recurse -Force $jarTmp }
New-Item -ItemType Directory -Force $jarTmp | Out-Null
Copy-Item -Recurse -Force "$outDir\*" $jarTmp
Copy-Item -Recurse -Force "$ws\src\main\resources\META-INF" $jarTmp
Copy-Item -Force "$ws\src\main\resources\pack.mcmeta" $jarTmp
Copy-Item -Force "$ws\src\main\resources\cbcmsmwcompat.mixins.json" $jarTmp
Copy-Item -Force "$ws\src\main\resources\logo.png" $jarTmp


$jarOut = Join-Path $ws "cbcmsmwcompat-1.2.0.jar"
if (Test-Path $jarOut) { Remove-Item -Force $jarOut }
Push-Location $jarTmp
& $jar cf $jarOut "*"
Pop-Location

if (Test-Path $jarOut) {
    Write-Host "BUILD SUCCESS: $jarOut"
} else {
    Write-Host "JAR creation failed!"
    exit 1
}

# Deploy: copy to the game mods folder and remove older builds of this mod.
Copy-Item -Force $jarOut (Join-Path $modsFolder "cbcmsmwcompat-1.2.0.jar")
Get-ChildItem $modsFolder -Filter "cbcmsmwcompat-*.jar" | Where-Object { $_.Name -ne "cbcmsmwcompat-1.2.0.jar" } | Remove-Item -Force
Write-Host "Deployed to: $modsFolder"
