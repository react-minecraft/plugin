<div align="center">

<a href="https://git.io/typing-svg"><img src="https://readme-typing-svg.demolab.com?font=Manrope&weight=700&size=38&pause=1000&color=EB2544&background=FFFFFF00&center=true&vCenter=true&width=700&lines=React+AntiCheat;Minecraft+Cheat+Protection" alt="React Typing SVG"/></a>

<p>Protection powered by AI-based rotation analysis</p>

<p>
  <a href="https://github.com/g4vrk/React/actions/workflows/gradle-publish.yml">
    <img src="https://img.shields.io/github/actions/workflow/status/g4vrk/React/build.yml?style=flat&label=build&logo=github&logoColor=white" alt="Build">
  </a>
  <a href="https://jitpack.io/#g4vrk/React">
    <img src="https://img.shields.io/jitpack/version/com.github.g4vrk/React?style=flat&logo=jitpack&logoColor=white" alt="JitPack">
  </a>
  <a href="https://github.com/g4vrk/React/blob/master/LICENSE">
    <img src="https://img.shields.io/github/license/g4vrk/React?style=flat&logo=opensourceinitiative&logoColor=white" alt="License">
  </a>
  <br>
  <a href="https://github.com/g4vrk/React/stargazers">
    <img src="https://img.shields.io/github/stars/g4vrk/React?style=flat&logo=githubsponsors&logoColor=white" alt="Stars">
  </a>
  <a href="https://github.com/g4vrk/React/network/members">
    <img src="https://img.shields.io/github/forks/g4vrk/React?style=flat&logo=git&logoColor=white" alt="Forks">
  </a>
</p>

<p>
  <a href="https://react-ac.space">Website</a>
  &nbsp;•&nbsp;
  <a href="https://t.me/react_ac">Channel</a>
  &nbsp;•&nbsp;
  <a href="https://t.me/react_ac_support">Support</a>
</p>

</div>

---

> [Русская версия](README.md)

## Download

* Latest releases:

    * **[GitHub Tags](https://github.com/g4vrk/react/tags)** *(recommended)*
    * **[SpigotMC RU](https://spigotmc.ru/resources/react-besplatnyj-antichit-s-ii-proverkami.5997/updates)**

---

## Requirements & Installation

* Server versions: `1.16.5 - 26.2`
* Server software: `Paper / Folia`

1. Download the plugin `.jar` file.
2. Move it to the `plugins/` folder.
3. Start the server. After the first tick the plugin downloads the React RCP from
   [GitHub](https://github.com/react-minecraft/rcp) into `plugins/React/.rcp/providers/`.
4. Link the server to your account at https://www.react-ac.space/: `/react link <code>`.

> [!IMPORTANT]
> Every RCP build is checked against SHA-256 and an ed25519 signature. A build that fails the check is deleted and
> the server keeps running without the RCP. When GitHub is unreachable, the last downloaded build is loaded.

### React RCP

The models and the React connection live in an RCP (Runtime Communication Protocol), not in the plugin.
How it is loaded is set in `plugins/React/.rcp/react-rcp.yml`:

| Key                    | Default   | Description                                                                         |
|------------------------|-----------|-------------------------------------------------------------------------------------|
| `settings.enabled`     | `true`    | Whether the React RCP is loaded                                                     |
| `settings.auto-update` | `true`    | Check for a new version on start and `/react reboot`; `false` downloads it only once |
| `settings.channel`     | `release` | `release` for releases only, `all` for every CI build (`nightly`)                   |

The RCP keeps its own settings in `plugins/React/.rcp/React/`.

| Command              | Description                                                        |
|----------------------|--------------------------------------------------------------------|
| `/react rcps`        | Every RCP: version, `api-version`, channel, time of the last check |
| `/react reboot`      | Update and reload the plugin together with the RCP, no restart     |
| `/react link`        | Get a link and confirm the server on the site                      |
| `/react link <code>` | Link the server to your React account                              |
| `/react link check`  | Check the link and the models you have                             |
| `/react alerts [players]`  | Alerts about everyone or only the given players            |
| `/react monitor [players]` | Every analysis as it happens (alias `/react verbose`)      |

---

## Building from Source

1. `git clone https://github.com/g4vrk/React.git`
2. `cd React`
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
