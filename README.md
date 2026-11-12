<div align="center">

<a href="https://git.io/typing-svg"><img src="https://readme-typing-svg.demolab.com?font=Manrope&weight=700&size=38&pause=1000&color=EB2544&background=FFFFFF00&center=true&vCenter=true&width=700&lines=React+AntiCheat;Minecraft+Cheat+Protection" alt="React AntiCheat"/></a>

<p>Античит для Minecraft с анализом поворотов в бою</p>

<p>
  <a href="https://react-ac.space"><img src="https://img.shields.io/badge/Website-EB2544?style=plastic&logo=googlechrome&logoColor=white" alt="Website"/></a>
  <a href="https://t.me/react_ac"><img src="https://img.shields.io/badge/Telegram-26A5E4?style=plastic&logo=telegram&logoColor=white" alt="Telegram"/></a>
  <a href="https://t.me/react_ac_support"><img src="https://img.shields.io/badge/Support-555555?style=plastic&logo=telegram&logoColor=white" alt="Support"/></a>
</p>

<p>
  <a href="https://github.com/react-minecraft/plugin/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/react-minecraft/plugin/build.yml?style=plastic&logo=github&logoColor=white&branch=master&label=build" alt="Build"/></a>
  <a href="https://github.com/react-minecraft/plugin/releases/tag/nightly"><img src="https://img.shields.io/badge/Nightly-EB2544?style=plastic&logo=github&logoColor=white" alt="Nightly"/></a>
  <a href="https://jitpack.io/#react-minecraft/plugin"><img src="https://img.shields.io/badge/JitPack-39A6A3?style=plastic&logo=jitpack&logoColor=white" alt="JitPack"/></a>
  <a href="https://github.com/react-minecraft/plugin/blob/master/LICENSE"><img src="https://img.shields.io/github/license/react-minecraft/plugin?style=plastic&logo=opensourceinitiative&logoColor=white" alt="License"/></a>
</p>

</div>

[English](README.en.md)

## Установка

1. Поставьте [PacketEvents для Bukkit](https://github.com/retrooper/packetevents/releases), совместимый с вашей версией Minecraft. Для плейсхолдеров `%react_…%` можно поставить [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI).
2. Возьмите JAR на странице [GitHub Releases](https://github.com/react-minecraft/plugin/releases). Текущая предварительная сборка лежит в [nightly](https://github.com/react-minecraft/plugin/releases/tag/nightly).
3. Положите в `plugins/` один JAR для вашего ядра. Сборка `react-paper-*.jar` предназначена для Paper, в том числе Paper 1.16.5. Для Folia используйте `react-folia-*.jar`. Java должна удовлетворять требованиям ядра. Сам JAR React требует минимум Java 17 на Paper и Java 21 на Folia.
4. Запустите сервер. Когда доступен подписанный стабильный релиз для API 2, React загрузит официальный RCP автоматически. Для теста nightly можно вручную положить [React RCP](https://github.com/react-minecraft/rcp/releases/tag/nightly) в `plugins/React/.rcp/providers/` перед запуском.
5. Для моделей сервиса React выполните `/react link` и подтвердите привязку на сайте. Если у вас уже есть код, используйте `/react link <код>`.

React передаёт данные о поворотах в RCP, обрабатывает результаты анализа и сохраняет историю. Порог проверок, оповещения и действия настраиваются на сервере. Для своего анализа можно написать RCP.

## Команды

| Команда | Действие |
|---|---|
| `/react alerts [игроки]` | Оповещения о флагах |
| `/react monitor [игроки]` | Мониторинг отдельных результатов анализа |
| `/react rcps` | Загруженные RCP и состояние моделей |
| `/react link check` | Проверка привязки и доступных моделей |
| `/react reload` | Перечитать настройки React и RCP |
| `/react reboot <all\|rcp\|addons\|имя>` | Перезапустить выбранные расширения |
| `/react reboot` | Перезапустить сам плагин там, где это поддерживается. На Folia для этого нужен рестарт сервера |

## Обновления

| Файл относительно `plugins/React/` | Назначение |
|---|---|
| `.rcp/react-rcp.yml` | `settings.enabled` включает официальный React RCP |
| `.rcp/React/` | Настройки подключения и моделей |
| `main-config.yml` | `official-addons.*.download-next-start` включает установку отсутствующих официальных аддонов из стабильного канала |
| `update/settings.yml` | Интервал проверки, уведомления и автоматический перезапуск расширений |
| `update/plugin.yml`, `update/rcp/React.yml`, `update/addons/<имя>.yml` | `enabled`, канал `release` или `prerelease` и `pinned-version` для конкретного компонента |

Автоматическая загрузка проверяет SHA-256 и подпись Ed25519. Подготовленные обновления плагина применяются при рестарте сервера. Обновления RCP и аддонов применяются при перезапуске соответствующего расширения.

## Сборка

Используйте JDK 21.

```sh
git clone https://github.com/react-minecraft/plugin.git
cd plugin
./gradlew build
```

На Windows запускайте `.\gradlew.bat build`. Готовые JAR лежат в `paper/build/libs/` и `folia/build/libs/`.

| Модуль | Назначение |
|---|---|
| `api` | Контракты React для аддонов и RCP |
| `common` | Проверки, хранилище, команды и загрузка расширений |
| `paper`, `folia` | Точки входа для соответствующих серверов |

## API для разработчиков

React 2.0.0 использует API 2. Метаданные расширения должны указывать `api-version: 2`.

- [example-addon](https://github.com/react-minecraft/example-addon) показывает, как слушать события React. Аддоны кладутся в `plugins/React/addons/`.
- [example-rcp](https://github.com/react-minecraft/example-rcp) показывает, как передавать результаты своей модели и добавлять команды. RCP кладутся в `plugins/React/.rcp/providers/`.

Зависимость берётся с JitPack. Замените `VERSION` на тег или коммит плагина. Для публичных контрактов подключайте `react-api`.

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.react-minecraft.plugin:react-api:VERSION")
}
```

<details>
<summary>Maven</summary>

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.react-minecraft.plugin</groupId>
    <artifactId>react-api</artifactId>
    <version>VERSION</version>
    <scope>provided</scope>
</dependency>
```

</details>


## Лицензия

[MIT](LICENSE).
