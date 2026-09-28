# split-places.ps1 - split seed\places.json into one file per region (run once)
# Save as UTF-8 WITH BOM (required: script contains Thai strings).
# Run from repo root:
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\split-places.ps1
$ErrorActionPreference = 'Stop'

$root     = $PSScriptRoot
$seedDir  = Join-Path $root 'src\main\resources\seed'
$jsPath   = Join-Path $root 'src\main\resources\static\places-data.js'
$placesPath = Join-Path $seedDir 'places.json'
$backupPath = Join-Path $root 'places.json.bak'
$regions  = @('north', 'west', 'isan', 'central', 'east', 'south')

if (!(Test-Path $placesPath)) { Write-Host 'ERROR: places.json not found'; exit 1 }
if (!(Test-Path $jsPath))     { Write-Host 'ERROR: places-data.js not found'; exit 1 }

# already split before? -> stop (never overwrite existing region files)
$regionFiles = @($regions | ForEach-Object { Join-Path $seedDir ("places-{0}.json" -f $_) })
$existing = @($regionFiles | Where-Object { Test-Path $_ })
if ($existing.Count -gt 0) {
    Write-Host 'ERROR: region files already exist. Nothing was changed.'
    exit 1
}

# --- parse PROVINCE_TO_REGION from places-data.js (single source of truth) ---
$js = Get-Content $jsPath -Raw -Encoding UTF8
if ($js -notmatch '(?s)const PROVINCE_TO_REGION = \{(.*?)\};') {
    Write-Host 'ERROR: PROVINCE_TO_REGION not found in places-data.js'
    exit 1
}
$block = $Matches[1]
$map = @{}
foreach ($m in [regex]::Matches($block, "(?s)'([^']+)'\s*:\s*'([^']+)'")) {
    $map[$m.Groups[1].Value] = $m.Groups[2].Value
}
if ($map.Count -eq 0) { Write-Host 'ERROR: mapping parsed is empty'; exit 1 }

# region (thai) -> file slug (english)
$slug = @{
    'เหนือ'              = 'north'
    'ตะวันออกเฉียงเหนือ' = 'isan'
    'กลาง'               = 'central'
    'ตะวันออก'           = 'east'
    'ตะวันตก'            = 'west'
    'ใต้'                = 'south'
}

# --- read places.json ---
try {
    $places = Get-Content $placesPath -Raw -Encoding UTF8 | ConvertFrom-Json
} catch {
    Write-Host ('ERROR: cannot parse places.json -> ' + $_.Exception.Message)
    exit 1
}
if ($null -eq $places) { Write-Host 'ERROR: places.json is empty (already split?)'; exit 1 }
$places = @($places)

# duplicate id warning (seeder will keep only first)
$dupIds = @($places | Group-Object id | Where-Object { $_.Count -gt 1 })
foreach ($d in $dupIds) { Write-Host ("WARNING duplicate id: {0} (x{1})" -f $d.Name, $d.Count) }

# --- fix known typo: 4 west places saved wrong province ---
$fixIds = @('phra-nakhon-khiri', 'mrigadayavan', 'kaeng-krachan', 'cha-am-beach')
$fixed = 0
foreach ($p in $places) {
    if (($fixIds -contains $p.id) -and ($p.province -eq 'เพชรบูรณ์')) {
        $p.province = 'เพชรบุรี'
        $fixed++
    }
}
if ($fixed -gt 0) { Write-Host ("fixed wrong province on {0} places (-> Phetchaburi)" -f $fixed) }

# --- group by region ---
$groups = @{}
$unmapped = @()
foreach ($p in $places) {
    $prov  = [string]$p.province
    $region = $null
    if ($map.ContainsKey($prov)) {
        $region = $map[$prov]
    } else {
        foreach ($k in $map.Keys) {
            if ($prov -like ("*{0}*" -f $k)) { $region = $map[$k]; break }
        }
    }
    $s = $null
    if ($null -ne $region -and $slug.ContainsKey($region)) { $s = $slug[$region] }
    if ($null -eq $s) { $unmapped += $p; continue }
    if (-not $groups.ContainsKey($s)) { $groups[$s] = @() }
    $groups[$s] += ,$p
}

if ($unmapped.Count -gt 0) {
    foreach ($u in $unmapped) { Write-Host ("UNMAPPED: {0} ({1})" -f $u.id, $u.province) }
    Write-Host 'ERROR: some provinces not in mapping. NOTHING was written.'
    exit 1
}

# --- backup original ---
Copy-Item $placesPath $backupPath -Force
Write-Host ("backup -> {0}" -f $backupPath)

# --- write one file per region ---
$total = 0
foreach ($s in $regions) {
    $arr = @()
    if ($groups.ContainsKey($s)) { $arr = @($groups[$s]) }
    $total += $arr.Count
    $out = Join-Path $seedDir ("places-{0}.json" -f $s)
    ConvertTo-Json -InputObject $arr -Depth 100 | Set-Content -Path $out -Encoding UTF8
    Write-Host ("{0,-8}: {1,3} places -> places-{2}.json" -f $s, $arr.Count, $s)
}

# all entries went to region files -> combined file becomes empty
Set-Content -Path $placesPath -Value '[]' -Encoding UTF8

if ($total -ne $places.Count) {
    Write-Host ("ERROR: count mismatch {0} vs {1} - restore from {2}" -f $total, $places.Count, $backupPath)
    exit 1
}
Write-Host ("DONE: {0} places split into {1} region files." -f $total, $regions.Count)