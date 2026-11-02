# CTF 平台端到端验证脚本（PowerShell 7+）
# 用法：pwsh -File scripts/verify-flow.ps1 -BaseUrl http://localhost:8080
# 覆盖：健康检查 → 注册 → 登录 → 看题 → 错误 flag → 正确 flag → 排行榜 → 管理端建题 → 审计

param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$script:failures = 0

function Call-Api {
    param(
        [string]$Method = "GET",
        [string]$Path,
        $Body = $null,
        [string]$Token = $null,
        [string]$UserAgent = "ctf-verify-script/1.0"
    )
    $headers = @{ "User-Agent" = $UserAgent }
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $params = @{
        Uri             = "$BaseUrl$Path"
        Method          = $Method
        Headers         = $headers
        SkipHttpErrorCheck = $true
        NoProxy         = $true
    }
    if ($null -ne $Body) {
        $params["ContentType"] = "application/json; charset=utf-8"
        $params["Body"] = ($Body | ConvertTo-Json -Depth 6 -Compress)
    }
    $response = Invoke-WebRequest @params
    # Actuator 健康端点返回 v3+json，PowerShell 可能给出 byte[]，这里统一转字符串
    $content = $response.Content
    if ($content -is [byte[]]) {
        $content = [System.Text.Encoding]::UTF8.GetString($content)
    }
    $json = $null
    try { $json = $content | ConvertFrom-Json } catch { }
    return [pscustomobject]@{
        Status = [int]$response.StatusCode
        Json   = $json
        Raw    = $content
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if ($Condition) {
        Write-Host "[PASS] $Message" -ForegroundColor Green
    } else {
        Write-Host "[FAIL] $Message" -ForegroundColor Red
        $script:failures++
    }
}

function Show-Step {
    param([string]$Title, $Result, [int]$MaxLength = 400)
    $body = $Result.Raw
    if ($body.Length -gt $MaxLength) { $body = $body.Substring(0, $MaxLength) + "..." }
    Write-Host ""
    Write-Host "== $Title (HTTP $($Result.Status)) ==" -ForegroundColor Cyan
    Write-Host $body
}

$stamp = Get-Date -Format "HHmmss"
$username = "verify_$stamp"
$password = "VerifyPass123"

Write-Host "CTF 平台验证：$BaseUrl（用户 $username）" -ForegroundColor Yellow

# 1. 健康检查
$health = Call-Api -Path "/actuator/health"
Show-Step "1. 健康检查" $health
Assert-True ($health.Status -eq 200 -and $health.Json.status -eq "UP") "Actuator 健康检查为 UP"

# 2. 注册
$register = Call-Api -Method POST -Path "/api/auth/register" -Body @{ username = $username; password = $password }
Show-Step "2. 注册" $register
$userToken = $register.Json.data.token
Assert-True ($register.Status -eq 200 -and $register.Json.code -eq 0 -and $userToken) "注册成功并返回 JWT"

# 3. 登录
$login = Call-Api -Method POST -Path "/api/auth/login" -Body @{ username = $username; password = $password }
Show-Step "3. 登录" $login
Assert-True ($login.Status -eq 200 -and $login.Json.code -eq 0) "登录成功"

# 4. 赛事列表
$events = Call-Api -Path "/api/events" -Token $userToken
Show-Step "4. 赛事/练习区列表" $events 600
$practice = $events.Json.data | Where-Object { $_.type -eq "PRACTICE" -and $_.name -eq "常驻练习区" } | Select-Object -First 1
if (-not $practice) {
    $practice = $events.Json.data | Where-Object { $_.type -eq "PRACTICE" -and $_.name -notlike "验证脚本*" } | Select-Object -First 1
}
$competition = $events.Json.data | Where-Object { $_.type -eq "COMPETITION" -and $_.name -eq "秋季夺旗赛 2026" } | Select-Object -First 1
if (-not $competition) {
    $competition = $events.Json.data | Where-Object { $_.type -eq "COMPETITION" -and $_.status -eq "ONGOING" } | Select-Object -First 1
}
Assert-True ($null -ne $practice) "存在练习区"
Assert-True ($null -ne $competition) "存在进行中的比赛"

# 5. 练习区题目列表
$practiceChallenges = Call-Api -Path "/api/challenges?eventId=$($practice.id)" -Token $userToken
$pwn = $practiceChallenges.Json.data.challenges | Where-Object { $_.name -eq "栈溢出入门" } | Select-Object -First 1
Show-Step "5. 练习区题目列表" (Call-Api -Path "/api/challenges?eventId=$($practice.id)" -Token $userToken) 800
Assert-True ($null -ne $pwn) "找到练习题目：栈溢出入门"

# 6. 错误 flag
$wrong = Call-Api -Method POST -Path "/api/challenges/$($pwn.id)/submit" -Token $userToken -Body @{ flag = "flag{definitely_wrong}" }
Show-Step "6. 错误 flag 提交" $wrong
Assert-True ($wrong.Status -eq 400 -and $wrong.Json.code -eq 4004) "错误 flag 返回 4004 / HTTP 400"

# 错误提交会触发冷却（默认 5 秒），等待后再提交正确 flag
Start-Sleep -Seconds 6

# 7. 正确 flag
$correct = Call-Api -Method POST -Path "/api/challenges/$($pwn.id)/submit" -Token $userToken -Body @{ flag = "flag{r3t2w1n_4nd_r0p}" }
Show-Step "7. 正确 flag 提交" $correct
$practicePoints = $correct.Json.data.points
Assert-True ($correct.Status -eq 200 -and $correct.Json.data.correct -eq $true) "正确 flag 判分成功"
Assert-True ($practicePoints -gt 0) "获得积分：$practicePoints"

# 8. 重复提交
$dup = Call-Api -Method POST -Path "/api/challenges/$($pwn.id)/submit" -Token $userToken -Body @{ flag = "flag{r3t2w1n_4nd_r0p}" }
Show-Step "8. 重复提交已解出题目" $dup
Assert-True ($dup.Status -eq 409 -and $dup.Json.code -eq 4009) "重复提交返回 4009 / HTTP 409"

# 9. 练习区排行榜
$practiceBoard = Call-Api -Path "/api/scoreboard?eventId=$($practice.id)&type=USER&limit=20" -Token $userToken
Show-Step "9. 练习区个人榜（含我的名次）" $practiceBoard 800
$me = $practiceBoard.Json.data.me
Assert-True ([int]$me.totalPoints -eq [int]$practicePoints) "榜单总分与我本次得分一致"
Assert-True ([int]$me.rank -gt 0) "我的名次：$($me.rank)"

# 10. 赛事内解题
$competitionChallenges = Call-Api -Path "/api/challenges?eventId=$($competition.id)" -Token $userToken
$welcome = $competitionChallenges.Json.data.challenges | Where-Object { $_.name -eq "欢迎来到 CTF" } | Select-Object -First 1
Assert-True ($null -ne $welcome) "赛事题目可见：欢迎来到 CTF"
$competitionSolve = Call-Api -Method POST -Path "/api/challenges/$($welcome.id)/submit" -Token $userToken -Body @{ flag = "flag{welcome_to_ctf_2026}" }
Show-Step "10. 赛事内正确提交" $competitionSolve
Assert-True ($competitionSolve.Status -eq 200 -and $competitionSolve.Json.data.correct -eq $true) "赛事题目判分成功"

# 11. 赛事榜与练习榜隔离
$competitionBoard = Call-Api -Path "/api/scoreboard?eventId=$($competition.id)&type=USER&limit=20" -Token $userToken
Show-Step "11. 赛事个人榜" $competitionBoard 800
$competitionPoints = $competitionSolve.Json.data.points
Assert-True ([int]$competitionBoard.Json.data.me.totalPoints -eq [int]$competitionPoints) "赛事榜分数与赛事得分一致（练习区不串分）"

# 12. 战队榜
$teamBoard = Call-Api -Path "/api/scoreboard?eventId=$($practice.id)&type=TEAM&limit=20" -Token $userToken
Show-Step "12. 练习区战队榜" $teamBoard 600
Assert-True ($teamBoard.Status -eq 200 -and $teamBoard.Json.code -eq 0) "战队榜接口可用"

# 13. 管理端登录并建练习区 + 题目
$adminLogin = Call-Api -Method POST -Path "/api/auth/login" -Body @{ username = "admin"; password = "Admin@123" }
Assert-True ($adminLogin.Status -eq 200 -and $adminLogin.Json.code -eq 0) "管理员登录成功"
$adminToken = $adminLogin.Json.data.token

$newEvent = Call-Api -Method POST -Path "/api/admin/events" -Token $adminToken -Body @{
    name        = "验证脚本练习区 $stamp"
    description = "由 scripts/verify-flow.ps1 创建"
    type        = "PRACTICE"
}
Show-Step "13. 管理端创建练习区" $newEvent
$newEventId = $newEvent.Json.data.id
Assert-True ($newEvent.Status -eq 200 -and $newEvent.Json.code -eq 0) "创建练习区成功"

$newChallenge = Call-Api -Method POST -Path "/api/admin/challenges" -Token $adminToken -Body @{
    eventId       = $newEventId
    name          = "验证脚本题目 $stamp"
    description   = "验证管理端建题与判分"
    category      = "MISC"
    difficulty    = "EASY"
    initialPoints = 100
    flag          = "flag{verify_script_ok}"
}
Show-Step "14. 管理端创建题目" $newChallenge
$newChallengeId = $newChallenge.Json.data.id
Assert-True ($newChallenge.Status -eq 200 -and $newChallenge.Json.code -eq 0) "创建题目成功"

# 14. 选手看到新题并解出
$newList = Call-Api -Path "/api/challenges?eventId=$newEventId" -Token $userToken
Show-Step "15. 选手查看新练习区题目" $newList
$newSolve = Call-Api -Method POST -Path "/api/challenges/$($newChallengeId)/submit" -Token $userToken -Body @{ flag = "flag{verify_script_ok}" }
Show-Step "16. 选手解出新题" $newSolve
Assert-True ($newSolve.Status -eq 200 -and $newSolve.Json.data.correct -eq $true) "选手解出管理端新建题目"

# 15. 审计与防作弊
$audit = Call-Api -Path "/api/admin/submissions?challengeId=$($newChallengeId)&size=20" -Token $adminToken
Show-Step "17. 管理端提交审计（IP/UA）" $audit 800
$auditItem = $audit.Json.data.items | Select-Object -First 1
Assert-True ($audit.Json.data.total -ge 1 -and $auditItem.ip -and $auditItem.userAgent) "审计记录包含 IP 与 User-Agent"

$anomalies = Call-Api -Path "/api/admin/anomalies" -Token $adminToken
Show-Step "18. 异常告警列表" $anomalies 600
Assert-True ($anomalies.Status -eq 200 -and $anomalies.Json.code -eq 0) "异常告警接口可用"

# 16. 我的战绩
$profile = Call-Api -Path "/api/me/profile" -Token $userToken
Show-Step "19. 我的战绩" $profile 800
Assert-True ($profile.Json.data.solves.Count -ge 3) "我的战绩包含 3 条以上解题记录"

# 17. 权限
$forbidden = Call-Api -Path "/api/admin/users" -Token $userToken
Show-Step "20. 选手访问管理接口" $forbidden
Assert-True ($forbidden.Status -eq 403 -and $forbidden.Json.code -eq 4030) "选手访问管理接口返回 4030 / HTTP 403"

Write-Host ""
if ($script:failures -eq 0) {
    Write-Host "全部验证通过（0 失败）" -ForegroundColor Green
    exit 0
} else {
    Write-Host "验证失败：$($script:failures) 项" -ForegroundColor Red
    exit 1
}
