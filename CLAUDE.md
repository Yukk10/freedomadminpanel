# freedomadminpanel — заметки для работы с проектом

Мультимодульный Gradle-проект (Kotlin DSL, Gradle wrapper, JDK 21): `fabric/` (клиентский мод), `velocity/` (плагин прокси), `paper/` (плагин Paper), `stubs/cmi-api/` (заглушки CMI только для компиляции). Пользователь общается по-русски, тексты в игре тоже на русском.

## Сборка и проверка

- `./gradlew buildAll` — собирает все три jar в `build/release/`. Перед коммитом сборка должна проходить.
- Fabric: Minecraft 1.21.10, Yarn mappings (`net.minecraft.client.MinecraftClient`, `ClientPlayNetworkHandler` и т.п. — не Mojang-имена).
- Velocity и Paper шейдят `org.mariadb.jdbc` в `dev.freedomadminpanel.<модуль>.libs.mariadb`. В коде пиши `org.mariadb.jdbc.Driver` — shadow сам переместит.

## Версии и релизы (важно)

- Версии в `gradle.properties`: `release_version`, `velocity_version`, `paper_version`, `fabric_version`, `protocol_version`.
- Меняя код части, подними **её** версию (semver) и **`release_version`**. Пуш в `main` с новой `release_version` автоматически создаёт GitHub Release `v<release_version>` со всеми тремя jar (`.github/workflows/release.yml`). Без поднятия `release_version` релиз не создастся.
- Не трогай версии частей, которые не менялись.
- Версии не хардкодить в коде: Velocity берёт их из `BuildConstants` (генерируется из `src/main/templates`), Paper — из `plugin.yml` (`${version}`), Fabric — из `fabric.mod.json` (`${version}`).

## Протокол мод ↔ Velocity

- Канал `freedomadminpanel:main`. Клиент: `fabric/.../net/PanelNet.java`; сервер: `velocity/.../Protocol.java` и `FreedomAdminPanelVelocity#handle`.
- Пакеты C→S: 1 = список (версия протокола + requestId), 2 = скины, 3 = рецидив игрока. S→C: 1 = список (чанками по 1500), 2 = скины, 3 = отказ, 4 = рецидив.
- Любое изменение формата делается в обеих частях одновременно. Несовместимое изменение — поднять `protocol_version` (Velocity отклонит старый мод). Добавлять поля лучше в конец пакета: клиент читает хвост пакета 4 через `in.available()`, так старые и новые версии остаются совместимы.
- Поле `float` в пакете 4 (`otherWeight`) сейчас всегда 0 — оставлено для совместимости формата.

## База

- Paper пишет таблицу `freedomadminpanel_jails` (схема в `JailDatabase#createTable`), Velocity её читает (`BanSource#jailCount`). Имена колонок менять синхронно.
- LiteBans читается либо через API на Velocity (`LiteBansBridge`), либо напрямую из MySQL (`LiteBansSql`); плейсхолдеры `{bans}`, `{mutes}`, `{warnings}`, `{kicks}`, `{history}` заменяются на таблицы с префиксом.

## CMI

CMI платный, его API нет в публичных репозиториях. Для компиляции используется `stubs/cmi-api` (`compileOnly`, в jar не попадает). Если нужен новый метод CMI — добавь его в заглушку с точной сигнатурой из настоящего CMI.

## История проекта

Исходники восстановлены декомпиляцией релизных jar (velocity 1.2.3, paper 1.1.0, fabric 1.5.2) и сверены с оригиналами по байткоду; версии сброшены на 1.0.0. Комментариев в коде изначально нет — их не было в jar.
