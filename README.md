# LoveContracts

Ежедневная система контрактов (в духе Witcher 3): доска заданий, ротация раз в сутки, награды и штрафы.

## Общая структура

Раз в сутки (по умолчанию в 00:00) плагин выбирает пул активных контрактов из `contracts.yml` (взвешенный случайный выбор по редкости: easy/medium/hard), и игроки принимают их через `/contracts`, доску на NPC (Citizens) или обычную табличку. У каждого контракта есть условие выполнения (убить мобов, добыть руду, поймать рыбу, скрафтить предмет), награда и штраф за провал/истечение срока.

Деньги (награды и штрафы) идут через `LoveCore.LoveEconomy` — тот же физический кошелёк, что и во всей экосистеме. Без LoveCore денежные награды/штрафы просто пропускаются, остальное (предметы, опыт, репутация) работает как обычно.

## Команды

| Команда | Алиасы | Описание | Пермишин |
|---|---|---|---|
| `/contracts` | `contract`, `lc` | Открыть доску контрактов | `lovecontracts.use` |
| `/contracts stats` | — | Открыть статистику игрока | `lovecontracts.use` |
| `/contracts abandon <id>` | — | Отказаться от контракта | `lovecontracts.use` |
| `/lovecontracts` | `lca`, `lcadmin` | Администраторские команды | `lovecontracts.admin` |

### Подкоманды `/lovecontracts`

- `reload` — перезагрузить config.yml и contracts.yml
- `rotate` — принудительно запустить ротацию контрактов
- `npc` — привязать Citizens NPC, на которого смотрит игрок (правый клик открывает доску)
- `sign` — подсказка по созданию таблички-контракта (без привязки — любая табличка с нужными строками работает)
- `complete <player> <contractId>` — принудительно засчитать выполнение
- `fail <player> <contractId>` — принудительно засчитать провал
- `stats <player>` — открыть статистику другого игрока
- `reset <player>` — сбросить дневные счётчики игрока
- `diag` — диагностика (загруженные контракты, активные сейчас, статус LoveCore/PlaceholderAPI/Citizens)

## Пермишины

| Пермишин | Описание | Default |
|---|---|---|
| `lovecontracts.use` | Доступ к `/contracts` | true |
| `lovecontracts.admin` | Доступ ко всем `/lovecontracts` командам | op |
| `lovecontracts.npc` | Привязка NPC доски контрактов | op |
| `lovecontracts.sign` | Создание табличек-контрактов | op |

## Контракты (`contracts.yml`)

Каждый контракт — запись с условием, наградами и штрафами:

```yaml
contracts:
  kill_zombies_easy:
    display-name: "<green>Kill 50 Zombies</green>"
    description: "Slay 50 zombies anywhere on the server"
    difficulty: "EASY"          # EASY | MEDIUM | HARD
    type: "REPEATING"           # REPEATING | ONE_TIME
    enabled: true
    weight: 10                  # вес при взвешенном случайном выборе в ротации
    daily-spawns: 1             # сколько "слотов" этого контракта создаётся за ротацию
    max-acceptances: -1         # максимум принятий за ротацию; -1 = без ограничения
    expiration-hours: 24
    condition:
      type: "KillEntity"        # KillEntity | MineMaterial | CatchFish | CraftItem
      entity-type: "ZOMBIE"
      count: 50
    rewards:
      money: 500                # монеты через LoveCore.LoveEconomy
      items:
        - "DIAMOND 1"
        - "OAK_LOG 16"
    penalties:
      none: true                # или money: -100 / отсутствие блока = нет штрафа
```

Условия (`condition.type`):
- `KillEntity` — убить `entity-type` (Bukkit `EntityType`) × `count`
- `MineMaterial` — добыть блок `material` (учитывает deepslate-варианты руд) × `count`
- `CatchFish` — поймать рыбу × `count`
- `CraftItem` — скрафтить предмет `material` × `count`

## Конфигурация (`config.yml`)

### Ротация

