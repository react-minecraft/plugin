<div align="center">

<a href="https://git.io/typing-svg"><img src="https://readme-typing-svg.demolab.com?font=Manrope&weight=700&size=38&pause=1000&color=EB2544&background=FFFFFF00&center=true&vCenter=true&width=700&lines=React+AntiCheat;Minecraft+Cheat+Protection" alt="React Typing SVG"/></a>

<p>Защита основанная на AI аналитике поворотов</p>

<p>
  <a href="https://github.com/react-minecraft/plugin/actions/workflows/build.yml">
    <img src="https://img.shields.io/github/actions/workflow/status/react-minecraft/plugin/build.yml?style=plastic&label=build&logo=github&logoColor=white" alt="Build">
  </a>
  <a href="https://jitpack.io/#react-minecraft/plugin">
    <img src="https://img.shields.io/badge/JitPack-39A6A3?style=plastic&logo=jitpack&logoColor=white" alt="JitPack">
  </a>
  <a href="https://github.com/react-minecraft/plugin/blob/master/LICENSE">
    <img src="https://img.shields.io/github/license/react-minecraft/plugin?style=plastic&logo=opensourceinitiative&logoColor=white" alt="License">
  </a>
    <br>
  <a href="https://github.com/react-minecraft/plugin/stargazers">
    <img src="https://img.shields.io/github/stars/react-minecraft/plugin?style=plastic&logo=github&logoColor=white" alt="Stars">
  </a>
  <a href="https://github.com/react-minecraft/plugin/network/members">
    <img src="https://img.shields.io/github/forks/react-minecraft/plugin?style=plastic&logo=git&logoColor=white" alt="Forks">
  </a>
</p>

<p>
  <a href="https://react-ac.space"><img src="https://img.shields.io/badge/Website-EB2544?style=plastic&logo=googlechrome&logoColor=white" alt="Website"/></a>
  <a href="https://t.me/react_ac"><img src="https://img.shields.io/badge/Telegram-26A5E4?style=plastic&logo=telegram&logoColor=white" alt="Telegram"/></a>
  <a href="https://t.me/react_ac_support"><img src="https://img.shields.io/badge/Support-555555?style=plastic&logo=telegram&logoColor=white" alt="Support"/></a>
</p>

</div>

---

> [English version](README.en.md)

## Скачивание плагина

