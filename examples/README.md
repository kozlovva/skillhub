# Примеры элементов SkillHub

Набор реальных скиллов разного содержания — для демонстрации формата, публикации
и установки. Каждый пример — самостоятельная папка с `manifest.json`, готовая к
`skillhub publish`.

## Каталог примеров

| Пример | Тип | Скрипты | Файлы | Что демонстрирует |
|--------|-----|---------|-------|-------------------|
| `code-review-checklist` | SKILL | нет | только SKILL.md | Чистый дисциплинарный скилл-чеклист: два прохода ревью, формат вердикта, red flags |
| `commit-messages` | SKILL | нет | SKILL.md + `templates/commit-template.txt` | Скилл с поддерживающим файлом-шаблоном (Conventional Commits) |
| `json-validate` | SKILL | Node (`scripts/json-validate.mjs`) | SKILL.md + скрипт | Скилл-утилита: валидация JSON с точным line/col ошибки, режим каталога, `--fix-indent` |
| `env-snapshot` | SCRIPT | PowerShell (`scripts/env-snapshot.ps1`) | README.md + скрипт | Элемент типа **SCRIPT** (не SKILL): самодостаточный скрипт снимка окружения |
| `regex-cookbook` | SKILL | нет | SKILL.md + `regex-patterns.md` | Reference-скилл: краткая шпаргалка в SKILL.md, тяжёлый справочник в отдельном файле |
| `../skills/code-stats` | SKILL | Node (`scripts/code-stats.mjs`) | SKILL.md + скрипт + манифест | Статистика кода по расширениям; уже опубликован как `code-stats@1.0.0` |

## Публикация и установка

```bash
cd cli && npm run build

# опубликовать любой пример (из папки cli)
node dist/index.js publish ../examples/json-validate

# установить в проект (в .opencode/skills или .claude/skills — авто-детект)
node dist/index.js install json-validate

# проверить
node .opencode/skills/json-validate/scripts/json-validate.mjs .
```

## Разновидности формата

- **SKILL.md с frontmatter** — обязателен для типа SKILL: `name`, `description`
  (третье лицо, «Use when...», только триггеры, без пересказа процесса)
- **`scripts/`** — исполняемые инструменты, путь указывается в SKILL.md
- **`templates/`, справочники** — поддерживающие файлы для тяжёлого контента
- **manifest.json** — обязателен для публикации: `name`, `version` (semver),
  `type` (SKILL/SCRIPT/AGENT/HOOK/PACK/OTHER), опционально `slug`, `tags`,
  `visibility` (PUBLIC/TEAM)
- **PACK** — составной элемент: создаётся отдельно, элементы добавляются в него
  через API (`/packs/{slug}/contents`); обычный `publish` для него не используется