```yaml
rotation:
  enabled: true
  time: "00:00"              # время ежедневной ротации (серверное)
  daily-count: 20             # сколько контрактов активно одновременно

  difficulty-distribution:
    easy: 60                  # % от daily-count
    medium: 30
    hard: 10

  notify-rotation: true
  rotation-announcement: "<green>✓ New contracts are available! Use /contracts</green>"

  warning-before-expiration-minutes: 5
  expiration-warning: "<yellow>⚠ Your contracts expire in {TIME} minutes!</yellow>"
```

### Награды и штрафы

```yaml
rewards:
  enabled: true
  # Денежные награды платятся через LoveCore.LoveEconomy (физические монеты в инвентаре) —
  # общая валюта экосистемы. Молча пропускаются, если LoveCore не установлен.
  money:
    enabled: true
  items:
    enabled: true
  reputation:
    enabled: false
    integration: "lovebehavior"   # точка интеграции, ещё не подключена

penalties:
  enabled: true
  # Денежные штрафы списываются через LoveCore.LoveEconomy, но не больше текущего баланса
  # игрока — у физической валюты нет долга.
  money:
    enabled: true
  reputation:
    enabled: false
  track-failures: true
```

### GUI

```yaml
gui:
  enabled: true
  title: "<gradient:#55FF55:#55FFFF>Contract Board</gradient>"
  auto-update: true
  update-interval-ms: 500

  difficulty-colors:
    easy: "green"
    medium: "yellow"
    hard: "red"
```

54-слотовая доска (`/contracts`): вкладки All/Accepted/Completed/Failed/Statistics, до 21 контракта на экране одновременно. Кнопка обновления и статистика игрока — отдельное меню (`/contracts stats`).

### NPC (Citizens)

```yaml
npc:
  enabled: true
  info-update-interval: 10
  # Привязывается через /lovecontracts npc (посмотреть на NPC перед командой).
  # -1 = NPC не привязан, доска всё равно доступна через /contracts и таблички.
  id: -1
```

### Таблички

```yaml
signs:
  enabled: true
```

Любая табличка с первой строкой `[LoveContracts]` и второй строкой = id контракта из `contracts.yml` работает на приём контракта по правому клику — привязка не требуется, проверка идёт по содержимому таблички в момент клика.

### База данных

```yaml
database:
  file: "contracts.db"   # только SQLite
  pool-size: 10
  connection-timeout: 30000
  leak-detection-threshold: 15000
```

Таблицы: `active_contracts` (текущая ротация, счётчик принятий на слот), `player_contracts` (кто что принял/выполнил/провалил, с уникальностью на игрока+контракт+день), `contract_stats` (дневные/суммарные счётчики, серии побед).

## PlaceholderAPI

Идентификатор: `%lovecontracts_<param>%`

| Плейсхолдер | Описание |
|---|---|
| `%lovecontracts_daily_completed%` | Выполнено сегодня |
| `%lovecontracts_daily_failed%` | Провалено сегодня |
| `%lovecontracts_daily_accepted%` | Принято сегодня |
| `%lovecontracts_total_completed%` | Всего выполнено |
| `%lovecontracts_total_failed%` | Всего провалено |
| `%lovecontracts_total_accepted%` | Всего принято |
| `%lovecontracts_current_streak%` | Текущая серия побед |
| `%lovecontracts_best_streak%` | Лучшая серия побед |
| `%lovecontracts_success_rate%` | Процент успеха |
| `%lovecontracts_active_contracts%` | Контрактов активно сейчас |

## Зависимости

### Мягкие зависимости (плагин работает и без них)

- **LoveCore** — денежные награды/штрафы через `LoveEconomy`; без него эти типы наград/штрафов молча пропускаются
- **Citizens** — NPC доски контрактов (`/lovecontracts npc`)
- **PlaceholderAPI** — плейсхолдеры статистики

## Установка и сборка

```bash
mvn package
```

Java 21, Paper 1.21.4+. Использует HikariCP + SQLite (шейдится в jar), Caffeine для кэша (зарезервировано под будущее использование).

## Ограничения

- Денежные штрафы применяются только к онлайн-игрокам — списать физические монеты из инвентаря офлайн-игрока невозможно, поэтому провал контракта по истечении срока для офлайн-игрока не штрафуется деньгами (статистика провала всё равно фиксируется).
- Репутация (LoveBehavior) — точка интеграции обозначена в конфиге и модели наград/штрафов, но общий сервис ещё не подключён.
