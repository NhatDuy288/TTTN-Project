param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$DemoPassword = $env:DEMO_USER_PASSWORD
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($DemoPassword)) {
    $envFile = Join-Path $PSScriptRoot '..\.env'
    if (Test-Path -LiteralPath $envFile) {
        $line = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^DEMO_USER_PASSWORD=' } | Select-Object -First 1
        if ($line) { $DemoPassword = ($line -split '=', 2)[1] }
    }
}
if ([string]::IsNullOrWhiteSpace($DemoPassword)) {
    throw 'Set DEMO_USER_PASSWORD or pass -DemoPassword.'
}
$BaseUrl = $BaseUrl.TrimEnd('/')

$ready = $false
for ($attempt = 0; $attempt -lt 15; $attempt++) {
    try {
        $probe = Invoke-WebRequest -Uri ($BaseUrl + '/login') -TimeoutSec 2
        if ($probe.StatusCode -eq 200) { $ready = $true; break }
    } catch {
        Start-Sleep -Seconds 1
    }
}
if (-not $ready) { throw 'App did not become ready within the smoke-test window.' }

function Assert-Status {
    param($Session, [string]$Path, [int]$Expected, [string]$Contains = '')
    $response = Invoke-WebRequest -Uri ($BaseUrl + $Path) -WebSession $Session -SkipHttpErrorCheck
    if ($response.StatusCode -ne $Expected) {
        throw "$Path returned $($response.StatusCode), expected $Expected."
    }
    if ($Contains -and -not $response.Content.Contains($Contains)) {
        throw "$Path did not contain expected content."
    }
    Write-Output "$Path $Expected"
}

function Login-Demo {
    param([string]$Username)
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $login = Invoke-WebRequest -Uri ($BaseUrl + '/login') -WebSession $session
    $csrf = [regex]::Match($login.Content, 'name="_csrf"[^>]*value="([^"]+)"').Groups[1].Value
    if (-not $csrf) {
        $csrf = [regex]::Match($login.Content, 'value="([^"]+)"[^>]*name="_csrf"').Groups[1].Value
    }
    if (-not $csrf) { throw 'Login CSRF token was not found.' }
    $body = @{ username = $Username; password = $DemoPassword; _csrf = $csrf }
    Invoke-WebRequest -Uri ($BaseUrl + '/login') -Method Post -Body $body -WebSession $session | Out-Null
    return $session
}

$roles = @{
    requester_demo = 'REQUESTER'
    request_approver_demo = 'REQUEST_APPROVER'
    inventory_staff_demo = 'INVENTORY_STAFF'
    inventory_approver_demo = 'INVENTORY_APPROVER'
    warehouse_keeper_demo = 'WAREHOUSE_KEEPER'
}
$sessions = @{}
foreach ($username in $roles.Keys) {
    $sessions[$username] = Login-Demo $username
    Assert-Status $sessions[$username] '/dashboard' 200 $roles[$username]
    Assert-Status $sessions[$username] '/materials' 200
}
Assert-Status $sessions['requester_demo'] '/request-approvals' 403
Assert-Status $sessions['request_approver_demo'] '/request-approvals' 200
Assert-Status $sessions['warehouse_keeper_demo'] '/reports/detailed-inventory' 403
Assert-Status $sessions['inventory_staff_demo'] '/reports/detailed-inventory' 200
Assert-Status $sessions['requester_demo'] '/workflow-history/TRANSACTION/999999999' 403
Assert-Status $sessions['inventory_staff_demo'] '/workflow-history/TRANSACTION/999999999' 404
Write-Output 'Authenticated workflow smoke passed.'
