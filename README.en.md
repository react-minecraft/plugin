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

* Platforms (React 2.0.0, API 2): `Paper / Spigot` (`1.16.5+`), `Folia` (`1.19.4+`), `Fabric` (`26.2`), `Minestom` (`26.2`), `Sponge` (`API 17`).
* Java: at least 17 for `react-bukkit-*.jar` (Paper/Spigot), and 21+ for `react-folia-*.jar`, `react-sponge-*.jar`, `react-fabric-*.jar`, `react-minestom-*.jar`. Your server may require a newer version.
* React bundles [PacketEvents](https://github.com/retrooper/packetevents) (GPL-3.0) and uses a compatible installed plugin when available. PlaceholderAPI is optional.
* Bukkit and Folia builds report standard metrics to [bStats](https://bstats.org/plugin/bukkit/React%20AntiCheat/35616). Set `enabled: false` in `plugins/bStats/config.yml` to opt out.

1. Download the JAR for your platform: `react-bukkit-*.jar` (Paper/Spigot), `react-folia-*.jar` (Folia), `react-fabric-*.jar` (Fabric), `react-minestom-*.jar` (Minestom), or `react-sponge-*.jar` (Sponge).
2. Place the JAR in your server plugins/mods directory (`plugins/` for Bukkit/Paper/Folia/Sponge, `mods/` for Fabric, or launch via `MinestomRuntime.start` on Minestom). To migrate an existing `react-paper-*.jar` installation, replace the old JAR manually and keep `plugins/React/`.
3. Start the server. React downloads its RCP into `plugins/React/.rcp/providers/` when a signed stable build for API 2 is available. To try a prerelease, [install the nightly RCP manually](https://github.com/react-minecraft/rcp#что-это).

For React 2.0.0 nightly, use RCP and addon builds compiled against API 2. Replace earlier API 1 releases with compatible builds (`hologram-addon` 1.2.1+, `menu-addon` 1.1.1+, `punish-addon` 1.0.3+).
4. Link the server at https://www.react-ac.space/ with `/react link` or `/react link <code>`.

> [!IMPORTANT]
> The updater checks automatic RCP downloads against SHA-256 and an Ed25519 signature. If verification fails or GitHub is unreachable, React can still load an existing compatible RCP.

### Environment settings

React accepts the same dotted names as environment variables and JVM properties. Environment variables override `-D`. Values under `react.config.<file>.<key>` override YAML in memory for the server process.

Example for Windows CMD:

```bat
set "react.language=en"
set "react.config.main.player.combat.time=3s"
set "react.config.rcp-update.channel=prerelease"
set "react.api.url=http://127.0.0.1:18765"
set "react.api.token=<local token>"
```

Language can be `ru` or `en`. Config names are `main`, `actions`, `history`, `punishments`, `database`, `database-driver`, `aim-ai`, `updates`, `plugin-update`, `rcp`, and `rcp-update`. Use keys from the corresponding YAML. Lists and maps can be passed as `[...]` and `{...}`. The RCP process token is sent only to `127.0.0.1` or `[::1]`.

### React RCP

React keeps third-party libraries in `plugins/React/.libraries`. On first startup it downloads the shared
libraries and the dependencies of the selected database. Disabling storage skips the database drivers.
Later starts verify the cached JARs against their SHA-256 digests and download any missing or damaged files.

Addons and RCPs declare dependencies in Java with `ExtensionBootstrap`. For example, `rcp:react-inference`
exports an API package that an addon can link with `context.require(...)` or `context.optional(...)`.
Private libraries are cached in `.libraries` inside the extension's data folder.
See [example-addon](https://github.com/react-minecraft/example-addon#вызов-api-установленного-rcp) for a service and library example.

The models and the React connection live in an RCP (Runtime Communication Protocol), not in the plugin.
How it is loaded is set in `plugins/React/.rcp/react-rcp.yml`:

| Key                    | Default   | Description                                                                         |
|------------------------|-----------|-------------------------------------------------------------------------------------|
| `settings.enabled`     | `true`    | Whether the React RCP is loaded                                                     |

Configure `enabled`, `channel` (`release` or `prerelease`) and `pinned-version` in `plugins/React/update/rcp/React.yml`. Shared updater settings are in `plugins/React/update/settings.yml`.

The RCP keeps its own settings in `plugins/React/.rcp/react-inference-rcp/`.

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
| `api`    | RCP and addon API, including server platform contracts |
| `common` | Checks, data, and extension loading |
| `platform/bukkit` | Common JAR for Paper and Spigot and shared adapter for Folia. Bundles PacketEvents and reuses a compatible installed version |
| `platform/folia`  | Entry point for Folia servers |
| `platform/fabric` | Adapter for Fabric |
| `platform/minestom` | Adapter for Minestom |
| `platform/sponge` | Adapter for Sponge |

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
