<#
  download-images.ps1 v3 — โหลดรูปจาก URL ตรง (ไม่ใช้ search API แล้ว = ไม่เจอ 429 จาก API)
  ใช้: powershell -ExecutionPolicy Bypass -File .\download-images.ps1
#>
param([string]$Only = "")

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$ErrorActionPreference = "Stop"
$ua = @{ "User-Agent" = "tiaoma-z-local-image-setup/1.0" }

$root     = $PSScriptRoot
$imgDir   = Join-Path $root "src\main\resources\static\image"
$placesP  = Join-Path $root "src\main\resources\seed\places.json"
$credJson = Join-Path $root "src\main\resources\static\image-credits.json"
$credHtml = Join-Path $root "src\main\resources\static\credits.html"
$enc      = New-Object System.Text.UTF8Encoding($false)

# รูปที่ตรวจ license แล้วจาก Wikimedia Commons metadata (9 ที่ค้าง)
$images = @(
  @{ id = "sao-din-na-noi"; ext = "jpg"; url = "https://thumb.wikimedia.org/wikipedia/commons/thumb/9/99/Sao_Din_Na_Noi_%28Hom_Chom%29_and_Kok_Sua.jpg/1920px-Sao_Din_Na_Noi_%28Hom_Chom%29_and_Kok_Sua.jpg"
     title = "Sao Din Na Noi (Hom Chom) and Kok Sua.jpg"; artist = "KOSIN SUKHUM"; license = "CC BY-SA 4.0"; licUrl = ""
     src = "https://commons.wikimedia.org/wiki/File:Sao_Din_Na_Noi_(Hom_Chom)_and_Kok_Sua.jpg" }
  @{ id = "wat-chong-kham"; ext = "jpg"; url = "https://thumb.wikimedia.org/wikipedia/commons/thumb/6/6d/Wat_Chong_Kham_4.jpg/1920px-Wat_Chong_Kham_4.jpg"
     title = "Wat Chong Kham 4.jpg"; artist = "Christophe95"; license = "CC BY-SA 4.0"; licUrl = "https://creativecommons.org/licenses/by-sa/4.0"
     src = "https://commons.wikimedia.org/wiki/File:Wat_Chong_Kham_4.jpg" }
  @{ id = "ban-rak-thai"; ext = "jpg"; url = "https://upload.wikimedia.org/wikipedia/commons/8/82/Ban_Rak_Thai_Mae_Hong_Sorn_Thailand_%28125159247%29.jpeg"
     title = "Ban Rak Thai Mae Hong Sorn Thailand (125159247).jpeg"; artist = "Wichian Ratanatongchai"; license = "CC BY-SA 3.0"; licUrl = "https://creativecommons.org/licenses/by-sa/3.0"
     src = "https://commons.wikimedia.org/wiki/File:Ban_Rak_Thai_Mae_Hong_Sorn_Thailand_(125159247).jpeg" }
  @{ id = "sukhothai-historical-park"; ext = "jpg"; url = "https://thumb.wikimedia.org/wikipedia/commons/thumb/2/28/Wat_Mahathat%2C_Sukhothai_%28I%29.jpg/1920px-Wat_Mahathat%2C_Sukhothai_%28I%29.jpg"
     title = "Wat Mahathat, Sukhothai (I).jpg"; artist = "Supanut Arunoprayote"; license = "CC BY 4.0"; licUrl = "https://creativecommons.org/licenses/by/4.0"
     src = "https://commons.wikimedia.org/wiki/File:Wat_Mahathat,_Sukhothai_(I).jpg" }
  @{ id = "si-satchanalai"; ext = "jpg"; url = "https://thumb.wikimedia.org/wikipedia/commons/thumb/4/4c/13th_Century_Thai_City_of_Si_Satchanalai-_Wat_Phra_Si_Rattana_Mahathat-_1.jpg/1920px-13th_Century_Thai_City_of_Si_Satchanalai-_Wat_Phra_Si_Rattana_Mahathat-_1.jpg"
     title = "13th Century Thai City of Si Satchanalai- Wat Phra Si Rattana Mahathat- 1.jpg"; artist = "Gary Todd"; license = "Public domain"; licUrl = ""
     src = "https://commons.wikimedia.org/wiki/File:13th_Century_Thai_City_of_Si_Satchanalai-_Wat_Phra_Si_Rattana_Mahathat-_1.jpg" }
  @{ id = "khum-chao-luang"; ext = "jpg"; url = "https://upload.wikimedia.org/wikipedia/commons/a/a9/Khum_Chao_Luang_Muang_Phrae8.jpg"
     title = "Khum Chao Luang Muang Phrae8.jpg"; artist = "LannaPhoto"; license = "CC BY-SA 4.0"; licUrl = "https://creativecommons.org/licenses/by-sa/4.0"
     src = "https://commons.wikimedia.org/wiki/File:Khum_Chao_Luang_Muang_Phrae8.jpg" }
  @{ id = "wat-phrathat-pha-sorn-kaew"; ext = "jpg"; url = "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a5/Wat_Pha_Sorn_Kaew_13.jpg/1920px-Wat_Pha_Sorn_Kaew_13.jpg"
     title = "Wat Pha Sorn Kaew 13.jpg"; artist = "Stevehhaigh"; license = "CC BY-SA 3.0"; licUrl = "https://creativecommons.org/licenses/by-sa/3.0"
     src = "https://commons.wikimedia.org/wiki/File:Wat_Pha_Sorn_Kaew_13.jpg" }
  @{ id = "phu-thap-boek"; ext = "jpg"; url = "https://upload.wikimedia.org/wikipedia/commons/7/7f/Phu_Thap_Buek71.JPG"
     title = "Phu Thap Buek71.JPG"; artist = "Xufanc"; license = "CC BY-SA 3.0"; licUrl = "https://creativecommons.org/licenses/by-sa/3.0"
     src = "https://commons.wikimedia.org/wiki/File:Phu_Thap_Buek71.JPG" }
  @{ id = "thi-lo-su"; ext = "jpg"; url = "https://thumb.wikimedia.org/wikipedia/commons/thumb/9/96/Thi_Lo_Su_Waterfall%2C_Umphang_Wildlife_Sanctuary%2C_Tak_Province%2C_Thailand.jpg/1920px-Thi_Lo_Su_Waterfall%2C_Umphang_Wildlife_Sanctuary%2C_Tak_Province%2C_Thailand.jpg"
     title = "Thi Lo Su Waterfall, Umphang Wildlife Sanctuary, Tak Province, Thailand.jpg"; artist = "Snobbird"; license = "CC BY-SA 4.0"; licUrl = "https://creativecommons.org/licenses/by-sa/4.0"
     src = "https://commons.wikimedia.org/wiki/File:Thi_Lo_Su_Waterfall,_Umphang_Wildlife_Sanctuary,_Tak_Province,_Thailand.jpg" }
)
if ($Only) { $images = @($images | Where-Object { $_.id -eq $Only }) }

