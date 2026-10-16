param(
    [ValidateSet('release', 'debug')]
    [string]$BuildType = 'release'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$gradleFile = Join-Path $projectRoot 'android/app/build.gradle.kts'
$version = [regex]::Match((Get-Content -LiteralPath $gradleFile -Raw), 'versionName\s*=\s*"([0-9A-Za-z._-]+)"').Groups[1].Value
if (-not $version) { throw '未找到应用版本号' }
$source = Join-Path $projectRoot "android/app/build/outputs/apk/$BuildType/app-$BuildType.apk"
if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "请先构建 $BuildType 安装包：$source" }
$destination = Join-Path $projectRoot "output/泉师课程表-$version-$BuildType.apk"
New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
Copy-Item -LiteralPath $source -Destination $destination
Get-Item -LiteralPath $destination | Select-Object Name, Length, FullName
Get-FileHash -LiteralPath $destination -Algorithm SHA256
