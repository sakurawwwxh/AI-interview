param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [Parameter(Mandatory = $true)][string]$Username,
    [Parameter(Mandatory = $true)][string]$Password,
    [string]$FixturePattern = "perf-resume-*.txt",
    [ValidateRange(1, 20)][int]$FixtureCount = 4,
    [int]$PollSeconds = 300
)

$ErrorActionPreference = "Stop"
$base = $BaseUrl.TrimEnd('/')
$fixtures = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'fixtures') -Filter $FixturePattern |
    Sort-Object Name |
    Select-Object -First $FixtureCount
if ($fixtures.Count -ne $FixtureCount) { throw "Expected $FixtureCount fixture(s) matching '$FixturePattern'." }

$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType "application/json" -Body $loginBody -TimeoutSec 20
if (-not $login.success) { throw "Login failed." }
$token = $login.data.accessToken
$headers = @{ Authorization = "Bearer $token" }

# A throttle of four stays below the upload endpoint's 5 req/s limit. Larger fixture sets are sent
# in successive groups as workers become free, so AI-consumer queueing can be observed safely.
$uploads = $fixtures | ForEach-Object -Parallel {
    $timer = [Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-RestMethod -Method Post -Uri "$using:base/api/resumes/upload" `
            -Headers $using:headers -Form @{ file = Get-Item -LiteralPath $_.FullName } -TimeoutSec 60
        $timer.Stop()
        [PSCustomObject]@{
            File = $_.Name; UploadMs = [math]::Round($timer.Elapsed.TotalMilliseconds, 1)
            Success = [bool]$response.success; ResumeId = $response.data.resume.id
            ApiCode = $response.code; Error = $null
        }
    } catch {
        $timer.Stop()
        [PSCustomObject]@{
            File = $_.Name; UploadMs = [math]::Round($timer.Elapsed.TotalMilliseconds, 1)
            Success = $false; ResumeId = $null; ApiCode = $null; Error = $_.Exception.Message
        }
    }
} -ThrottleLimit 4

$started = Get-Date
$pending = @($uploads | Where-Object { $_.Success -and $_.ResumeId })
$finished = @()
while ($pending.Count -gt 0 -and ((Get-Date) - $started).TotalSeconds -lt $PollSeconds) {
    Start-Sleep -Seconds 2
    $next = @()
    foreach ($upload in $pending) {
        try {
            $detail = Invoke-RestMethod -Method Get -Uri "$base/api/resumes/$($upload.ResumeId)/detail" -Headers $headers -TimeoutSec 30
            $status = [string]$detail.data.analyzeStatus
            if ($status -in @('COMPLETED', 'FAILED')) {
                $finished += [PSCustomObject]@{
                    File = $upload.File; ResumeId = $upload.ResumeId; UploadMs = $upload.UploadMs
                    Status = $status; EndToEndMs = [math]::Round(((Get-Date) - $started).TotalMilliseconds, 1)
                    Analyses = @($detail.data.analyses).Count; Error = $detail.data.analyzeError
                }
            } else { $next += $upload }
        } catch { $next += $upload }
    }
    $pending = $next
}
foreach ($upload in $pending) {
    $finished += [PSCustomObject]@{
        File = $upload.File; ResumeId = $upload.ResumeId; UploadMs = $upload.UploadMs
        Status = 'TIMEOUT'; EndToEndMs = [math]::Round(((Get-Date) - $started).TotalMilliseconds, 1)
        Analyses = $null; Error = 'Polling timeout'
    }
}

[PSCustomObject]@{
    StartedAt = $started.ToString('o')
    Uploads = $uploads
    Results = $finished | Sort-Object File
    Summary = [PSCustomObject]@{
        Submitted = $uploads.Count
        UploadSucceeded = @($uploads | Where-Object Success).Count
        Completed = @($finished | Where-Object Status -eq 'COMPLETED').Count
        Failed = @($finished | Where-Object Status -eq 'FAILED').Count
        TimedOut = @($finished | Where-Object Status -eq 'TIMEOUT').Count
    }
} | ConvertTo-Json -Depth 6
