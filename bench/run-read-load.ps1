param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [Parameter(Mandatory = $true)][string]$Username,
    [Parameter(Mandatory = $true)][string]$Password,
    [int[]]$ConcurrencyStages = @(1, 5, 10, 20, 40),
    [int]$RequestsPerWorker = 20
)

$ErrorActionPreference = "Stop"
$base = $BaseUrl.TrimEnd('/')

# Uses an authenticated, read-only endpoint. It does not upload files or invoke AI.
$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" `
    -ContentType "application/json" -Body $loginBody -TimeoutSec 20
if (-not $login.success) {
    throw "Login failed."
}
$token = $login.data.accessToken
$targetUri = "$base/api/resumes"

foreach ($concurrency in $ConcurrencyStages) {
    $requestCount = $concurrency * $RequestsPerWorker
    $wall = [Diagnostics.Stopwatch]::StartNew()
    $results = 1..$requestCount | ForEach-Object -Parallel {
        $stopwatch = [Diagnostics.Stopwatch]::StartNew()
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $using:targetUri `
                -Headers @{ Authorization = "Bearer $using:token" } -TimeoutSec 20
            $stopwatch.Stop()
            [PSCustomObject]@{
                Success = ($response.StatusCode -eq 200)
                Milliseconds = $stopwatch.Elapsed.TotalMilliseconds
                Error = $null
            }
        } catch {
            $stopwatch.Stop()
            [PSCustomObject]@{
                Success = $false
                Milliseconds = $stopwatch.Elapsed.TotalMilliseconds
                Error = $_.Exception.Message
            }
        }
    } -ThrottleLimit $concurrency
    $wall.Stop()

    $latencies = @($results | Where-Object Success | ForEach-Object Milliseconds | Sort-Object)
    $successCount = $latencies.Count
    $p50Index = [math]::Floor(($successCount - 1) * 0.50)
    $p95Index = [math]::Floor(($successCount - 1) * 0.95)
    $failureSample = @($results | Where-Object { -not $_.Success } | Select-Object -First 3 -ExpandProperty Error) -join " | "
    [PSCustomObject]@{
        Concurrency = $concurrency
        Requests = $requestCount
        Succeeded = $successCount
        Failed = $requestCount - $successCount
        Rps = [math]::Round($successCount / $wall.Elapsed.TotalSeconds, 2)
        P50Ms = if ($successCount) { [math]::Round($latencies[$p50Index], 1) } else { $null }
        P95Ms = if ($successCount) { [math]::Round($latencies[$p95Index], 1) } else { $null }
        MaxMs = if ($successCount) { [math]::Round($latencies[$successCount - 1], 1) } else { $null }
        FailureSample = $failureSample
    }

    if ($successCount -lt $requestCount) {
        Write-Warning "Stopping after the first failed stage; inspect the application console for the matching server-side exception."
        break
    }
}
