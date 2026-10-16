param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$Package,
    [int]$Required = 15,
    [int]$Attempts = 50,
    [int]$WaitSeconds = 18,
    [string]$ResumeFrom,
    [switch]$RequireSplash,
    [switch]$ReviewEach
)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$repo = Resolve-Path (Join-Path $PSScriptRoot '../..')
$reportDir = Join-Path $repo ('output/adskip-device/' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
$activity = (& $adb -s $Serial shell cmd package resolve-activity --brief $Package | Select-Object -Last 1).Trim()
if ($activity -notmatch '/') { throw "Cannot resolve launcher: $Package" }
$results = @()
$streak = 0
for ($attempt = 1; $attempt -le $Attempts -and $streak -lt $Required; $attempt++) {
    if ($ResumeFrom) {
        $otherActivity = (& $adb -s $Serial shell cmd package resolve-activity --brief $ResumeFrom | Select-Object -Last 1).Trim()
        if ($otherActivity -notmatch '/') { throw "Cannot resolve resume source: $ResumeFrom" }
        & $adb -s $Serial shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n $otherActivity | Out-Null
        Start-Sleep -Seconds 3
        & $adb -s $Serial shell screencap -p /sdcard/adskip-source.png
        $oldErrorAction = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & $adb -s $Serial pull /sdcard/adskip-source.png (Join-Path $reportDir "$Package-$attempt-source.png") 2>&1 | Out-Null
        $ErrorActionPreference = $oldErrorAction
        if ($ReviewEach) {
            Write-Output "REVIEW source=$(Join-Path $reportDir "$Package-$attempt-source.png")"
            Read-Host 'Inspect source screenshot, then press Enter' | Out-Null
        }
    } else {
        foreach ($other in (@('com.zzw.october', 'com.mygolbs.mybus', 'com.zjwh.android_wh_physicalfitness', $Package) | Select-Object -Unique)) {
            & $adb -s $Serial shell am force-stop $other | Out-Null
        }
    }
    & $adb -s $Serial logcat -c
    & $adb -s $Serial shell rm -f /sdcard/adskip-feedback.png | Out-Null
    & $adb -s $Serial shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n $activity | Out-Null
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $captured = $false
    do {
        Start-Sleep -Milliseconds 500
        $log = @(& $adb -s $Serial logcat -d -s AdSkipTrace:I)
        $own = @($log | Where-Object { $_ -match ('pkg=' + [regex]::Escape($Package) + '\s') })
        if (!$captured -and ($own -match 'feedback shown')) {
            & $adb -s $Serial shell screencap -p /sdcard/adskip-feedback.png
            # adb writes its successful pull summary to stderr on Windows;
            # merge it before discarding so a valid capture cannot abort the
            # strict run under $ErrorActionPreference=Stop.
            $oldErrorAction = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            & $adb -s $Serial pull /sdcard/adskip-feedback.png (Join-Path $reportDir "$Package-$attempt-feedback.png") 2>&1 | Out-Null
            $ErrorActionPreference = $oldErrorAction
            $captured = $true
        }
    } while ($timer.Elapsed.TotalSeconds -lt $WaitSeconds)
    $finalImage = Join-Path $reportDir "$Package-$attempt-final.png"
    & $adb -s $Serial shell screencap -p /sdcard/adskip-final.png
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & $adb -s $Serial pull /sdcard/adskip-final.png $finalImage 2>&1 | Out-Null
    $ErrorActionPreference = $oldErrorAction
    # Include actions triggered while capturing the final state.
    $log = @(& $adb -s $Serial logcat -d -s AdSkipTrace:I)
    $own = @($log | Where-Object { $_ -match ('pkg=' + [regex]::Escape($Package) + '\s') })
    $terminal = @($own | Where-Object { $_ -match 'verify .* result=' })
    $matched = @($own | Where-Object { $_ -match 'matched ' })
    $splashMatched = @($matched | Where-Object { $_ -match 'rule=(builtin-global-splash|yiban-splash|sportworld-splash-skip|.*-splash-skip)\s' })
    $interaction = @($own | Where-Object { $_ -match 'action .*?(accessibility_click_accepted|gesture_completed)' })
    # A launch is valid only when every attempted action in this launch was
    # confirmed. A later popup success cannot hide an earlier unconfirmed or
    # still-present target from the same run.
    $badTerminal = @($terminal | Where-Object { $_ -match 'result=(UNCONFIRMED|TARGET_PRESENT|CLICK_REJECTED|GESTURE_CANCELLED)' })
    $goodTerminal = @($terminal | Where-Object { $_ -match 'result=(TARGET_GONE|NORMAL_PAGE)' })
    $success = ($terminal.Count -gt 0) -and ($badTerminal.Count -eq 0) -and ($goodTerminal.Count -eq $terminal.Count) -and ($matched.Count -eq $terminal.Count) -and ($interaction.Count -ge $matched.Count) -and ($captured) -and (!$RequireSplash -or $splashMatched.Count -gt 0)
    if ($ReviewEach) {
        Write-Output "REVIEW image=$finalImage evidenceComplete=$success"
        $review = Read-Host 'Page clean with no wrong navigation? y/n'
        $success = $success -and $review -eq 'y'
    }
    if ($success) { $streak++ } else { $streak = 0 }
    $focused = (& $adb -s $Serial shell dumpsys activity activities | Select-String 'mFocusedApp=' | Select-Object -First 1).Line
    $results += [pscustomobject]@{ attempt = $attempt; mode = $(if ($ResumeFrom) { 'resume' } else { 'cold' }); success = [bool]$success; streak = $streak; image = $finalImage; focused = $focused; evidence = $terminal; interaction = $interaction; log = $own }
    $results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $reportDir "$Package.json") -Encoding utf8
    Write-Output "$Package attempt=$attempt streak=$streak/$Required success=$success"
    $terminal | ForEach-Object { Write-Output $_ }
}
if ($streak -lt $Required) { exit 2 }
