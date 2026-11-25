<h1 align="center">ModularMCLib Utils</h1>

<p align="center">
  面向 NeoForge 模组的通用工具库。<br>
  A general-purpose utility library for NeoForge mods.
</p>

<p align="center">
  <a href="https://github.com/ModularMCLib/ModularMCLib-Utils/releases"><img src="https://img.shields.io/badge/GitHub-Releases-24292F?logo=github&amp;logoColor=white&amp;style=for-the-badge" alt="GitHub Releases"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=for-the-badge" alt="License"></a>
  <a href="https://discord.gg/HwnF3RTPtH"><img src="https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&amp;logoColor=white&amp;style=for-the-badge" alt="Discord"></a>
</p>

<p align="center">
  <a href="#简体中文">简体中文</a> · <a href="#english">English</a>
</p>

## 分支与版本 / Branches and Versions

| 分支 / Branch | Minecraft | NeoForge | Java | CI | 最新 Maven / Latest Maven | 下载 / Download |
| --- | --- | --- | --- | --- | --- | --- |
| [`26.1`](https://github.com/ModularMCLib/ModularMCLib-Utils/tree/26.1) | `26.1.2` | `26.1.2.126` | `25` | [![CI (26.1)](https://img.shields.io/github/actions/workflow/status/ModularMCLib/ModularMCLib-Utils/auto-build.yml?branch=26.1&style=flat-square)](https://github.com/ModularMCLib/ModularMCLib-Utils/actions/workflows/auto-build.yml?query=branch%3A26.1) | [![Latest Maven version](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmaven.fury.io%2Fmodularmclib%2Fcom%2Fmodularmc%2Fmmclutils%2Fmmclutils-26.1.2%2Fmaven-metadata.xml&style=flat-square)](https://maven.fury.io/modularmclib/com/modularmc/mmclutils/mmclutils-26.1.2/maven-metadata.xml) | [开发快照 / Snapshot](https://github.com/ModularMCLib/ModularMCLib-Utils/releases/tag/latest-26.1) |
| [`1.21`](https://github.com/ModularMCLib/ModularMCLib-Utils/tree/1.21) | `1.21.1` | `21.1.265` | `21` | [![CI (1.21)](https://img.shields.io/github/actions/workflow/status/ModularMCLib/ModularMCLib-Utils/auto-build.yml?branch=1.21&style=flat-square)](https://github.com/ModularMCLib/ModularMCLib-Utils/actions/workflows/auto-build.yml?query=branch%3A1.21) | [![Latest Maven version](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmaven.fury.io%2Fmodularmclib%2Fcom%2Fmodularmc%2Fmmclutils%2Fmmclutils-1.21.1%2Fmaven-metadata.xml&style=flat-square)](https://maven.fury.io/modularmclib/com/modularmc/mmclutils/mmclutils-1.21.1/maven-metadata.xml) | [开发快照 / Snapshot](https://github.com/ModularMCLib/ModularMCLib-Utils/releases/tag/latest-1.21) |

Maven 徽章显示 [ModularMCLib Maven](https://maven.fury.io/modularmclib/) 为对应 Minecraft 版本公开的最新版本。带 `-SNAPSHOT` 的版本是开发快照。

The Maven badges show the latest published versions for each Minecraft version in [ModularMCLib Maven](https://maven.fury.io/modularmclib/). Versions ending in `-SNAPSHOT` are development builds.

## 简体中文

模组标识为 `mmclutils`。

### 安装与构建

将发布的完整 JAR 放入客户端或专用服的 `mods` 目录。

在仓库根目录运行 Gradle Wrapper，产物位于 `build/libs/`。

```powershell
.\gradlew.bat build
```

### 许可

本项目采用 GNU AGPL 3.0。 [许可证](src/main/resources/META-INF/licenses/AGPL-3.0.txt)

## English

The mod ID is `mmclutils`.

### Installation and Build

Place the full JAR for your Minecraft version in the client or dedicated server's `mods` directory.

Run the Gradle Wrapper from the repository root. Build outputs are in `build/libs/`.

```powershell
.\gradlew.bat build
```

### License

This project is licensed under [GNU AGPL 3.0](src/main/resources/META-INF/licenses/AGPL-3.0.txt).
