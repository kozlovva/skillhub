# SkillHub demo seed. Usage: .\scripts\seed-demo.ps1 [-ApiBase http://localhost:8080]
param(
    [string]$ApiBase = "http://localhost:8080",
    [string]$DemoToken = "skh_demo_token_2026_skillhub"
)

$ErrorActionPreference = "Stop"

function Sha256Hex([string]$s) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    ($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($s)) | ForEach-Object { $_.ToString("x2") }) -join ""
}

$headers = @{ Authorization = "Bearer $DemoToken" }
$headersJson = @{ Authorization = "Bearer $DemoToken"; "Content-Type" = "application/json" }

function Invoke-Json([string]$Method, [string]$Uri, $Body) {
    $json = $Body | ConvertTo-Json -Depth 5
    Invoke-RestMethod -Uri "$ApiBase$Uri" -Method $Method -Headers $headersJson -Body $json
}

# --- 1. Admin user + API token (direct SQL, auth chicken-and-egg) ---
$demoUserId = "11111111-1111-1111-1111-111111111111"
$tokenHash = Sha256Hex $DemoToken

$sql = @"
INSERT INTO users (id, sso_subject, email, display_name, is_admin)
VALUES ('$demoUserId', 'seed-admin', 'admin@skillhub.io', 'Demo Admin', TRUE)
ON CONFLICT (sso_subject) DO UPDATE SET is_admin = TRUE;

INSERT INTO api_tokens (user_id, name, token_hash)
SELECT '$demoUserId', 'seed', '$tokenHash'
WHERE NOT EXISTS (SELECT 1 FROM api_tokens WHERE token_hash = '$tokenHash');
"@
$sql | docker compose exec -T postgres psql -U skillhub -v ON_ERROR_STOP=1 2>$null
if ($LASTEXITCODE -ne 0) { throw "seed SQL failed" }
Write-Host "[1/5] admin user + api token seeded"

# --- 2. Teams ---
foreach ($t in @(
    @{ slug = "platform"; name = "Platform Team" },
    @{ slug = "ai-lab";  name = "AI Lab" }
)) {
    try { Invoke-Json POST "/api/teams" $t | Out-Null } catch { if ($_.Exception.Response.StatusCode.value__ -ne 409) { throw } }
}
Write-Host "[2/5] teams created"

# --- 3. Categories ---
foreach ($c in @(
    @{ slug = "dev-tools";   name = "Dev Tools" },
    @{ slug = "ai";          name = "AI" },
    @{ slug = "documents";   name = "Documents" }
)) {
    try { Invoke-Json POST "/api/categories" $c | Out-Null } catch { if ($_.Exception.Response.StatusCode.value__ -ne 409) { throw } }
}
Write-Host "[3/5] categories created"

# --- 4. Elements + versions (zip with manifest.json) ---
$zipDir = Join-Path $env:TEMP "skillhub-seed"
Remove-Item -Recurse -Force $zipDir -ErrorAction SilentlyContinue | Out-Null
New-Item -ItemType Directory -Path $zipDir | Out-Null

