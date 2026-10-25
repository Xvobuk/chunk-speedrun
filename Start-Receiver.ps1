param([string]$RuntimeDirectory=(Join-Path $PSScriptRoot '..\..\work\receiver-runtime'))
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Force $RuntimeDirectory | Out-Null
$RuntimeDirectory=(Resolve-Path -LiteralPath $RuntimeDirectory).Path
$cloudflared=Join-Path $RuntimeDirectory 'cloudflared.exe'
if(!(Test-Path -LiteralPath $cloudflared)) {
    $release=Invoke-RestMethod 'https://api.github.com/repos/cloudflare/cloudflared/releases/latest'
    $asset=$release.assets | Where-Object name -eq 'cloudflared-windows-amd64.exe' | Select-Object -First 1
    if(!$asset -or !$asset.digest.StartsWith('sha256:')) {throw 'Cannot verify cloudflared download.'}
    Invoke-WebRequest $asset.browser_download_url -OutFile $cloudflared
    if((Get-FileHash -LiteralPath $cloudflared -Algorithm SHA256).Hash.ToLowerInvariant() -ne $asset.digest.Substring(7)) {throw 'cloudflared checksum mismatch.'}
}
$python=(Get-Command python.exe -ErrorAction SilentlyContinue | Select-Object -First 1).Source
if(!$python) {$python=Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'}
if(!(Test-Path -LiteralPath $python)){throw 'Python 3.10+ is required on the receiver computer.'}
$script=Join-Path $PSScriptRoot 'receiver\launch_receiver.py'
$arguments=@(('"'+$script+'"'),'--repo','Xvobuk/chunk-speedrun','--runtime',('"'+$RuntimeDirectory+'"'),'--cloudflared',('"'+$cloudflared+'"')) -join ' '
Start-Process -FilePath $python -ArgumentList $arguments -WindowStyle Hidden -RedirectStandardOutput (Join-Path $RuntimeDirectory 'receiver.log') -RedirectStandardError (Join-Path $RuntimeDirectory 'receiver-error.log')
Write-Output 'Receiver started in background. Logs:'
Write-Output (Join-Path $RuntimeDirectory 'receiver.log')
