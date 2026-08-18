param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$Count = 100,
    [int]$Balance = 100000,
    [string]$OutputFile = "performance/tokens.csv"
)

# 为压测批量注册账号、提高测试余额，并导出登录 Token。
$ErrorActionPreference = "Stop"
$runId = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$prefix = "load_${runId}_"
$tokens = [System.Collections.Generic.List[string]]::new()

for ($index = 1; $index -le $Count; $index++) {
    $username = "${prefix}${index}"
    $body = @{ username = $username; password = "LoadTest123" } | ConvertTo-Json -Compress
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/user/register" -ContentType "application/json" -Body $body | Out-Null
    $login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/user/login" -ContentType "application/json" -Body $body
    $tokens.Add($login.data.token)
}

$sql = "USE game_flow; UPDATE sys_user SET balance=$Balance WHERE username LIKE '$prefix%';"
docker compose exec -T mysql mysql -uroot -proot -e $sql
if ($LASTEXITCODE -ne 0) {
    throw "Failed to increase load-test balances"
}

$outputPath = Join-Path (Get-Location) $OutputFile
$outputDirectory = Split-Path -Parent $outputPath
if (-not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}
$tokens | Set-Content -LiteralPath $outputPath -Encoding utf8

Write-Host "Prepared $Count users with balance $Balance"
Write-Host "Token file: $outputPath"
Write-Host "User prefix for cleanup: $prefix"
