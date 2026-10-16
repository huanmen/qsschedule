param([switch]$NoBrowser, [switch]$Local, [string]$Server = $env:QSS_DEPLOY_SSH)
$ErrorActionPreference = 'Stop'
$projectDirectory = $PSScriptRoot
$adminUrl = 'http://127.0.0.1:18474/'
$localDataDirectory = (Resolve-Path (Join-Path $projectDirectory 'server/qsschedule')).Path
$expectedDataDirectory = if ($Local) { $localDataDirectory } else { '/var/lib/qsschedule' }
function Read-AdminState {
    try { Invoke-RestMethod -Uri ($adminUrl + 'api/state') -TimeoutSec 2 } catch { $null }
}
$response = Read-AdminState
if ($null -ne $response -and $response.dataDirectory -ne $expectedDataDirectory) {
    # Only switch away from this project's own local preview, never another listener.
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort 18474 -ErrorAction SilentlyContinue)
    $owners = @($listeners | Select-Object -ExpandProperty OwningProcess -Unique)
    $canStop = -not $Local -and $response.dataDirectory -eq $localDataDirectory -and $owners.Count -eq 1
    if ($canStop) {
        $owner = Get-CimInstance Win32_Process -Filter ('ProcessId={0}' -f $owners[0])
        $scriptPath = Join-Path $projectDirectory 'server/qsschedule/plugin_admin.py'
        $canStop = $owner.Name -eq 'python.exe' -and $owner.CommandLine.Contains($scriptPath)
    }
    if (-not $canStop) { throw 'Port 18474 belongs to another admin. Close that listener before switching modes.' }
    Stop-Process -Id $owners[0] -ErrorAction Stop
    $response = $null
}
if ($null -eq $response) {
    if ($Local) {
        $executable = (Get-Command python -ErrorAction Stop).Source
        $arguments = '"{0}" --data "{1}"' -f (Join-Path $projectDirectory 'server/qsschedule/plugin_admin.py'), $localDataDirectory
    } else {
        if ([string]::IsNullOrWhiteSpace($Server)) { $Server = 'root@huanmen.com.cn' }
        if ($Server -notmatch '^[A-Za-z0-9_.@-]+$') { throw 'Use an SSH alias or user@host for -Server.' }
        $executable = (Get-Command ssh -ErrorAction Stop).Source
        $arguments = '-N -o BatchMode=yes -o ConnectTimeout=10 -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -L 127.0.0.1:18474:127.0.0.1:18474 ' + $Server
    }
    $process = Start-Process -FilePath $executable -ArgumentList $arguments -WorkingDirectory $projectDirectory -WindowStyle Hidden -PassThru
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        Start-Sleep -Milliseconds 250
        if ($process.HasExited) { throw 'Plugin admin connection failed. Check SSH access and port 18474.' }
        $response = Read-AdminState
        if ($null -ne $response) { break }
    }
    if ($null -eq $response -or $response.dataDirectory -ne $expectedDataDirectory) {
        if (-not $process.HasExited) { Stop-Process -Id $process.Id }
        throw 'Plugin admin did not become ready with the expected data directory.'
    }
}
if (-not $NoBrowser) { Start-Process $adminUrl }
Write-Output $adminUrl
Write-Output ('Data: {0}' -f $response.dataDirectory)
