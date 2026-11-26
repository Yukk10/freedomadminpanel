# freedomadminpanel

Панель модерации для сети серверов Minecraft: игроки онлайн и офлайн, поиск по нику, быстрые команды, выдача и снятие наказаний, рецидив, история тюрьмы, режим слежки.

Проект состоит из трёх частей, которые собираются из одного репозитория:

| Часть | Куда ставить | Что делает |
|---|---|---|
| `freedomadminpanel-fabric-<ver>.jar` | клиент модератора (Fabric 1.21.10 + Fabric API) | GUI панели, список игроков, формы наказаний, слежка |
| `freedomadminpanel-velocity-<ver>.jar` | прокси Velocity | отдаёт моду список игроков, скины, баны/муты/варны из LiteBans, рецидив |
| `freedomadminpanel-paper-<ver>.jar` | сервера Paper с CMI | пишет историю тюрьмы CMI в базу, команда `/jailhistory` |

Готовые jar лежат в [Releases](../../releases) — в каждом релизе всегда все три файла.

## Как это связано

```
 Мод (клиент) ──plugin message "freedomadminpanel:main"──► Velocity ──► LiteBans (API или MySQL)
                                                                 └──► таблица freedomadminpanel_jails ◄── Paper (CMI)
```

Мод общается с Velocity по каналу `freedomadminpanel:main` (бинарный протокол, версия в `gradle.properties` → `protocol_version`).
Paper-плагин записывает каждую посадку/выход из тюрьмы CMI в ту же базу, что у LiteBans; Velocity читает её для подсчёта рецидива.

## Сборка

Нужен JDK 21.

```bash
./gradlew buildAll
```

Все три jar окажутся в `build/release/`. Отдельные части: `./gradlew :fabric:build`, `:velocity:build`, `:paper:build`.

## Версии и релизы

Все версии — в [`gradle.properties`](gradle.properties):

```properties
release_version=1.0.0   # версия релиза (тег v1.0.0)
velocity_version=1.0.0
paper_version=1.0.0
fabric_version=1.0.0
protocol_version=1      # протокол мод <-> Velocity
```

Как выпустить обновление:

1. Подними версию той части, которую менял (например, `fabric_version=1.0.1`).
2. Подними `release_version` (например, `1.0.1`).
3. Запушь в `main` — GitHub Actions соберёт все три jar и создаст релиз `v1.0.1`.
   Части без изменений попадут в релиз со своими прежними версиями, в описании релиза будет видно, что именно обновилось.

Если меняется формат пакетов между модом и Velocity — подними `protocol_version` и обнови **обе** части: Velocity отклоняет мод с другой версией протокола.

## Установка и настройка

### Velocity
Положить jar в `plugins/`. Опционально: LiteBans (баны/муты/рецидив), SkinsRestorer (скины офлайн-игроков).
Конфиг: `plugins/freedomadminpanel/config.json` (создаётся при первом запуске).

- `permission` — право на использование панели (по умолчанию `freedomadminpanel.use`), `allowedPlayers` — ники с доступом без права.
- `liteBansDatabase` — прямое подключение к MySQL LiteBans (если LiteBans стоит не на прокси).
- `serverNames` — как показывать сервера в панели, `jailTable` — таблица истории тюрьмы.
- `recidivism` — окно в днях и формулы сроков (база / шаг / максимум) для мута, тюрьмы и бана.
- `warnAutoBan` — сколько варнов до автобана и на какой срок (для подсказок в моде).

Команда `/fapanel [status|reload|check <ник>]` (алиас `/freedomadminpanel`), право `freedomadminpanel.admin`.

### Paper
Положить jar на сервера, где стоит CMI. Конфиг `plugins/freedomadminpanel/config.yml`: название сервера, подключение к базе (та же, что у LiteBans) и имя таблицы (должно совпадать с `jailTable` на Velocity).

Команда `/jailhistory <ник> [страница]` (алиас `/jhistory`), право `freedomadminpanel.use` или `cmi.command.jail`.

### Мод (Fabric)
Положить jar в `mods/` вместе с Fabric API. Конфиг: `config/freedomadminpanel.json` — шаблоны команд, пресеты времени/причин, количество тюрем, роль.

Клавиши по умолчанию (меняются в настройках управления):

| Действие | Клавиша |
|---|---|
| Открыть панель модерации | `]` |
| Список игроков | не назначена |
| Режим слежки вкл/выкл | не назначена |
| Слежка: следующий / предыдущий игрок | `→` / `←` |
| Слежка: свободная мышь (удерживать) | левый `Alt` |

## Структура

```
fabric/          клиентский мод (Fabric Loom, Yarn mappings)
velocity/        плагин для прокси (MariaDB-драйвер шейдится)
paper/           плагин для Paper (MariaDB-драйвер шейдится)
stubs/cmi-api/   заглушки CMI API только для компиляции (CMI платный и не публикует API)
```

## Лицензия

[MIT](LICENSE)
