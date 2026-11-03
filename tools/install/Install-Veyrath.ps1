<#
.SYNOPSIS
    Installs the VEYRATH development profile for the official Minecraft Launcher, in its own game
    folder, without changing existing profiles, worlds or game folders.

.DESCRIPTION
    What it does, in order:
      1. Checks that the Minecraft Launcher folder exists and that the launcher is closed.
      2. Backs up launcher_profiles.json (launcher_profiles.json.veyrath-backup-<time>).
      3. Installs the Fabric Loader version (versions\fabric-loader-<loader>-<mc>) from Fabric's
         official meta server, the same files Fabric's own installer writes.
      4. Creates the VEYRATH game folder (default %APPDATA%\.minecraft\instances\veyrath) with mods,
         shaderpacks and config.
      5. Downloads the mods and shader packs listed in modpack\modpack.json from Modrinth's API
         and verifies each file's SHA-512.
      6. Copies the locally built VEYRATH mod jar into the mods folder.
      7. Adds one profile named "VEYRATH" to launcher_profiles.json, pointing at that game folder.
      8. Writes veyrath-install.json in the game folder: every file installed, version, hash and
         licence, so re-running updates cleanly and uninstalling removes exactly those files.

    It never deletes or edits other profiles, other version folders, or the default game folder.
    Signing in to Minecraft and pressing Play stay manual: the script cannot and does not touch
    your Microsoft account.

.PARAMETER MinecraftDir
    Launcher data folder (contains launcher_profiles.json and versions). Default: %APPDATA%\.minecraft

.PARAMETER GameDir
    VEYRATH game folder (saves, mods, config, shaderpacks). Default: <MinecraftDir>\instances\veyrath

.PARAMETER VeyrathJar
    Path to the built VEYRATH mod jar. Default: the newest mod-fabric\build\libs\veyrath-*.jar of
    this repository.

.PARAMETER MemoryGB
    Maximum Java heap for the profile. Default: half the PC's RAM, clamped to 6..12 GB.

.PARAMETER SkipC2ME
    Do not install C2ME (for troubleshooting chunk generation).

.PARAMETER NoShaders
    Do not download shader packs.

.PARAMETER Uninstall
    Remove the VEYRATH profile and the files this script installed (the game folder itself, with
    your VEYRATH worlds, is kept unless -RemoveGameDir is also given).

.PARAMETER RemoveGameDir
    With -Uninstall: also delete the VEYRATH game folder, including VEYRATH worlds.

.PARAMETER Force
    Continue even if the Minecraft Launcher seems to be running (it may overwrite the change).

.PARAMETER ModrinthApi
    Modrinth API base URL (for testing against a mock server). Default: https://api.modrinth.com/v2

.PARAMETER FabricMeta
    Fabric meta base URL (for testing against a mock server). Default: https://meta.fabricmc.net/v2

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\install\Install-Veyrath.ps1

.NOTES
    Compatible with Windows PowerShell 5.1 and PowerShell 7.
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$MinecraftDir,
    [string]$GameDir,
    [string]$VeyrathJar,
    [int]$MemoryGB = 0,
    [switch]$SkipC2ME,
    [switch]$NoShaders,
    [switch]$Uninstall,
    [switch]$RemoveGameDir,
    [switch]$Force,
    [string]$ModrinthApi = 'https://api.modrinth.com/v2',
    [string]$FabricMeta = 'https://meta.fabricmc.net/v2'
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # Invoke-WebRequest is very slow with the progress bar on 5.1

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir '..\..')
# Modrinth asks API clients for a uniquely identifying User-Agent with a contact. Kept to the
# strict HTTP product-token form, which PowerShell 7 validates.
$UserAgent = 'veyrath-installer/0.1 (github.com/robb-devo/veyrath)'
$Manifest = Get-Content -Raw -Path (Join-Path $RepoRoot 'modpack\modpack.json') | ConvertFrom-Json
$InstallRecordName = 'veyrath-install.json'

