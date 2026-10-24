# Compila o simulador, os agentes de exemplo e o código do trabalho para a pasta build/.
# Uso (na raiz do projeto):  powershell -ExecutionPolicy Bypass -File compilar.ps1
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# JDK: usa o JDK portátil em tools/ se existir; senão, o javac do PATH.
$javac = "javac"
$jdk = Get-ChildItem -Path "tools" -Directory -Filter "jdk-*" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdk) { $javac = Join-Path $jdk.FullName "bin\javac.exe" }

if (Test-Path build) { Remove-Item -Recurse -Force build }
New-Item -ItemType Directory -Force build | Out-Null

# Lista de fontes com caminhos relativos (a raiz do projeto tem espaço no nome).
$fontes = Get-ChildItem -Recurse -Filter *.java -Path "simulador\PacMan-vs-Ghosts\src", "simulador\PacMan-vs-Ghosts-Agents\src", "src" |
    ForEach-Object { '"' + (Resolve-Path -Relative $_.FullName).Replace('\', '/') + '"' }
[System.IO.File]::WriteAllLines((Join-Path $PSScriptRoot "build\fontes.txt"), $fontes)

& $javac -nowarn -encoding UTF-8 -d build "@build/fontes.txt"
if ($LASTEXITCODE -ne 0) { throw "falha na compilacao" }

# Recursos do simulador (labirintos e distâncias pré-calculadas).
New-Item -ItemType Directory -Force "build\game\core" | Out-Null
Copy-Item -Recurse -Force "simulador\PacMan-vs-Ghosts\src\game\core\resources" "build\game\core\"
Write-Output "compilado em build/"