# download with 429 retry
function Invoke-Wei([string]$Uri, [string]$OutFile) {
  for ($i = 1; $i -le 4; $i++) {
    try {
      if ($OutFile) { Invoke-WebRequest -Uri $Uri -OutFile $OutFile -Headers $ua -UseBasicParsing; return }
      return (Invoke-RestMethod -Uri $Uri -Headers $ua)
    }
    catch {
      if ($_.Exception.Message -match '429' -and $i -lt 4) {
        $wait = 30 * $i
        Write-Host ("... 429, wait {0}s (try {1}/4)" -f $wait, $i) -ForegroundColor Yellow
        Start-Sleep -Seconds $wait
        continue
      }
      throw
    }
  }
}

$creds = @{}
if (Test-Path $credJson) {
  foreach ($e in (Get-Content $credJson -Raw -Encoding UTF8 | ConvertFrom-Json)) { $creds[$e.id] = $e }
}
$placeMap = @{}
$placesRaw = [IO.File]::ReadAllText($placesP, [Text.Encoding]::UTF8)
foreach ($p in ($placesRaw | ConvertFrom-Json)) { $placeMap[$p.id] = $p.name }

$done = 0; $skip = 0; $fail = @()

foreach ($im in $images) {
  $id = $im.id
  try {
    $hasFile = (Test-Path (Join-Path $imgDir "$id.jpg") -PathType Leaf) -or
               (Test-Path (Join-Path $imgDir "$id.png") -PathType Leaf)
    if ($hasFile -and $creds.ContainsKey($id)) {
      Write-Host ("skip {0} - already has image" -f $id) -ForegroundColor DarkGray; $skip++; continue
    }

    $out = Join-Path $imgDir ("{0}.{1}" -f $id, $im.ext)
    Invoke-Wei -Uri $im.url -OutFile $out
    if ((Get-Item $out).Length -lt 10000) { Remove-Item $out -Force; throw "downloaded file too small" }

    # update places.json image field
    $newPath = "image/$id.$($im.ext)"
    $pattern = '("id"\s*:\s*"' + [regex]::Escape($id) + '"(?:(?!"id"\s*:).)*?"image"\s*:\s*")[^"]*(")'
    $updated = [regex]::Replace($placesRaw, $pattern, ('${1}' + $newPath + '${2}'), [Text.RegularExpressions.RegexOptions]::Singleline)
    if ($updated -eq $placesRaw) { throw "id block not found in places.json" }
    $placesRaw = $updated

    $creds[$id] = [pscustomobject]@{
      id = $id; name = $placeMap[$id]; title = $im.title
      artist = $im.artist; license = $im.license; licenseUrl = $im.licUrl; source = $im.src
    }
    Write-Host ("OK  {0} - {1} [{2}]" -f $id, $im.title, $im.license) -ForegroundColor Green
    $done++
    Start-Sleep -Milliseconds 3000
  }
  catch {
    Write-Host ("FAIL {0} - {1}" -f $id, $_.Exception.Message) -ForegroundColor Red
    $fail += $id
  }
}