if (-not $MinecraftDir) { $MinecraftDir = Join-Path $env:APPDATA '.minecraft' }
# Inside the launcher folder on purpose: the Microsoft Store launcher (a packaged app) does not
# see game folders elsewhere in %APPDATA% that it did not create itself. A profile pointing at
# %APPDATA%\.minecraft-veyrath started with an empty mods folder (Fabric loaded 4 built-ins).
if (-not $GameDir) { $GameDir = Join-Path $MinecraftDir 'instances\veyrath' }

# Windows PowerShell 5.1 defaults to old TLS versions; Modrinth and Fabric need TLS 1.2+.
try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12 } catch { }

function Write-Step([string]$Message) { Write-Host "==> $Message" -ForegroundColor Cyan }
function Write-Note([string]$Message) { Write-Host "    $Message" }
function Write-Warn([string]$Message) { Write-Host "    WARNING: $Message" -ForegroundColor Yellow }

function Get-Json([string]$Url) {
    return Invoke-RestMethod -Uri $Url -UserAgent $UserAgent -UseBasicParsing
}

# JSON arrays from Invoke-RestMethod arrive enumerated in Windows PowerShell 5.1 but as one array
# object in PowerShell 7; this always returns a flat array (empty when the reply is empty).
function Get-JsonArray([string]$Url) {
    $reply = Invoke-RestMethod -Uri $Url -UserAgent $UserAgent -UseBasicParsing
    $list = New-Object System.Collections.ArrayList
    foreach ($item in $reply) { if ($null -ne $item) { [void]$list.Add($item) } }
    return ,($list.ToArray())
}

function Get-FileSha512([string]$Path) {
    return (Get-FileHash -Algorithm SHA512 -Path $Path).Hash.ToLowerInvariant()
}

function Save-Download([string]$Url, [string]$Path, [string]$Sha512) {
    $tmp = "$Path.part"
    Invoke-WebRequest -Uri $Url -OutFile $tmp -UserAgent $UserAgent -UseBasicParsing
    if ($Sha512) {
        $actual = Get-FileSha512 $tmp
        if ($actual -ne $Sha512.ToLowerInvariant()) {
            Remove-Item -LiteralPath $tmp -Force
            throw "SHA-512 mismatch for $Url"
        }
    }
    Move-Item -LiteralPath $tmp -Destination $Path -Force
}

function ConvertTo-QueryArray([string[]]$Values) {
    $json = '[' + (($Values | ForEach-Object { '"' + $_ + '"' }) -join ',') + ']'
    return [Uri]::EscapeDataString($json)
}

# Picks a Modrinth version: the pinned version_number if present, otherwise the newest release
# (or the newest pre-release when no release exists). Returns $null when nothing is compatible.
function Select-ModrinthVersion([string]$Slug, [string[]]$Loaders, [string[]]$GameVersions, $Pin) {
    $url = "$ModrinthApi/project/$Slug/version?loaders=$(ConvertTo-QueryArray $Loaders)"
    if ($GameVersions) { $url += "&game_versions=$(ConvertTo-QueryArray $GameVersions)" }
    $versions = Get-JsonArray $url
    if ($versions.Count -eq 0) { return $null }
    if ($Pin) {
        $match = @($versions | Where-Object { $_.version_number -eq $Pin })
        if ($match.Count -gt 0) { return $match[0] }
        Write-Warn "$Slug $Pin not found on Modrinth for $($GameVersions -join ','); using the newest compatible version instead."
    }
    $releases = @($versions | Where-Object { $_.version_type -eq 'release' } | Sort-Object { [DateTime]$_.date_published } -Descending)
    if ($releases.Count -gt 0) { return $releases[0] }
    $sorted = @($versions | Sort-Object { [DateTime]$_.date_published } -Descending)
    Write-Warn "$Slug has no release build for this setup; using pre-release $($sorted[0].version_number)."
    return $sorted[0]
}

function Get-PrimaryFile($Version) {
    $files = @($Version.files)
    $primary = @($files | Where-Object { $_.primary })
    if ($primary.Count -gt 0) { return $primary[0] }
    return $files[0]
}

function Test-LauncherRunning {
    $names = @('MinecraftLauncher', 'Minecraft', 'Minecraft Launcher')
    foreach ($n in $names) {
        if (Get-Process -Name $n -ErrorAction SilentlyContinue) { return $true }
    }
    return $false
}

