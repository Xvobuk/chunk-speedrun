param([string]$RuntimeDirectory=(Join-Path $PSScriptRoot '..\..\work\receiver-runtime'))
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Force $RuntimeDirectory | Out-Null
$RuntimeDirectory=(Resolve-Path -LiteralPath $RuntimeDirectory).Path
try {$health=Invoke-RestMethod 'http://127.0.0.1:24873/health' -TimeoutSec 2; if($health.repository -eq 'Xvobuk/chunk-speedrun'){Write-Output 'Receiver is already running.';return}} catch {}
if(!(Test-Path -LiteralPath "$env:SystemRoot\System32\OpenSSH\ssh.exe")){throw 'Windows OpenSSH Client is required.'}
$python=(Get-Command python.exe -ErrorAction SilentlyContinue | Select-Object -First 1).Source
if(!$python) {$python=Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'}
if(!(Test-Path -LiteralPath $python)){throw 'Python 3.10+ is required on the receiver computer.'}
$script=Join-Path $PSScriptRoot 'receiver\launch_receiver.py'
$arguments=@(('"'+$script+'"'),'--repo','Xvobuk/chunk-speedrun','--runtime',('"'+$RuntimeDirectory+'"')) -join ' '
Start-Process -FilePath $python -ArgumentList $arguments -WindowStyle Hidden -RedirectStandardOutput (Join-Path $RuntimeDirectory 'receiver.log') -RedirectStandardError (Join-Path $RuntimeDirectory 'receiver-error.log')
Write-Output 'Receiver started in background. Logs:'
Write-Output (Join-Path $RuntimeDirectory 'receiver.log')
