# Env Snapshot: report tool versions, key env vars and disk space.
# Usage: .\env-snapshot.ps1 [-Full] [-OutFile <path>]

param(
    [switch]$Full,
    [string]$OutFile
)

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("=== Environment Snapshot: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===")
$lines.Add("")
$lines.Add("--- Machine ---")
$lines.Add("OS       : $([System.Environment]::OSVersion.VersionString)")
$lines.Add("Host     : $env:COMPUTERNAME")
$lines.Add("User     : $env:USERNAME")
$lines.Add("PS       : $($PSVersionTable.PSVersion)")
$lines.Add("")

$lines.Add("--- Tools ---")
$tools = @(
    @{ Name = 'git';    Args = '--version' },
    @{ Name = 'node';   Args = '--version' },
    @{ Name = 'npm';    Args = '--version' },
    @{ Name = 'java';   Args = '-version' },
    @{ Name = 'docker'; Args = '--version' },
    @{ Name = 'mvn';    Args = '--version' }
)
foreach ($t in $tools) {
    $cmd = Get-Command $t.Name -ErrorAction SilentlyContinue
    if (-not $cmd) {
        $lines.Add("{0,-8}: not found" -f $t.Name)
        continue
    }
    try {
        $output = (& $t.Name $t.Args 2>&1 | Select-Object -First 1) -join ' '
        $lines.Add("{0,-8}: {1}" -f $t.Name, $output.Trim())
    } catch {
        $lines.Add("{0,-8}: present, failed to run: {1}" -f $t.Name, $_.Exception.Message)
    }
}
$lines.Add("")

$lines.Add("--- Project env vars (SKILLHUB_*, JAVA_*, NODE_*) ---")
Get-ChildItem env: | Where-Object { $_.Name -match '^(SKILLHUB|JAVA|NODE)_' } | ForEach-Object {
    $lines.Add("$($_.Name) = $($_.Value)")
}
$lines.Add("")

if ($Full) {
    $lines.Add("--- All env vars ---")
    Get-ChildItem env: | Sort-Object Name | ForEach-Object {
        $lines.Add("$($_.Name) = $($_.Value)")
    }
    $lines.Add("")
}

$lines.Add("--- Disk space ---")
Get-PSDrive -PSProvider FileSystem | ForEach-Object {
    if ($_.Free -ne $null) {
        $freeGb = [math]::Round($_.Free / 1GB, 1)
        $usedGb = [math]::Round(($_.Used) / 1GB, 1)
        $lines.Add("{0}: used {1} GB, free {2} GB" -f $_.Name, $usedGb, $freeGb)
    }
}

$report = $lines -join [Environment]::NewLine
if ($OutFile) {
    Set-Content -LiteralPath $OutFile -Value $report -Encoding UTF8
    Write-Output "Snapshot written to $OutFile"
} else {
    Write-Output $report
}