# PowerShell 6+ turns ISO date strings into DateTime objects when parsing JSON and writes them
# back in another format, which would silently rewrite every other profile's "created" and
# "lastUsed". Dates are therefore tagged as plain strings on the way in and untagged on the way
# out, so other profiles round-trip unchanged on every PowerShell version.
$DateTag = '~veyrath-date~'

function Read-LauncherProfiles([string]$Path) {
    $text = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    $text = [regex]::Replace($text, '"(\d{4}-\d{2}-\d{2}T[0-9:.]+(?:Z|[+-]\d{2}:?\d{2})?)"', ('"' + $DateTag + '$1"'))
    return ConvertFrom-Json -InputObject $text
}

function Write-LauncherProfiles($Data, [string]$Path) {
    $json = ConvertTo-Json -InputObject $Data -Depth 64
    $json = $json.Replace('"' + $DateTag, '"')
    # Write UTF-8 without BOM (the launcher's own format).
    [System.IO.File]::WriteAllText($Path, $json, (New-Object System.Text.UTF8Encoding($false)))
}

function Get-DefaultMemoryGB {
    try {
        $bytes = (Get-CimInstance -ClassName Win32_ComputerSystem).TotalPhysicalMemory
        $gb = [int][Math]::Floor($bytes / 1GB / 2)
        return [Math]::Max(6, [Math]::Min(12, $gb))
    } catch {
        return 8
    }
}

$ProfilesPath = Join-Path $MinecraftDir 'launcher_profiles.json'
$RecordPath = Join-Path $GameDir $InstallRecordName
$ProfileId = $Manifest.profileId
$Mc = $Manifest.minecraft
$Loader = $Manifest.fabricLoader
$VersionId = "fabric-loader-$Loader-$Mc"

# ---------------------------------------------------------------------------------------------
Write-Step "VEYRATH installer (Minecraft $Mc, Fabric Loader $Loader)"
Write-Note "Launcher folder: $MinecraftDir"
Write-Note "VEYRATH game folder: $GameDir"

