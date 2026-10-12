<#
  스펙 오디세이 — 클라우드플레어 터널 띄우기 (윈도우)

  쓰는 법:   .\deploy\start-tunnel.ps1 -Quick     # 임시 주소 (시연 당일 — 설정 파일 필요 없음)
             .\deploy\start-tunnel.ps1            # 고정 주소 (config.yml 필요)
             .\deploy\start-tunnel.ps1 -Quick -Port 8081

  하는 일: 띄우기 전에 흔히 걸리는 것들을 먼저 확인하고, 하나라도 어긋나면
  터널을 올리지 않고 무엇이 문제인지 알려 준다. 터널은 떴는데 사이트가 안 열리는
  상황을 만들지 않는 게 목적이다.

  자세한 배경: docs/deploy-cloudflare-tunnel.md
#>
param(
    [int]$Port = 8080,
    [string]$TunnelName = 'spec-odyssey',
    [switch]$Quick,
    [switch]$SkipChecks
)

$ErrorActionPreference = 'Stop'
function Ok   ($m) { Write-Host "  [OK]  $m" -ForegroundColor Green }
function Warn ($m) { Write-Host "  [확인] $m" -ForegroundColor Yellow }
function Bad  ($m) { Write-Host "  [막힘] $m" -ForegroundColor Red }

Write-Host ""
Write-Host "스펙 오디세이 — 클라우드플레어 터널" -ForegroundColor Cyan
Write-Host "---------------------------------------------"

$problems = @()

if (-not $SkipChecks) {

    # 1) cloudflared 설치
    $cf = Get-Command cloudflared -ErrorAction SilentlyContinue
    if ($null -eq $cf) {
        Bad "cloudflared 가 없습니다.  winget install --id Cloudflare.cloudflared"
        $problems += 'cloudflared'
    } else {
        Ok "cloudflared $((cloudflared --version) -split ' ' | Select-Object -Index 2)"
    }

    # 2) 설정 파일 — 고정 터널에서만 쓴다 (임시 터널은 설정 파일이 없다)
    if ($Quick) { Ok "임시 터널 — 설정 파일 확인을 건너뜁니다" } else {
    $cfgPath = Join-Path $env:USERPROFILE '.cloudflared\config.yml'
    if (-not (Test-Path $cfgPath)) {
        Bad "설정 파일이 없습니다: $cfgPath"
        Write-Host "        deploy\cloudflared\config.example.yml 을 복사해서 값을 채우세요." -ForegroundColor DarkGray
        $problems += 'config'
    } else {
        Ok "설정 파일 $cfgPath"
        $cfg = Get-Content $cfgPath -Raw
        if ($cfg -match 'httpHostHeader') {
            Bad "config.yml 에 httpHostHeader 가 있습니다 — 모든 POST 가 403 이 됩니다."
            Write-Host "        Host 헤더가 바뀌면 CSRF 동일 출처 검사가 떨어집니다. 그 줄을 지우세요." -ForegroundColor DarkGray
            $problems += 'hostheader'
        }
        if ($cfg -match '바꿔주세요') {
            Bad "config.yml 에 '바꿔주세요' 가 남아 있습니다 — 값을 아직 안 채웠습니다."
            $problems += 'placeholder'
        }
        if ($cfg -notmatch "http://localhost:$Port(\D|$)") {
            Warn "config.yml 의 service 주소가 http://localhost:$Port 이 아닙니다. 포트를 확인하세요."
        }
    }
    }

    # 3) 톰캣이 떠 있는지
    $alive = $false
    try {
        $r = Invoke-WebRequest "http://localhost:$Port/" -UseBasicParsing -TimeoutSec 5 -MaximumRedirection 0 -ErrorAction Stop
        $alive = $true
    } catch {
        # 302(로그인으로 보냄)도 살아 있는 것이다
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode.value__ -ge 200 `
            -and $_.Exception.Response.StatusCode.value__ -lt 500) { $alive = $true }
    }
    if ($alive) { Ok "톰캣이 http://localhost:$Port 에서 응답합니다" }
    else {
        Bad "http://localhost:$Port 이 응답하지 않습니다 — 톰캣을 먼저 띄우세요."
        $problems += 'tomcat'
    }

    # 4) WAR 안에 RemoteIpValve 가 들어갔는지
    $ctx = Join-Path $PSScriptRoot '..\src\main\webapp\META-INF\context.xml'
    if ((Test-Path $ctx) -and ((Get-Content $ctx -Raw) -match 'RemoteIpValve')) {
        Ok "RemoteIpValve 설정 있음 (공유 링크가 https 주소로 만들어집니다)"
    } else {
        Bad "META-INF/context.xml 에 RemoteIpValve 가 없습니다."
        Write-Host "        없으면 공유 링크가 http://도메인:$Port 으로 만들어져 밖에서 안 열립니다." -ForegroundColor DarkGray
        $problems += 'valve'
    }

    Write-Host "---------------------------------------------"
    if ($problems.Count -gt 0) {
        Write-Host ""
        Bad "$($problems.Count) 가지가 막고 있습니다. 위를 먼저 해결하세요."
        Write-Host "   (확인을 건너뛰려면 -SkipChecks)" -ForegroundColor DarkGray
        Write-Host ""
        exit 1
    }
    Write-Host ""
}

if ($Quick) {
    Write-Host "임시 터널을 올립니다. 출력되는 https://___.trycloudflare.com 주소를 쓰세요." -ForegroundColor Cyan
    Write-Host "이 주소는 끄면 사라집니다. 멈추려면 Ctrl+C." -ForegroundColor DarkGray
    Write-Host ""
    cloudflared tunnel --url "http://localhost:$Port"
} else {
    Write-Host "터널 '$TunnelName' 을 올립니다. 멈추려면 Ctrl+C." -ForegroundColor Cyan
    Write-Host ""
    cloudflared tunnel run $TunnelName
}
