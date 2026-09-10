# =====================================================================
#  Internet Access Control  (Windows 64-bit)
# ---------------------------------------------------------------------
#  Opens a checklist of all installed/running applications.
#    CHECKED   = Internet BLOCKED  (an outbound Windows Firewall rule)
#    UNCHECKED = Internet ALLOWED  (no block rule)
#
#  Buttons: Check All | Uncheck All | Refresh | Apply Rules | Add program | Done
#  Requires Administrator rights (a UAC prompt appears automatically).
# =====================================================================

$ErrorActionPreference = 'SilentlyContinue'

# ---------------------------------------------------------------------
# Self-elevate: relaunch as Administrator if needed (UAC).
# ---------------------------------------------------------------------
$principal = New-Object Security.Principal.WindowsPrincipal(
    [Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Start-Process -FilePath 'powershell.exe' -Verb RunAs -ArgumentList (
        '-NoProfile -ExecutionPolicy Bypass -File "{0}"' -f $PSCommandPath)
    exit
}

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$script:RulePrefix = 'IAC-Block-'
$script:ConfigDir  = Join-Path $env:LOCALAPPDATA 'InternetAccessControl'
$script:ConfigFile = Join-Path $script:ConfigDir 'state.json'

# ---------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------
function Get-Hash([string]$s) {
    $md5 = [System.Security.Cryptography.MD5]::Create()
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($s)
    $hex = [System.BitConverter]::ToString($md5.ComputeHash($bytes)) -replace '-', ''
    return $hex.Substring(0, 8).ToLower()
}

function Invoke-Netsh([string]$arguments) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = Join-Path $env:SystemRoot 'System32\netsh.exe'
    $psi.Arguments = $arguments
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $p = New-Object System.Diagnostics.Process
    $p.StartInfo = $psi
    [void]$p.Start()
    $p.WaitForExit()
    return $p.ExitCode
}