if (-not (Test-Path -LiteralPath $ProfilesPath)) {
    throw "No launcher_profiles.json in '$MinecraftDir'. Install and start the official Minecraft Launcher once, or pass -MinecraftDir with the folder that contains launcher_profiles.json."
}
if ((Test-LauncherRunning) -and -not $Force) {
    throw "The Minecraft Launcher is running. Close it completely (it rewrites launcher_profiles.json on exit), then run this script again. Use -Force to continue anyway."
}
if ([IO.Path]::GetFullPath($GameDir).TrimEnd('\', '/') -eq [IO.Path]::GetFullPath($MinecraftDir).TrimEnd('\', '/')) {
    throw "GameDir must not be the launcher folder itself; VEYRATH uses its own game folder."
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = "$ProfilesPath.veyrath-backup-$stamp"
if ($PSCmdlet.ShouldProcess($ProfilesPath, "Back up to $backup")) {
    Copy-Item -LiteralPath $ProfilesPath -Destination $backup
    Write-Note "Backed up launcher_profiles.json to $backup"
}

$previous = $null
if (Test-Path -LiteralPath $RecordPath) { $previous = Get-Content -Raw -Path $RecordPath | ConvertFrom-Json }

# ---------------------------------------------------------------------------------------------
if ($Uninstall) {
    Write-Step 'Removing the VEYRATH launcher profile'
    $data = Read-LauncherProfiles $ProfilesPath
    if ($data.profiles.PSObject.Properties.Name -contains $ProfileId) {
        if ($PSCmdlet.ShouldProcess($ProfilesPath, "Remove profile '$ProfileId'")) {
            $data.profiles.PSObject.Properties.Remove($ProfileId)
            Write-LauncherProfiles $data $ProfilesPath
        }
    } else {
        Write-Note 'No VEYRATH profile found.'
    }
    if ($previous) {
        foreach ($f in @($previous.files)) {
            $p = Join-Path $GameDir $f.path
            if ((Test-Path -LiteralPath $p) -and $PSCmdlet.ShouldProcess($p, 'Delete installed file')) { Remove-Item -LiteralPath $p -Force }
        }
        if ($PSCmdlet.ShouldProcess($RecordPath, 'Delete install record')) { Remove-Item -LiteralPath $RecordPath -Force }
    }
    if ($RemoveGameDir -and (Test-Path -LiteralPath $GameDir)) {
        if ($PSCmdlet.ShouldProcess($GameDir, 'Delete the VEYRATH game folder, including VEYRATH worlds')) { Remove-Item -LiteralPath $GameDir -Recurse -Force }
    }
    Write-Note "The Fabric version folder 'versions\$VersionId' is left in place; other profiles may use it."
    Write-Step 'Done.'
    return
}

# ---------------------------------------------------------------------------------------------
Write-Step "Installing Fabric Loader version $VersionId"
$versionDir = Join-Path (Join-Path $MinecraftDir 'versions') $VersionId
$versionJson = Join-Path $versionDir "$VersionId.json"
if (Test-Path -LiteralPath $versionJson) {
    Write-Note 'Already installed.'
} elseif ($PSCmdlet.ShouldProcess($versionDir, 'Create Fabric version')) {
    New-Item -ItemType Directory -Force -Path $versionDir | Out-Null
    Save-Download "$FabricMeta/versions/loader/$Mc/$Loader/profile/json" $versionJson $null
    # Fabric's installer creates an empty jar next to the json; the launcher expects the file.
    $jar = Join-Path $versionDir "$VersionId.jar"
    if (-not (Test-Path -LiteralPath $jar)) { New-Item -ItemType File -Path $jar | Out-Null }
    Write-Note "Wrote $versionJson"
}

# ---------------------------------------------------------------------------------------------
Write-Step 'Preparing the VEYRATH game folder'
foreach ($d in @('', 'mods', 'shaderpacks', 'config', 'resourcepacks')) {
    $p = Join-Path $GameDir $d
    if (-not (Test-Path -LiteralPath $p) -and $PSCmdlet.ShouldProcess($p, 'Create folder')) { New-Item -ItemType Directory -Force -Path $p | Out-Null }
}

# Files from the previous run that this run does not install again are removed (updates).
$installed = New-Object System.Collections.ArrayList
$keep = @{}

function Install-ModrinthFile($Entry, [string]$Kind, [string[]]$Loaders, [string]$Folder) {
    $gameVersions = @($Mc)
    $version = Select-ModrinthVersion $Entry.slug $Loaders $gameVersions $Entry.pin
    if (-not $version -and $Kind -eq 'shader') {
        # Shader packs often list few exact game versions; any Iris-compatible build is fine.
        $version = Select-ModrinthVersion $Entry.slug $Loaders $null $Entry.pin
    }
    if (-not $version) {
        Write-Warn "No $Kind build of '$($Entry.slug)' for Minecraft $Mc on Modrinth. Skipped."
        return $null
    }
    $file = Get-PrimaryFile $version
    $rel = Join-Path $Folder $file.filename
    $dest = Join-Path $GameDir $rel
    $sha = $file.hashes.sha512
    if ((Test-Path -LiteralPath $dest) -and ((Get-FileSha512 $dest) -eq $sha)) {
        Write-Note ("{0,-24} {1} (already present)" -f $Entry.slug, $version.version_number)
    } elseif ($PSCmdlet.ShouldProcess($dest, "Download $($Entry.slug) $($version.version_number)")) {
        Save-Download $file.url $dest $sha
        Write-Note ("{0,-24} {1}" -f $Entry.slug, $version.version_number)
    }
    $project = $null
    try { $project = Get-Json "$ModrinthApi/project/$($Entry.slug)" } catch { }
    $license = if ($project -and $project.license) { $project.license.id } else { 'unknown' }
    $script:keep[$rel] = $true
    [void]$script:installed.Add([ordered]@{
        kind = $Kind; slug = $Entry.slug; project_id = $version.project_id; version = $version.version_number
        version_id = $version.id; path = $rel; sha512 = $sha; license = $license; pinned = [bool]($Entry.pin -and $Entry.pin -eq $version.version_number)
    })
    return $version
}

Write-Step 'Downloading mods from Modrinth'
$modVersions = @()
foreach ($m in @($Manifest.mods)) {
    if (($m.PSObject.Properties.Name -contains 'optionalFlag') -and $m.optionalFlag -eq 'SkipC2ME' -and $SkipC2ME) {
        Write-Note ("{0,-24} skipped (-SkipC2ME)" -f $m.slug)
        continue
    }
    $v = Install-ModrinthFile $m 'mod' @('fabric') 'mods'
    if ($v) { $modVersions += $v }
}

# Required dependencies that are not part of the pack.
$have = @{}
foreach ($v in $modVersions) { $have[$v.project_id] = $true }
foreach ($v in $modVersions) {
    foreach ($dep in @($v.dependencies)) {
        if ($dep -and $dep.dependency_type -eq 'required' -and $dep.project_id -and -not $have.ContainsKey($dep.project_id)) {
            Write-Warn "$($v.name) requires Modrinth project $($dep.project_id), which is not in modpack.json."
        }
        if ($dep -and $dep.dependency_type -eq 'incompatible' -and $dep.project_id -and $have.ContainsKey($dep.project_id)) {
            Write-Warn "$($v.name) declares itself incompatible with project $($dep.project_id), which is installed."
        }
    }
}

$activeShader = $null
if (-not $NoShaders) {
    Write-Step 'Downloading shader packs from Modrinth'
    foreach ($s in @($Manifest.shaders)) {
        $v = Install-ModrinthFile $s 'shader' @('iris') 'shaderpacks'
        if ($v -and $s.active) { $activeShader = (Get-PrimaryFile $v).filename }
    }
}

# ---------------------------------------------------------------------------------------------
Write-Step 'Installing the VEYRATH mod'
if (-not $VeyrathJar) {
    $libs = Join-Path $RepoRoot 'mod-fabric\build\libs'
    if (Test-Path -LiteralPath $libs) {
        $candidate = Get-ChildItem -LiteralPath $libs -Filter 'veyrath-*.jar' |
            Where-Object { $_.Name -notmatch '-(sources|dev|javadoc)\.jar$' } |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($candidate) { $VeyrathJar = $candidate.FullName }
    }
}
if ($VeyrathJar -and (Test-Path -LiteralPath $VeyrathJar)) {
    $rel = Join-Path 'mods' (Split-Path -Leaf $VeyrathJar)
    if ($PSCmdlet.ShouldProcess((Join-Path $GameDir $rel), 'Copy VEYRATH mod')) {
        Copy-Item -LiteralPath $VeyrathJar -Destination (Join-Path $GameDir $rel) -Force
    }
    $keep[$rel] = $true
    [void]$installed.Add([ordered]@{ kind = 'mod'; slug = 'veyrath'; version = 'local build'; path = $rel; sha512 = (Get-FileSha512 $VeyrathJar); license = 'All-Rights-Reserved' })
    Write-Note "Copied $VeyrathJar"
} else {
    Write-Warn 'No VEYRATH mod jar found. Build it first (gradlew :mod-fabric:build) or pass -VeyrathJar. The profile is created anyway.'
}

if ($previous) {
    foreach ($f in @($previous.files)) {
        if ($NoShaders -and $f.kind -eq 'shader') {
            # -NoShaders means "do not download", not "remove": keep them and their record.
            if (-not $keep.ContainsKey($f.path)) { [void]$installed.Add($f); $keep[$f.path] = $true }
            continue
        }
        if (-not $keep.ContainsKey($f.path)) {
            $p = Join-Path $GameDir $f.path
            if ((Test-Path -LiteralPath $p) -and $PSCmdlet.ShouldProcess($p, 'Remove file from previous install')) {
                Remove-Item -LiteralPath $p -Force
                Write-Note "Removed outdated $($f.path)"
            }
        }
    }
}

# ---------------------------------------------------------------------------------------------
Write-Step 'Writing default settings (only files that do not exist yet)'
$irisConfig = Join-Path $GameDir 'config\iris.properties'
if ($activeShader -and -not (Test-Path -LiteralPath $irisConfig) -and $PSCmdlet.ShouldProcess($irisConfig, 'Select shader pack')) {
    Set-Content -Path $irisConfig -Encoding ASCII -Value @("enableShaders=true", "shaderPack=$activeShader")
    Write-Note "Iris: $activeShader selected"
}
$options = Join-Path $GameDir 'options.txt'
if (-not (Test-Path -LiteralPath $options) -and $PSCmdlet.ShouldProcess($options, 'Write starting video options')) {
    # Distant Horizons draws the far terrain; vanilla chunks only need to cover the near field.
    Set-Content -Path $options -Encoding ASCII -Value @('renderDistance:12', 'simulationDistance:8')
    Write-Note 'options.txt: render distance 12, simulation distance 8'
}

# ---------------------------------------------------------------------------------------------
Write-Step 'Adding the VEYRATH launcher profile'
if ($MemoryGB -le 0) { $MemoryGB = Get-DefaultMemoryGB }
$javaArgs = "-Xmx$($MemoryGB)G -Xms2G -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+ParallelRefProcEnabled -Dveyrath.profile=launcher"
$iconPath = Join-Path $ScriptDir 'profile-icon.png'
$icon = 'Grass'
if (Test-Path -LiteralPath $iconPath) { $icon = 'data:image/png;base64,' + [Convert]::ToBase64String([IO.File]::ReadAllBytes($iconPath)) }
$now = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss.fff'Z'")

$data = Read-LauncherProfiles $ProfilesPath
if (-not ($data.PSObject.Properties.Name -contains 'profiles')) {
    $data | Add-Member -NotePropertyName 'profiles' -NotePropertyValue ([pscustomobject]@{})
}
$created = $now
if ($data.profiles.PSObject.Properties.Name -contains $ProfileId) {
    $old = $data.profiles.$ProfileId
    if ($old.PSObject.Properties.Name -contains 'created') { $created = $old.created }
    $data.profiles.PSObject.Properties.Remove($ProfileId)
}
$profile = [pscustomobject][ordered]@{
    created       = $created
    gameDir       = [IO.Path]::GetFullPath($GameDir)
    icon          = $icon
    javaArgs      = $javaArgs
    lastUsed      = $now
    lastVersionId = $VersionId
    name          = $Manifest.profileName
    type          = 'custom'
}
$data.profiles | Add-Member -NotePropertyName $ProfileId -NotePropertyValue $profile
if ($PSCmdlet.ShouldProcess($ProfilesPath, "Add profile '$($Manifest.profileName)'")) {
    Write-LauncherProfiles $data $ProfilesPath
    # Read it back: a broken file would hide every profile in the launcher.
    $check = Read-LauncherProfiles $ProfilesPath
    if (-not ($check.profiles.PSObject.Properties.Name -contains $ProfileId)) {
        Copy-Item -LiteralPath $backup -Destination $ProfilesPath -Force
        throw 'launcher_profiles.json did not read back correctly; the backup was restored.'
    }
    Write-Note "Profile '$($Manifest.profileName)' -> $VersionId, game folder $GameDir, $($MemoryGB) GB heap"
}

# ---------------------------------------------------------------------------------------------
$record = [ordered]@{
    installer = 'tools/install/Install-Veyrath.ps1'
    installed_at = $now
    minecraft = $Mc
    fabric_loader = $Loader
    version_id = $VersionId
    profile_id = $ProfileId
    files = @($installed)
}
if ($PSCmdlet.ShouldProcess($RecordPath, 'Write install record')) {
    [System.IO.File]::WriteAllText($RecordPath, (ConvertTo-Json -InputObject $record -Depth 8), (New-Object System.Text.UTF8Encoding($false)))
}

Write-Step 'Installed.'
Write-Host ''
Write-Host 'Next steps (manual, they need you):'
Write-Host '  1. Open the Minecraft Launcher and sign in if asked.'
Write-Host "  2. Choose the 'VEYRATH' installation and press Play. The first start downloads Minecraft $Mc and Fabric's libraries."
Write-Host "  3. Singleplayer > Create New World > World tab > World Type: VEYRATH."
Write-Host "Install record: $RecordPath"
