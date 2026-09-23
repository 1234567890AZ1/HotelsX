# HotelsX 一键打包（Windows）
# 本文件必须保存为「UTF-8 带 BOM」，否则 Windows PowerShell 5.1 会按系统 ANSI 码页读取，中文会乱码。
# 所有中文提示都放在这里，build.bat 保持纯 ASCII，避免 cmd.exe 的码页解析问题。

Set-Location -LiteralPath $PSScriptRoot

Write-Host ""
Write-Host "=========================================================="
Write-Host "  HotelsX 一键打包"
Write-Host "=========================================================="
Write-Host ""

if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'mvnw.cmd'))) {
    Write-Host "[错误] 找不到 mvnw.cmd，请确认本文件位于项目根目录。" -ForegroundColor Red
    Write-Host ""
    exit 1
}

# ---------- 1. 定位 java.exe：优先 JAVA_HOME，其次 PATH ----------
$javaExe = $null
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path -LiteralPath $candidate) { $javaExe = $candidate }
}
if (-not $javaExe) {
    $found = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($found) { $javaExe = $found.Source }
}

if (-not $javaExe) {
    Write-Host "[错误] 没有找到 Java。" -ForegroundColor Red
    Write-Host ""
    Write-Host "  本项目需要 JDK 21 或更高版本，请先安装："
    Write-Host "    https://adoptium.net/temurin/releases/?version=21"
    Write-Host ""
    Write-Host "  安装后二选一："
    Write-Host "    1) 把 JAVA_HOME 环境变量指向 JDK 安装目录"
    Write-Host "    2) 把 JDK 的 bin 目录加入 PATH"
    Write-Host ""
    exit 1
}

# ---------- 2. 校验 JDK 主版本号 >= 21 ----------
$versionLine = [string](& $javaExe -version 2>&1 | Select-Object -First 1)
$javaVer = $null
if ($versionLine -match '"([^"]+)"') { $javaVer = $Matches[1] }

$javaMajor = 0
if ($javaVer) {
    $first = ($javaVer -split '\.')[0]
    if ($first -eq '1') { $javaMajor = 8 }
    elseif ($first -match '^\d+$') { $javaMajor = [int]$first }
}

if ($javaMajor -lt 21) {
    Write-Host "[错误] 检测到 Java $javaVer，本项目需要 JDK 21 或更高版本。" -ForegroundColor Red
    Write-Host "        当前使用的 java: $javaExe"
    Write-Host ""
    Write-Host "        如果你已经装了新版 JDK 仍然报这个错，说明 PATH 上的 java 是旧版本，"
    Write-Host "        请把 JAVA_HOME 指向新的 JDK 目录后重试。"
    Write-Host ""
    exit 1
}

Write-Host "[1/2] 环境检查通过：Java $javaVer"
Write-Host "[2/2] 开始构建（首次运行会自动下载 Maven 和依赖，请耐心等待）"
Write-Host ""

& (Join-Path $PSScriptRoot 'mvnw.cmd') -B clean package -DskipTests
$mvnExit = $LASTEXITCODE

if ($mvnExit -ne 0) {
    Write-Host ""
    Write-Host "=========================================================="
    Write-Host "  构建失败，请把上面的报错信息发给开发者。" -ForegroundColor Red
    Write-Host "=========================================================="
    Write-Host ""
    exit $mvnExit
}

Write-Host ""
Write-Host "=========================================================="
Write-Host "  构建成功"
$targetDir = Join-Path $PSScriptRoot 'target'
Get-ChildItem -LiteralPath $targetDir -Filter 'HotelsX-*.jar' -ErrorAction SilentlyContinue |
    ForEach-Object { Write-Host "  插件位置: $($_.FullName)" }
Write-Host ""
Write-Host "  把它放进服务器的 plugins 目录，重启服务器即可。"
Write-Host "=========================================================="
Write-Host ""
exit 0