if ($done -gt 0) { [IO.File]::WriteAllText($placesP, $placesRaw, $enc) }

$credList = @($creds.Values | Sort-Object name)
[IO.File]::WriteAllText($credJson, ($credList | ConvertTo-Json -Depth 5), $enc)

# ---- credits.html ----
$rows = ""
foreach ($c in $credList) {
  $licLink = if ($c.licenseUrl) { $c.licenseUrl } else { $c.source }
  $rows += "<tr><td>" + [Net.WebUtility]::HtmlEncode([string]$c.name) + "</td>" +
           "<td>" + [Net.WebUtility]::HtmlEncode([string]$c.artist) + "</td>" +
           "<td><a href='" + $licLink + "' target='_blank' rel='noopener'>" + [Net.WebUtility]::HtmlEncode([string]$c.license) + "</a></td>" +
           "<td><a href='" + $c.source + "' target='_blank' rel='noopener'>Wikimedia Commons</a></td></tr>`n"
}
$html = @"
<!DOCTYPE html>
<html lang="th">
<head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>เครดิตภาพ | tiaoma-Z</title>
<style>
 body{font-family:system-ui,'Noto Sans Thai',sans-serif;margin:0;background:#0f172a;color:#e2e8f0;padding:24px}
 h1{font-size:1.4rem} p{color:#94a3b8;font-size:.9rem}
 table{width:100%;border-collapse:collapse;font-size:.9rem;margin-top:16px}
 th,td{border-bottom:1px solid #334155;padding:10px 8px;text-align:left;vertical-align:top}
 th{color:#7dd3fc} a{color:#7dd3fc}
</style>
</head>
<body>
<h1>เครดิตภาพ / Image Credits</h1>
<p>ภาพถ่ายที่ใช้ในเว็บนี้มาจาก Wikimedia Commons ภายใต้สัญญาอนุญาตแบบ Creative Commons หรือสาธารณะ ตามที่ระบุไว้ในตาราง
ผู้สร้างภาพเป็นเจ้าของลิขสิทธิ์เดิม การใช้งานเป็นไปตามเงื่อนไขของแต่ละสัญญาอนุญาต</p>
<table>
<tr><th>สถานที่</th><th>ผู้สร้าง</th><th>สัญญาอนุญาต</th><th>แหล่งที่มา</th></tr>
$rows
</table>
</body>
</html>
"@
[IO.File]::WriteAllText($credHtml, $html, $enc)

Write-Host ""
Write-Host ("=== DONE: new {0} | skipped {1} | failed {2} ===" -f $done, $skip, $fail.Count)
if ($fail.Count -gt 0) { Write-Host ("failed: " + ($fail -join ", ")) -ForegroundColor Yellow }
Write-Host "credits page: src\main\resources\static\credits.html