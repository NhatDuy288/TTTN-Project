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

$roles = @(
    [pscustomobject]@{ Username = 'requester_demo'; Role = 'REQUESTER' }
    [pscustomobject]@{ Username = 'request_approver_demo'; Role = 'REQUEST_APPROVER' }
    [pscustomobject]@{ Username = 'inventory_staff_demo'; Role = 'INVENTORY_STAFF' }
    [pscustomobject]@{ Username = 'inventory_approver_demo'; Role = 'INVENTORY_APPROVER' }
    [pscustomobject]@{ Username = 'warehouse_keeper_demo'; Role = 'WAREHOUSE_KEEPER' }
)
$sessions = @{}
foreach ($role in $roles) {
    $sessions[$role.Username] = Login-Demo $role.Username
    Assert-Status $sessions[$role.Username] '/dashboard' 200 $role.Role
}

$allRoles = @($roles.Role)
$endpointMatrix = @(
    [pscustomobject]@{ Path = '/dashboard'; AllowedRoles = $allRoles }
    [pscustomobject]@{ Path = '/materials'; AllowedRoles = $allRoles }
    [pscustomobject]@{ Path = '/materials/new'; AllowedRoles = @('INVENTORY_STAFF') }
    [pscustomobject]@{ Path = '/warehouses'; AllowedRoles = $allRoles }
    [pscustomobject]@{ Path = '/warehouses/new'; AllowedRoles = @('WAREHOUSE_KEEPER') }
    [pscustomobject]@{
        Path = '/purchase-orders'
        AllowedRoles = @('REQUESTER', 'REQUEST_APPROVER', 'INVENTORY_STAFF', 'INVENTORY_APPROVER')
    }
    [pscustomobject]@{
        Path = '/purchase-orders/new'
        AllowedRoles = @('REQUESTER', 'INVENTORY_STAFF')
    }
    [pscustomobject]@{ Path = '/requests'; AllowedRoles = @('REQUESTER') }
    [pscustomobject]@{ Path = '/requests/new'; AllowedRoles = @('REQUESTER') }
    [pscustomobject]@{ Path = '/request-approvals'; AllowedRoles = @('REQUEST_APPROVER') }
    [pscustomobject]@{ Path = '/warehouse-transactions'; AllowedRoles = @('INVENTORY_STAFF') }
    [pscustomobject]@{ Path = '/transaction-approvals'; AllowedRoles = @('INVENTORY_APPROVER') }
    [pscustomobject]@{ Path = '/transaction-confirmations'; AllowedRoles = @('WAREHOUSE_KEEPER') }
    [pscustomobject]@{ Path = '/warehouse-transfers'; AllowedRoles = @('INVENTORY_STAFF') }
    [pscustomobject]@{ Path = '/transfer-approvals'; AllowedRoles = @('INVENTORY_APPROVER') }
    [pscustomobject]@{ Path = '/transfer-confirmations/source'; AllowedRoles = @('WAREHOUSE_KEEPER') }
    [pscustomobject]@{ Path = '/transfer-confirmations/destination'; AllowedRoles = @('WAREHOUSE_KEEPER') }
    [pscustomobject]@{
        Path = '/reports/detailed-inventory'
        AllowedRoles = @('INVENTORY_STAFF', 'INVENTORY_APPROVER')
    }
    [pscustomobject]@{
        Path = '/reports/nxt'
        AllowedRoles = @('INVENTORY_STAFF', 'INVENTORY_APPROVER', 'WAREHOUSE_KEEPER')
    }
    [pscustomobject]@{
        Path = '/reports/stock-card'
        AllowedRoles = @('INVENTORY_STAFF', 'INVENTORY_APPROVER', 'WAREHOUSE_KEEPER')
    }
)

foreach ($endpoint in $endpointMatrix) {
    foreach ($role in $roles) {
        $expected = if ($role.Role -in $endpoint.AllowedRoles) { 200 } else { 403 }
        Assert-Status $sessions[$role.Username] $endpoint.Path $expected
    }
}

Assert-Status $sessions['requester_demo'] '/workflow-history/TRANSACTION/999999999' 403
Assert-Status $sessions['inventory_staff_demo'] '/workflow-history/TRANSACTION/999999999' 404
Write-Output "Authenticated endpoint matrix passed: $($endpointMatrix.Count) endpoints x $($roles.Count) roles."
