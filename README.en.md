<div align="center">

<a href="https://git.io/typing-svg"><img src="https://readme-typing-svg.demolab.com?font=Manrope&weight=700&size=38&pause=1000&color=EB2544&background=FFFFFF00&center=true&vCenter=true&width=700&lines=React+AntiCheat;Minecraft+Cheat+Protection" alt="React Typing SVG"/></a>

<p>Protection powered by AI-based rotation analysis</p>

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

> [Русская версия](README.md)

## Download

* Latest releases:

    * **[GitHub Nightly](https://github.com/react-minecraft/plugin/releases/tag/nightly)** *(recommended)*
    * **[SpigotMC RU](https://spigotmc.ru/resources/react-besplatnyj-antichit-s-ii-proverkami.5997/updates)**

---

## Requirements & Installation

* Server software: `Paper / Folia`. Paper support starts at `1.16.5`.
* Java: at least 17 for the Paper JAR and 21 for the Folia JAR. Your server may require a newer version.
* Required plugin: [PacketEvents for Bukkit](https://github.com/retrooper/packetevents/releases). PlaceholderAPI is optional.

1. Download `react-paper-*.jar` or `react-folia-*.jar` for your server.
2. Move it to the `plugins/` folder.
3. Start the server. React downloads its RCP into `plugins/React/.rcp/providers/` when a signed stable build for API 2 is available. To try a prerelease, [install the nightly RCP manually](https://github.com/react-minecraft/rcp#что-это).
4. Link the server at https://www.react-ac.space/ with `/react link` or `/react link <code>`.

> [!IMPORTANT]
> The updater checks automatic RCP downloads against SHA-256 and an Ed25519 signature. If verification fails or GitHub is unreachable, React can still load an existing compatible RCP.

### React RCP

The models and the React connection live in an RCP (Runtime Communication Protocol), not in the plugin.
How it is loaded is set in `plugins/React/.rcp/react-rcp.yml`:

| Key                    | Default   | Description                                                                         |
|------------------------|-----------|-------------------------------------------------------------------------------------|
| `settings.enabled`     | `true`    | Whether the React RCP is loaded                                                     |

Configure `enabled`, `channel` (`release` or `prerelease`) and `pinned-version` in `plugins/React/update/rcp/React.yml`. Shared updater settings are in `plugins/React/update/settings.yml`.

The RCP keeps its own settings in `plugins/React/.rcp/React/`.

| Command              | Description                                                        |
|----------------------|--------------------------------------------------------------------|
| `/react rcps`        | Every RCP: version, `api-version`, channel, time of the last check |
| `/react reboot <all\|rcp\|addons\|name>` | Reload selected extensions |
| `/react reboot` | Reload React where supported. Folia requires a server restart |
| `/react link`        | Get a link and confirm the server on the site                      |
| `/react link <code>` | Link the server to your React account                              |
| `/react link check`  | Check the link and the models you have                             |
| `/react alerts [players]`  | Alerts about everyone or only the given players            |
| `/react monitor [players]` | Every analysis as it happens (alias `/react verbose`)      |

---

## Building from Source

Use JDK 21.

1. `git clone https://github.com/react-minecraft/plugin.git`
2. `cd plugin`
3. `./gradlew build`
4. The built plugin files will be available in `<platform>/build/libs`.

---

## How Does the Analysis Work?

* React sends analysis requests to an externally configured server. You can use our service
  at https://www.react-ac.space/ or deploy and use your own model.
* After receiving the analysis response, the plugin processes the returned data so that player statistics and analysis
  history can be tracked. The same data is also stored and made available to addons through the API.
* What you do with the results is entirely up to you. You can ban players, kick them, or simply review the data
  manually. The main purpose of React is to simplify this process.

---

## Project Structure

| Module   | Description                                   |
|----------|-----------------------------------------------|
| `api`    | Stable API for RCPs and addons, RCP and addon loaders |
| `common` | Core anti-cheat logic, data, checks, and more |
| `paper`  | Implementation for Paper servers              |
| `folia`  | Implementation for Folia servers              |

---

## Developer API

> [!IMPORTANT]
> If you want to add your own functionality or mechanics to the plugin, you can create an addon. Help with addon
> development is available at https://t.me/g4vrk/
>
> Set `api-version: 2` in the extension metadata for React 2.0.0.

Examples with explanations:

- **[example-rcp](https://github.com/react-minecraft/example-rcp)**: a model of its own, a result in the check and a `/react`
  subcommand. An RCP builds against `react-api` only and loads while its `api-version` matches;
- **[example-addon](https://github.com/react-minecraft/example-addon)**: an addon on the plugin's events, built against `react-common`.

<details>
<summary><b>Using JitPack</b></summary>

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

## License

The plugin is distributed under the MIT License. See [LICENSE](LICENSE).