function Publish-Version([string]$slug, [string]$version, [string]$changelog, [string]$displayName) {
    $dir = Join-Path $zipDir "$slug-$version"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $manifest = @{ name = $displayName; version = $version; description = "Demo skill $slug"; type = "SKILL" } | ConvertTo-Json
    # UTF-8 без BOM: Jackson не переваривает 0xFEFF
    [IO.File]::WriteAllText("$dir\manifest.json", $manifest, (New-Object System.Text.UTF8Encoding($false)))
    Set-Content -Path "$dir\SKILL.md" -Value "# $displayName`n`nDemo content for $slug v$version.`n" -Encoding UTF8
    $zip = Join-Path $zipDir "$slug-$version.zip"
    Compress-Archive -Path "$dir\*" -DestinationPath $zip -Force
    curl.exe -s -o NUL -w "%{http_code}" -X POST `
        -H "Authorization: Bearer $DemoToken" `
        -F "file=@$zip" `
        "$ApiBase/api/elements/$slug/versions?changelog=$([uri]::EscapeDataString($changelog))"
}

$elements = @(
    @{ slug = "pdf-report-skill"; type = "SKILL"; name = "PDF Report Skill"; description = "Генерирует PDF-отчёты из markdown с графиками и таблицами."; team = "platform"; category = "dev-tools"; tags = @("pdf", "reports", "docs"); visibility = "PUBLIC"; versions = @(
        @{ v = "1.0.0"; changelog = "Initial release"; rating = 5; review = "Отлично работает, красиво оформляет отчёты." },
        @{ v = "1.1.0"; changelog = "Поддержка таблиц и кириллицы в шрифтах" }) },
    @{ slug = "code-review-agent"; type = "AGENT"; name = "Code Review Agent"; description = "AI-агент для ревью pull request'ов: ищет баги, уязвимости и стилистические проблемы."; team = "ai-lab"; category = "ai"; tags = @("review", "ai", "code-quality"); visibility = "PUBLIC"; versions = @(
        @{ v = "0.9.0"; changelog = "Beta"; rating = 4; review = "Хорошо ловит типовые баги." },
        @{ v = "1.0.0"; changelog = "Стабильный релиз, интеграция с GitLab"; rating = 5; review = "После 1.0 заметно умнее." }) },
    @{ slug = "git-convention-check"; type = "SCRIPT"; name = "Git Convention Check"; description = "Скрипт проверки commit messages и веток на соответствие конвенции команды."; team = "platform"; category = "dev-tools"; tags = @("git", "ci"); visibility = "PUBLIC"; versions = @(
        @{ v = "2.1.0"; changelog = "Support conventional commits + scopes" }) },
    @{ slug = "meeting-notes-skill"; type = "SKILL"; name = "Meeting Notes Skill"; description = "Скилл для саммаризации встреч: action items, решения, вопросы."; team = "ai-lab"; category = "ai"; tags = @("meetings", "summary"); visibility = "TEAM"; versions = @(
        @{ v = "0.3.0"; changelog = "Internal draft" }) },
    @{ slug = "onboarding-pack"; type = "PACK"; name = "New Joiner Onboarding Pack"; description = "Пак для новичка: доклады, гит-конвенции и PDF-отчёты."; team = "platform"; category = "dev-tools"; tags = @("onboarding", "pack"); visibility = "PUBLIC"; versions = @() }
)

foreach ($e in $elements) {
    try { Invoke-Json POST "/api/elements" $e | Out-Null; Write-Host "  + element $($e.slug)" }
    catch { if ($_.Exception.Response.StatusCode.value__ -ne 409) { throw } }
}
foreach ($e in $elements) {
    foreach ($v in $e.versions) {
        $code = Publish-Version -slug $e.slug -version $v.v -changelog $v.changelog -displayName $e.name
        Write-Host "  + version $($e.slug)@$($v.v) -> HTTP $code"
    }
}

# Pack contents
try { Invoke-Json POST "/api/packs/onboarding-pack/contents" @{ element = "pdf-report-skill"; versionConstraint = "latest" } | Out-Null } catch {}
try { Invoke-Json POST "/api/packs/onboarding-pack/contents" @{ element = "git-convention-check"; versionConstraint = "latest" } | Out-Null } catch {}
Write-Host "[4/5] elements, versions and pack contents seeded"

# --- 5. Social: ratings, reviews, favorites ---
foreach ($e in $elements) {
    foreach ($v in $e.versions) {
        if ($v.rating) {
            try { Invoke-Json PUT "/api/elements/$($e.slug)/rating" @{ rating = $v.rating } | Out-Null } catch {}
            try { Invoke-Json PUT "/api/elements/$($e.slug)/review" @{ rating = $v.rating; text = $v.review } | Out-Null } catch {}
        }
    }
}
try { Invoke-RestMethod -Uri "$ApiBase/api/elements/pdf-report-skill/favorite" -Method Post -Headers $headers | Out-Null } catch {}
Write-Host "[5/5] ratings, reviews and favorites seeded"

Write-Host "`nDone. Open http://localhost:3000 — catalog now has $($elements.Count) elements."
Write-Host "Demo API token (for CLI/agents): $DemoToken"
