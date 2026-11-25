param([ValidateSet(7, 8)][int]$Case = 7)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$config = @{}
Get-Content .env | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $config[$matches[1]] = $matches[2] }
}
if (-not $config['PAYMENT_SERVICE_NAME'] -or -not $config['PAYMENT_TOKEN']) {
    throw 'Set PAYMENT_SERVICE_NAME and PAYMENT_TOKEN in the local .env first.'
}
$cases = @{
    7 = @{ branch = 'hw-07'; accounts = 'acc-7'; ratePerSecond = 5; testCount = 1000; processingTimeMillis = 6000 }
    8 = @{ branch = 'hw-08'; accounts = 'acc-9'; ratePerSecond = 100; testCount = 5000; processingTimeMillis = 20000 }
}
$body = $cases[$Case].Clone()
$body.serviceName = $config['PAYMENT_SERVICE_NAME']
$body.token = $config['PAYMENT_TOKEN']
$body.onPremises = $true
# The remote runner deploys the selected GitHub branch, regardless of the local branch.
Invoke-RestMethod -Method Post -Uri 'http://77.234.215.138:34321/run' `
    -ContentType 'application/json' -Body ($body | ConvertTo-Json) -TimeoutSec 180