function Get-Apps {
    # Returns a hashtable: exe path -> display name.
    $apps = @{}
    $shell = New-Object -ComObject WScript.Shell

    # 1) Installed programs (Start Menu shortcuts -> exe)
    $startDirs = @(
        (Join-Path $env:ProgramData 'Microsoft\Windows\Start Menu\Programs'),
        (Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs')
    )
    foreach ($dir in $startDirs) {
        if (Test-Path $dir) {
            Get-ChildItem -Path $dir -Filter '*.lnk' -Recurse | ForEach-Object {
                try {
                    $target = $shell.CreateShortcut($_.FullName).TargetPath
                    if ($target -and $target -match '\.exe$' -and (Test-Path $target)) {
                        if (-not $apps.ContainsKey($target)) { $apps[$target] = $_.BaseName }
                    }
                } catch { }
            }
        }
    }

    # 2) Currently running programs (Windows system folders are excluded).
    foreach ($p in (Get-Process)) {
        try {
            $path = $p.Path
        } catch {
            $path = $null
        }
        if ($path -and $path -match '\.exe$' -and $path -notmatch '^C:\\Windows\\' -and (Test-Path $path)) {
            if (-not $apps.ContainsKey($path)) { $apps[$path] = $p.ProcessName }
        }
    }

    return $apps
}

function Load-State {
    if (Test-Path $script:ConfigFile) {
        try { return @(Get-Content $script:ConfigFile -Raw | ConvertFrom-Json) } catch { return @() }
    }
    return @()
}

# ---------------------------------------------------------------------
# Build the form
# ---------------------------------------------------------------------
$script:form = New-Object System.Windows.Forms.Form
$script:form.Text = 'Internet Access Control'
$script:form.ClientSize = New-Object System.Drawing.Size(720, 540)
$script:form.StartPosition = 'CenterScreen'
$script:form.MinimumSize = New-Object System.Drawing.Size(640, 460)
$script:form.Font = New-Object System.Drawing.Font('Segoe UI', 9)

$lbl = New-Object System.Windows.Forms.Label
$lbl.Text = 'CHECKED = Internet BLOCKED      UNCHECKED = Internet ALLOWED'
$lbl.Location = New-Object System.Drawing.Point(12, 10)
$lbl.AutoSize = $true
$lbl.Font = New-Object System.Drawing.Font('Segoe UI', 10, [System.Drawing.FontStyle]::Bold)
$lbl.ForeColor = [System.Drawing.Color]::Firebrick
$script:form.Controls.Add($lbl)

$script:lv = New-Object System.Windows.Forms.ListView
$script:lv.Location = New-Object System.Drawing.Point(12, 40)
$script:lv.Size = New-Object System.Drawing.Size(696, 420)
$script:lv.View = [System.Windows.Forms.View]::Details
$script:lv.CheckBoxes = $true
$script:lv.FullRowSelect = $true
$script:lv.GridLines = $true
$script:lv.Anchor = [System.Windows.Forms.AnchorStyles]::Top -bor [System.Windows.Forms.AnchorStyles]::Bottom -bor [System.Windows.Forms.AnchorStyles]::Left -bor [System.Windows.Forms.AnchorStyles]::Right
$script:lv.Columns.Add('Application', 240) | Out-Null
$script:lv.Columns.Add('Program path', 436) | Out-Null
$script:form.Controls.Add($script:lv)

function Add-ItemRow([string]$path, [string]$name, [bool]$checked) {
    $item = New-Object System.Windows.Forms.ListViewItem($name)
    $item.SubItems.Add($path) | Out-Null
    $item.Checked = $checked
    $item.Tag = $path
    $script:lv.Items.Add($item) | Out-Null
}

# Load saved check state
$savedChecks = @{}
foreach ($e in (Load-State)) {
    $savedChecks[[string]$e.path] = [bool]$e.checked
}

# Populate the list
$script:apps = Get-Apps
foreach ($path in ($script:apps.Keys | Sort-Object { $script:apps[$_] })) {
    $chk = $false
    if ($savedChecks.ContainsKey($path)) { $chk = $savedChecks[$path] }
    Add-ItemRow $path $script:apps[$path] $chk
}

# Buttons
function New-Button([string]$text, [int]$x, [int]$width) {
    $b = New-Object System.Windows.Forms.Button
    $b.Text = $text
    $b.Location = New-Object System.Drawing.Point($x, 468)
    $b.Size = New-Object System.Drawing.Size($width, 32)
    $b.Anchor = [System.Windows.Forms.AnchorStyles]::Bottom -bor [System.Windows.Forms.AnchorStyles]::Left
    $script:form.Controls.Add($b)
    return $b
}

$btnCheckAll   = New-Button 'Check All' 12 100
$btnUncheckAll = New-Button 'Uncheck All' 118 100
$btnRefresh    = New-Button 'Refresh' 224 90
$btnApply      = New-Button 'Apply Rules' 320 110
$btnAdd        = New-Button 'Add program...' 436 110
$btnDone       = New-Button 'Done' 552 90

$script:status = New-Object System.Windows.Forms.Label
$script:status.Location = New-Object System.Drawing.Point(12, 506)
$script:status.AutoSize = $true
$script:status.Anchor = [System.Windows.Forms.AnchorStyles]::Bottom -bor [System.Windows.Forms.AnchorStyles]::Left
$script:status.Text = ('{0} applications loaded.' -f $script:lv.Items.Count)
$script:form.Controls.Add($script:status)

# ---------------------------------------------------------------------
# Button actions
# ---------------------------------------------------------------------
$btnCheckAll.Add_Click({
    foreach ($it in $script:lv.Items) { $it.Checked = $true }
    $script:status.Text = 'All checked = everything will be BLOCKED after Apply.'
})

$btnUncheckAll.Add_Click({
    foreach ($it in $script:lv.Items) { $it.Checked = $false }
    $script:status.Text = 'All unchecked = everything keeps internet after Apply.'
})

$btnRefresh.Add_Click({
    $checks = @{}
    foreach ($it in $script:lv.Items) { $checks[[string]$it.Tag] = [bool]$it.Checked }
    $script:lv.Items.Clear()
    $script:apps = Get-Apps
    foreach ($path in ($script:apps.Keys | Sort-Object { $script:apps[$_] })) {
        $chk = $false
        if ($checks.ContainsKey($path)) { $chk = $checks[$path] }
        Add-ItemRow $path $script:apps[$path] $chk
    }
    $script:status.Text = ('{0} applications loaded.' -f $script:lv.Items.Count)
})

$btnApply.Add_Click({
    $blocked = 0; $allowed = 0; $failed = 0
    $state = @()
    foreach ($it in $script:lv.Items) {
        $path = [string]$it.Tag
        $name = $it.Text
        $hash = Get-Hash $path
        $ruleName = $script:RulePrefix + ($name -replace '[^A-Za-z0-9_\- ]', '') + '-' + $hash
        if ($it.Checked) {
            $code = Invoke-Netsh ('advfirewall firewall add rule name="{0}" dir=out action=block program="{1}" enable=yes' -f $ruleName, $path)
            if ($code -eq 0) { $blocked++ } else { $failed++ }
        } else {
            [void](Invoke-Netsh ('advfirewall firewall delete rule name="{0}"' -f $ruleName))
            $allowed++
        }
        $state += [pscustomobject]@{ path = $path; name = $name; checked = [bool]$it.Checked }
    }
    if (-not (Test-Path $script:ConfigDir)) { New-Item -ItemType Directory -Force -Path $script:ConfigDir | Out-Null }
    $state | ConvertTo-Json | Set-Content -Path $script:ConfigFile -Encoding UTF8
    $script:status.Text = ('Applied. Blocked: {0}   Allowed: {1}   Errors: {2}' -f $blocked, $allowed, $failed)
    [System.Windows.Forms.MessageBox]::Show(
        ('Done.\n\nBlocked (no internet): {0}\nAllowed (internet on): {1}\nErrors: {2}' -f $blocked, $allowed, $failed),
        'Internet Access Control', 'OK', 'Information') | Out-Null
})

$btnAdd.Add_Click({
    $dlg = New-Object System.Windows.Forms.OpenFileDialog
    $dlg.Filter = 'Executable (*.exe)|*.exe|All files (*.*)|*.*'
    $dlg.Title = 'Select a program to add to the list'
    if ($dlg.ShowDialog($script:form) -eq [System.Windows.Forms.DialogResult]::OK) {
        $p = $dlg.FileName
        $n = [System.IO.Path]::GetFileNameWithoutExtension($p)
        Add-ItemRow $p $n $false
        $script:status.Text = ('Added: {0} (unchecked = internet allowed).' -f $p)
    }
})

$btnDone.Add_Click({ $script:form.Close() })

# ---------------------------------------------------------------------
# Show the window
# ---------------------------------------------------------------------
[void]$script:form.ShowDialog()