- Последние обновления:
    - **[GitHub Nightly](https://github.com/react-minecraft/plugin/releases/tag/nightly)** *(рекомендуется)*
    - **[SpigotMC RU](https://spigotmc.ru/resources/react-besplatnyj-antichit-s-ii-proverkami.5997/updates)**

---

## Требования & Установка

- Ядро сервера: `Paper / Folia`. Paper поддерживается с `1.16.5`.
- Java: от 17 для JAR Paper и от 21 для JAR Folia. Само ядро может требовать более новую версию.
- Обязательно поставьте [PacketEvents для Bukkit](https://github.com/retrooper/packetevents/releases). PlaceholderAPI нужен только для его плейсхолдеров.

1. Скачайте `react-paper-*.jar` или `react-folia-*.jar` под своё ядро
2. Переместите его в папку `plugins/`
3. Запустите сервер. Плагин сам скачает React RCP в `plugins/React/.rcp/providers/`, когда есть подписанный стабильный релиз под API 2. Для проверки nightly [поставьте RCP вручную](https://github.com/react-minecraft/rcp#что-это).
4. Привяжите сервер к аккаунту на https://www.react-ac.space/: `/react link` или `/react link <код>`

> [!IMPORTANT]
> При автоскачивании React проверяет SHA-256 и подпись Ed25519. Если проверка не прошла или GitHub недоступен, React всё равно может загрузить уже установленный совместимый RCP.

### React RCP

Сторонние библиотеки React хранятся в `plugins/React/.libraries`. При первом запуске React скачивает
общие библиотеки и зависимости выбранной базы данных. Если хранилище выключено, драйверы не скачиваются.
При следующих запусках React проверяет SHA-256 локальных JAR и скачивает только отсутствующие или повреждённые файлы.

Аддоны и RCP объявляют связи через Java `ExtensionBootstrap`. Например, `rcp:react-inference` экспортирует
пакет API, который аддон может подключить через `context.require(...)` или `context.optional(...)`.
Собственные библиотеки расширения хранятся в `.libraries` внутри его папки данных.
Пример вызова сервиса и объявления библиотеки есть в [example-addon](https://github.com/react-minecraft/example-addon#вызов-api-установленного-rcp).

Модели и подключение к React живут не в плагине, а в RCP (Runtime Communication Protocol).
Настройки загрузки лежат в `plugins/React/.rcp/react-rcp.yml`:

| Ключ                   | По умолчанию | Описание                                                                |
|------------------------|--------------|-------------------------------------------------------------------------|
| `settings.enabled`     | `true`       | Загружать ли React RCP                                                  |

Обновления React RCP настраиваются в `plugins/React/update/rcp/React.yml`: `enabled`, `channel`
(`release` или `prerelease`) и `pinned-version`. Общие настройки обновлений лежат в
`plugins/React/update/settings.yml`.

Свои настройки RCP хранит в `plugins/React/.rcp/React/`.

| Команда                | Описание                                                       |
|------------------------|----------------------------------------------------------------|
| `/react rcps`          | Все RCP: версия, `api-version`, канал, время последней проверки |
| `/react reboot <all\|rcp\|addons\|имя>` | Перезагрузить выбранные расширения |
| `/react reboot` | Перезагрузить React там, где это поддерживается. На Folia нужен рестарт сервера |
| `/react link`          | Получить ссылку и привязать сервер к аккаунту React            |
| `/react link <код>`    | Привязать сервер к аккаунту React                               |
| `/react link check`    | Проверить привязку и доступные модели                          |
| `/react alerts [ники]` | Оповещения обо всех игроках или только о указанных             |
| `/react monitor [ники]`| Мониторинг каждого анализа (алиас `/react verbose`)            |

---

## Сборка исходников

Для сборки нужен JDK 21.

1. `git clone https://github.com/react-minecraft/plugin.git`
2. `cd plugin`
3. `./gradlew build`
4. Готовые файлы плагина будут лежать в папках `<ядро>/build/libs`

---

## Как работает анализ?

- Суть плагина в том, что он лишь отправляет запросы на внешний настроенный сайт, вы можете использовать наш
  сайт https://www.react-ac.space/ или же сделать свою модель и использовать ее.
- После получения ответа на запрос анализа, плагин работает с этими данными, для того чтобы мы могли смотреть
  статистику / историю аналитики игроков, так же эти данные сохраняются и доступны в аддонах.
- То что делать с данными уже исключительно ваше решение, можете банить, можете кикать, можете просто проверять вручную,
  но суть плагина - облегчить задачу

---

## Структура

| Модуль   | Описание                                           |
|----------|----------------------------------------------------|
| `api`    | Стабильный API для RCP и аддонов, загрузчики RCP и аддонов |
| `common` | Главная работа античита, данные, проверки и прочее |
| `paper`  | Реализация для Paper серверов                      |
| `folia`  | Реализация для Folia серверов                      |

---

## API для разработчиков

> [!IMPORTANT]
> Если вы хотите добавить в плагин какую либо функцию / механику, то вы можете создать аддон, помощь в их создании
> оказывает https://t.me/g4vrk/
>
> Для React 2.0.0 укажите `api-version: 2` в метаданных расширения.

Примеры с пояснениями:

- **[example-rcp](https://github.com/react-minecraft/example-rcp)**: своя модель, результат в проверке и подкоманда `/react`.
  RCP собирается только против `react-api` и грузится, пока совпадает `api-version`;
- **[example-addon](https://github.com/react-minecraft/example-addon)**: аддон на событиях плагина, собирается против `react-common`.

<details>
<summary><b>Подключение через JitPack</b></summary>

<br>

<div align="center">

<b>Gradle (Kotlin DSL)</b>

</div>

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.react-minecraft.plugin:react-api:VERSION")
    compileOnly("com.github.react-minecraft.plugin:react-common:VERSION")
}
```

<div align="center">

<b>Maven</b>

</div>

```xml

<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.react-minecraft.plugin</groupId>
    <artifactId>react-common</artifactId>
    <version>VERSION</version>
    <scope>provided</scope>
</dependency>
```

</details>

---

## Лицензия

Плагин распространяется под MIT — см. [LICENSE](LICENSE).
