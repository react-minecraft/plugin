<div align="center">

<a href="https://git.io/typing-svg"><img src="https://readme-typing-svg.demolab.com?font=Manrope&weight=700&size=38&pause=1000&color=EB2544&background=FFFFFF00&center=true&vCenter=true&width=700&lines=React+AntiCheat;Minecraft+Cheat+Protection" alt="React AntiCheat"/></a>

<p>Minecraft anticheat with combat rotation analysis</p>

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

[Русский](README.md)

## Installation

1. Install [PacketEvents for Bukkit](https://github.com/retrooper/packetevents/releases) for your Minecraft version. Install [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) if you want `%react_…%` placeholders.
2. Get the plugin from [GitHub Releases](https://github.com/react-minecraft/plugin/releases). The current prerelease is available under [nightly](https://github.com/react-minecraft/plugin/releases/tag/nightly).
3. Put one JAR in `plugins/`. Use `react-paper-*.jar` on Paper, including Paper 1.16.5, or `react-folia-*.jar` on Folia. Follow your server's Java requirements. The React JAR itself requires Java 17 or later on Paper and Java 21 or later on Folia.
4. Start the server. React installs the official RCP automatically when a signed stable build for API 2 is available. To try nightly, place the [React RCP JAR](https://github.com/react-minecraft/rcp/releases/tag/nightly) in `plugins/React/.rcp/providers/` before starting the server.
5. To use the React service, run `/react link` and confirm the link on the website. If you already have a code, use `/react link <code>`.

React passes rotation data to an RCP, processes the results and stores check history. You can configure check thresholds, alerts and actions on the server, or write an RCP for your own analysis.

## Commands

| Command | Action |
|---|---|
| `/react alerts [players]` | Show flag alerts |
| `/react monitor [players]` | Show individual analysis results |
| `/react rcps` | List loaded RCPs and model status |
| `/react link check` | Check the server link and available models |
| `/react reload` | Reload React and RCP settings |
| `/react reboot <all\|rcp\|addons\|name>` | Restart selected extensions |
| `/react reboot` | Restart the plugin on supported servers. On Folia, restart the server |

## Updates

| Path relative to `plugins/React/` | Purpose |
|---|---|
| `.rcp/react-rcp.yml` | `settings.enabled` enables the official React RCP |
| `.rcp/React/` | Connection and model settings |
| `main-config.yml` | `official-addons.*.download-next-start` installs missing official addons from the stable channel |
| `update/settings.yml` | Check interval, notifications and automatic extension restarts |
| `update/plugin.yml`, `update/rcp/React.yml`, `update/addons/<name>.yml` | `enabled`, `release` or `prerelease` channel, and `pinned-version` for each component |

Automatic downloads are verified with SHA-256 and an Ed25519 signature. Staged plugin updates are applied when the server restarts. RCP and addon updates are applied when their extension restarts.

## Building from source

Use JDK 21.

```sh
git clone https://github.com/react-minecraft/plugin.git
cd plugin
./gradlew build
```

On Windows, run `.\gradlew.bat build`. The JARs are in `paper/build/libs/` and `folia/build/libs/`.

| Module | Purpose |
|---|---|
| `api` | React contracts for addons and RCPs |
| `common` | Checks, storage, commands and extension loading |
| `paper`, `folia` | Platform entry points |

## Developer API

React 2.0.0 uses API 2. Set `api-version: 2` in extension metadata.

- [example-addon](https://github.com/react-minecraft/example-addon) shows how to listen for React events. Install addons in `plugins/React/addons/`.
- [example-rcp](https://github.com/react-minecraft/example-rcp) shows how to submit your own model results and add commands. Install RCPs in `plugins/React/.rcp/providers/`.

Resolve the dependency from JitPack. Replace `VERSION` with a plugin tag or commit. Use `react-api` for the public contracts.

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


## License

[MIT](LICENSE).
