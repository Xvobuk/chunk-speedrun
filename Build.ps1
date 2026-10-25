param(
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [string]$MinecraftDirectory = "$env:APPDATA\.minecraft",
    [string]$BuildDirectory = (Join-Path $PSScriptRoot '..\..\work\mod-build')
)
$ErrorActionPreference = 'Stop'
$projectDirectory = $PSScriptRoot
$buildDirectory = $BuildDirectory
$classesDirectory = Join-Path $buildDirectory 'classes'
New-Item -ItemType Directory -Force $classesDirectory | Out-Null
$minecraftJar = Join-Path $MinecraftDirectory 'versions\26.3\26.3.jar'
if (!(Test-Path -LiteralPath $minecraftJar)) { throw 'Install Minecraft Java 26.3 first.' }
if ((Get-FileHash -LiteralPath $minecraftJar -Algorithm SHA1).Hash -ne 'e877b6a07acd633fb3bb475002175cec036e7b87') { throw 'Unexpected Minecraft 26.3 JAR. Use the official game artifact.' }
$libraryJars = Get-ChildItem -LiteralPath (Join-Path $MinecraftDirectory 'libraries') -Recurse -Filter '*.jar'
if (!($libraryJars | Where-Object Name -eq 'fabric-loader-0.19.5.jar')) { throw 'Install Fabric Loader 0.19.5 first.' }
$classpath = (@($minecraftJar) + @($libraryJars.FullName)) -join ';'
$sources = Get-ChildItem -LiteralPath (Join-Path $projectDirectory 'src\main\java') -Recurse -Filter '*.java'
$arguments = '-proc:none -encoding UTF-8 -classpath "' + $classpath.Replace('\','/') + '" -d "' + $classesDirectory.Replace('\','/') + '" ' + (($sources | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' }) -join "`n")
$argumentFile = Join-Path $buildDirectory 'javac.args'
[IO.File]::WriteAllText($argumentFile, $arguments)
& (Join-Path $JavaHome 'bin\javac.exe') "@$argumentFile"
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
Copy-Item (Join-Path $projectDirectory 'src\main\resources\*') $classesDirectory -Force
$jarPath = Join-Path $projectDirectory '..\chunkspeedrun-1.0.0+26.3.jar'
& (Join-Path $JavaHome 'bin\jar.exe') --create --file $jarPath -C $classesDirectory .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
Get-Item -LiteralPath $jarPath
