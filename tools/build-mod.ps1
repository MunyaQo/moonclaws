# Moonclaws: builds the mod on this computer and puts the .jar into the CurseForge "Moonclaws" instance.
# Started by "Собрать мод.bat". Writes the full build output to logs\build.log.

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$root      = Split-Path -Parent $PSScriptRoot
$toolsDir  = Join-Path $root '.tools'
$logsDir   = Join-Path $root 'logs'
$logFile   = Join-Path $logsDir 'build.log'
$gradleVer = '7.5.1'
$gradleDir = Join-Path $toolsDir "gradle-$gradleVer"
$gradleBat = Join-Path $gradleDir 'bin\gradle.bat'
$modsDir   = Join-Path $env:USERPROFILE 'curseforge\minecraft\Instances\Moonclaws\mods'

New-Item -ItemType Directory -Force -Path $toolsDir, $logsDir | Out-Null

function Say($text, $color = 'White') { Write-Host $text -ForegroundColor $color }
function Fail($text) {
    Say ''
    Say "ОШИБКА: $text" 'Red'
    "FAILED: $text" | Out-File -FilePath (Join-Path $logsDir 'result.txt') -Encoding utf8
    exit 1
}

Say '=== Moonclaws: сборка мода ===' 'Cyan'

# 1. Java 8 (JDK)
$jdk = $null
$candidates = @()
if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
foreach ($base in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Zulu", "${env:ProgramFiles(x86)}\Java")) {
    if (Test-Path $base) { $candidates += (Get-ChildItem $base -Directory | Where-Object { $_.Name -match '(jdk-?8|jdk1\.8|zulu-?8)' } | ForEach-Object FullName) }
}
foreach ($c in $candidates) {
    $javac = Join-Path $c 'bin\javac.exe'
    if (Test-Path $javac) {
        $v = (& cmd /c "`"$(Join-Path $c 'bin\java.exe')`" -version 2>&1") -join ' '
        if ($v -match '"1\.8\.') { $jdk = $c; break }
    }
}
if (-not $jdk) { Fail 'Не найдена Java 8 (JDK). Установите Eclipse Temurin 8 JDK и запустите снова.' }
$env:JAVA_HOME = $jdk
$env:Path = (Join-Path $jdk 'bin') + ';' + $env:Path
Say "Java 8 найдена: $jdk" 'Green'

# 2. Gradle (downloaded once)
if (-not (Test-Path $gradleBat)) {
    Say "Скачиваю Gradle $gradleVer (один раз, около 120 МБ)..." 'Yellow'
    $zip = Join-Path $toolsDir "gradle-$gradleVer-bin.zip"
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$gradleVer-bin.zip" -OutFile $zip -UseBasicParsing
        Expand-Archive -Path $zip -DestinationPath $toolsDir -Force
        Remove-Item $zip -Force
    } catch { Fail "Не удалось скачать Gradle: $($_.Exception.Message)" }
}
Say 'Gradle готов.' 'Green'

# 3. Build
Say ''
Say 'Собираю мод. Первый раз это долго (10-20 минут), дальше 1-2 минуты...' 'Yellow'
Push-Location $root
$ErrorActionPreference = 'Continue'
cmd /c "`"$gradleBat`" build --no-daemon 2>&1" | Tee-Object -FilePath $logFile
$code = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
Pop-Location
if ($code -ne 0) { Fail 'Сборка не удалась. Подробности в logs\build.log — напишите Claude, он прочитает сам.' }

# 4. Copy the jar into the Moonclaws instance
$jar = Get-ChildItem (Join-Path $root 'build\libs') -Filter 'moonclaws-*.jar' |
       Where-Object { $_.Name -notmatch '(sources|dev|slim)' } |
       Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { Fail 'Сборка прошла, но готовый .jar не найден в build\libs.' }
if (-not (Test-Path $modsDir)) { Fail "Не найдена папка сборки CurseForge: $modsDir" }
Get-ChildItem $modsDir -Filter 'moonclaws-*.jar' | Remove-Item -Force
Copy-Item $jar.FullName -Destination $modsDir -Force

"OK: $($jar.Name) -> $modsDir" | Out-File -FilePath (Join-Path $logsDir 'result.txt') -Encoding utf8
Say ''
Say "ГОТОВО! $($jar.Name) лежит в сборке Moonclaws. Можно запускать игру." 'Green'
exit 0
