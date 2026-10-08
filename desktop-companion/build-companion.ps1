# 스펙 오디세이 데스크톱 캐릭터 — 빌드 · 배포 파일 만들기
#
#   powershell -ExecutionPolicy Bypass -File desktop-companion\build-companion.ps1
#
# 결과 (desktop-companion\dist\):
#   SpecOdysseyCompanion\SpecOdysseyCompanion.exe   바로 실행해 볼 수 있는 폴더 (Java 포함 — 사용자 PC에 Java 불필요)
#   SpecOdysseyCompanion.zip                        압축 배포가 필요할 때만 (보통은 Setup.exe를 쓴다)
#   SpecOdysseyCompanion.zip.sha256                 확인값 — 같이 올린다 (캐릭터 업데이트가 이 값으로 파일을 검사)
#   SpecOdysseyCompanion-Setup.exe (+ .sha256)      이용자에게 줄 설치 파일 하나 — 더블클릭하면 내 계정에 설치(관리자 권한 불필요),
#                                                   specodyssey:// 등록, 시작 메뉴, 설치 끝나면 캐릭터 실행. WiX 3.14가 있을 때만 만든다
#
# 새 버전 내기: pom.xml의 <version>을 올리고 → 이 스크립트 → 사이트 관리자 화면 '데스크톱 캐릭터'에서 Setup.exe와 같은 버전 번호로 올리기.
#   올린 파일은 DB에 보관되고, 사이트 [내려받기]와 설치된 캐릭터의 [업데이트]가 그 파일을 쓴다.
# Java는 필요한 부분(화면·HTTPS 통신)만 골라 넣어 크기를 줄인다.
# 필요한 것: JDK 17 이상 (jpackage 포함). Setup.exe까지 만들려면 WiX 3.14 — %LOCALAPPDATA%\wix314에 압축만 풀어 두면 된다
#   (https://github.com/wixtoolset/wix3/releases 의 wix314-binaries.zip). 없으면 zip만 만든다.

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $here
$dist = Join-Path $here 'dist'
$name = 'SpecOdysseyCompanion'

[xml]$pom = Get-Content (Join-Path $here 'pom.xml') -Encoding UTF8
$version = $pom.project.version
Write-Host "== 빌드 $name $version"

# jpackage가 만든 exe는 읽기 전용이라 maven clean이 못 지운다 — 먼저 강제로 지운다
foreach ($old in @((Join-Path $here 'target'), $dist)) {
    if (Test-Path $old) { Remove-Item $old -Recurse -Force }
}
& (Join-Path $root 'mvnw.cmd') -q -f (Join-Path $here 'pom.xml') clean package
if ($LASTEXITCODE -ne 0) { throw 'maven 빌드 실패' }

$input = Join-Path $here 'target\jpackage-input'
if (Test-Path $input) { Remove-Item $input -Recurse -Force }
New-Item -ItemType Directory $input | Out-Null
Copy-Item (Join-Path $here 'target\companion.jar') $input

if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
New-Item -ItemType Directory $dist | Out-Null

# jpackage는 버전 형식이 숫자.숫자.숫자만 된다
$appVersion = ($version -replace '[^0-9.].*$', '')
jpackage --type app-image `
    --name $name `
    --app-version $appVersion `
    --vendor 'Spec Odyssey' `
    --description 'Spec Odyssey desktop companion' `
    --input $input `
    --main-jar companion.jar `
    --main-class com.specodyssey.companion.CompanionApp `
    --java-options '-Dfile.encoding=UTF-8' `
    --java-options '-Dsun.java2d.d3d=false' `
    --add-modules 'java.desktop,java.net.http,java.logging,jdk.crypto.ec,jdk.localedata' `
    --jlink-options '--strip-debug --no-man-pages --no-header-files --compress=zip-6' `
    --dest $dist
if ($LASTEXITCODE -ne 0) { throw 'jpackage 실패' }

$zip = Join-Path $dist "$name.zip"
# 방금 만든 파일을 백신이 검사하느라 잠깐 잡고 있을 수 있어 몇 번 다시 시도한다
Add-Type -AssemblyName System.IO.Compression.FileSystem
for ($i = 1; $i -le 5; $i++) {
    try {
        if (Test-Path $zip) { Remove-Item $zip -Force }
        [System.IO.Compression.ZipFile]::CreateFromDirectory((Join-Path $dist $name), $zip, [System.IO.Compression.CompressionLevel]::Optimal, $true)
        break
    } catch {
        if ($i -eq 5) { throw }
        Start-Sleep -Seconds 2
    }
}
$hash = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLower()
Set-Content -Path "$zip.sha256" -Value "$hash  $name.zip" -Encoding ascii -NoNewline

$size = [math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host "== 완료: $zip ($size MB)"
Write-Host "   sha256 $hash"
Write-Host "   새 버전이면 관리자 화면에서 Setup.exe를 버전 $version (으)로 올리세요"

# ---- 설치 파일 하나 (Setup.exe) — WiX가 있을 때만
$wix = Join-Path $env:LOCALAPPDATA 'wix314'
if (Test-Path (Join-Path $wix 'candle.exe')) {
    $env:PATH = "$wix;$env:PATH"
    $setupDir = Join-Path $here 'target\setup'
    if (Test-Path $setupDir) { Remove-Item $setupDir -Recurse -Force }
    # installer\main.wxs = jpackage 기본 틀 + specodyssey:// 등록 + 설치 뒤 바로 실행
    jpackage --type exe `
        --app-image (Join-Path $dist $name) `
        --name $name `
        --app-version $appVersion `
        --vendor 'Spec Odyssey' `
        --description 'Spec Odyssey desktop companion' `
        --resource-dir (Join-Path $here 'installer') `
        --win-per-user-install `
        --win-menu --win-menu-group 'Spec Odyssey' `
        --win-upgrade-uuid '6f3b2a1e-7c4d-4e8a-9b51-2d0c9e7a4f10' `
        --dest $setupDir
    if ($LASTEXITCODE -ne 0) { throw 'Setup.exe 만들기 실패' }
    $setup = Join-Path $dist "$name-Setup.exe"
    Move-Item (Get-ChildItem $setupDir -Filter '*.exe' | Select-Object -First 1).FullName $setup
    $sh = (Get-FileHash $setup -Algorithm SHA256).Hash.ToLower()
    Set-Content -Path "$setup.sha256" -Value "$sh  $name-Setup.exe" -Encoding ascii -NoNewline
    Write-Host "== 설치 파일: $setup ($([math]::Round((Get-Item $setup).Length / 1MB, 1)) MB)"
} else {
    Write-Host "== WiX가 없어 Setup.exe는 건너뜀 ($wix)"
}
