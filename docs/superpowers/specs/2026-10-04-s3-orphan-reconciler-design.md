# Дизайн: фоновая сверка S3-сирот

Дата: 2026-10-04

## Контекст

`VersionUseCase.publish` загружает zip в S3 (VersionUseCase.java:79) внутри `@Transactional`-метода. Если после `storage.upload` транзакция падает (ошибка БД, гонка duplicate version, краш процесса), объект в бакете остаётся без строки в `element_versions` — «сирота». `StoragePort.delete` существует (S3StorageAdapter.java:78), но не вызывается никем; повторная публикация той же версии перезапишет ключ, но до тех пор это мёртвые байты.

Решение: периодическая джоба-сверка (вариант «полная сверка множеств»), раз в час, с грейс-периодом 24 часа. Горячий путь публикации и схема БД не меняются.

## Архитектура

Сверка сравнивает два множества: все объекты бакета (пагинация S3) и все `s3_key` из `element_versions`. Объект удаляется только если его нет в БД и он старше грейса. Гонка с незакоммиченной публикацией исключается грейсом: свежезагруженный объект имеет свежий `lastModified` и не трогается; 24 ч ≫ таймаута любой транзакции.

## Компоненты

1. **`StoragePort.list()`** — новый метод порта, возвращает `List<StorageObjectInfo>`; новый record `StorageObjectInfo(String key, Instant lastModified)` в `domain/model`. `S3StorageAdapter` реализует через `listObjectsV2Paginator` (SDK сам идёт по страницам, лимит страницы по умолчанию 1000). Тестовые фейки реализуют тривиально.

2. **`ElementVersionRepositoryPort.findAllS3Keys()`** — возвращает `Set<String>` всех ключей; JPA-адаптер делает JPQL `select v.s3Key from ...` без загрузки сущностей.

3. **`StorageReconciliationService`** (`application/service`):
   - Публичный метод `reconcile()`, помеченный `@Scheduled(cron = "${skillhub.storage.reconcile-cron:0 0 * * * *}")` — каждый час в минуту 0.
   - Логика: `listed = storage.list()`; `known = versions.findAllS3Keys()`; для каждого `obj` из `listed`, где `!known.contains(obj.key)` и `obj.lastModified <= clock.now() - grace` → `storage.delete(obj.key)` + INFO-лог удалённого ключа.
   - Каждый `delete` — в собственном try/catch (WARN + переход к следующему объекту). Всё тело `reconcile` — в общем try/catch (ERROR-лог), чтобы исключение никогда не валило шедулер.
   - Зависимости: `StoragePort`, `ElementVersionRepositoryPort`, `ClockPort`; grace — `@Value("${skillhub.storage.reconcile-grace:24h}") Duration`.

4. **Конфиг:**
   - `SchedulingConfig` (`config`): `@Configuration @EnableScheduling`.
   - Сервис включается `@ConditionalOnProperty(name = "skillhub.storage.reconcile-enabled", havingValue = "true", matchIfMissing = true)`.
   - Свойства: `skillhub.storage.reconcile-enabled` (default true), `skillhub.storage.reconcile-cron` (default `0 0 * * * *`), `skillhub.storage.reconcile-grace` (default `24h`).

## Ошибки

- S3 недоступен при `list()` — общий catch логирует ERROR, следующая попытка через час.
- Ошибка удаления одного ключа — WARN, остальные продолжают удаляться.
- Пустой бакет — джоба завершается без работы.

## Тесты

- Юнит-тесты `StorageReconciliationService` на фейковых `StoragePort`/`ElementVersionRepositoryPort`/`ClockPort`:
  - сирота старше грейса удаляется;
  - свежая сиропа (моложе грейса) не трогается;
  - ключ из БД не трогается независимо от возраста;
  - неудачный `delete` одного ключа не мешает удалению остальных;
  - пустой бакет — без удалений.
- Тест `S3StorageAdapter.list()` на пагинацию (>1000 объектов — более одной страницы) в существующей S3-тестовой инфре; если инфра не позволяет разложить такое число объектов приемлемо по времени — проверить пагинацию двумя страницами через маленький `max-keys`, зафиксировать выбор в отчёте.

## Вне объёма

- Изменения `VersionUseCase.publish` (в т.ч. try/catch-удаление) и схемы БД.
- Метрики/алерты по числу сирот.
- Жизненный цикл версий (удаление элементов/версий) — отдельный follow-up.
