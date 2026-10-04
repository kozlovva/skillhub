# SkillHub CLI

Установка скиллов из SkillHub в проекты, поиск, публикация.

## Установка

```bash
npx @skillhub/cli --help
# или глобально
npm i -g @skillhub/cli
```

## Использование

```bash
skillhub login --url https://skillhub.internal   # токен из веб-UI (Settings → API tokens)
skillhub whoami
skillhub search pdf --limit 10
skillhub install pdf-export@1.2.3                # в .opencode/skills или .claude/skills (авто-детект)
skillhub install team-skills                     # пак, latest
skillhub publish ./my-skill                      # нужен manifest.json
```

## manifest.json

```json
{
  "name": "PDF Export",
  "version": "1.2.3",
  "description": "Export skills for PDFs",
  "type": "SKILL",
  "visibility": "PUBLIC",
  "tags": ["pdf", "export"],
  "changelog": "First release"
}
```

`slug` — опционален, иначе выводится из `name` (kebab-case).

## Конфигурация

`~/.skillhub/config.json` (создаётся `login`); переменные `SKILLHUB_URL`, `SKILLHUB_TOKEN` переопределяют его — удобно в CI.

## Коды выхода

- `0` — успех
- `1` — ошибка пользователя (нет токена, 404/409/422, невалидный manifest)
- `2` — сеть/сервер (недоступен API, 5xx)
