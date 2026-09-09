<p align="right">
  <b>中文</b> | <a href="./README.md">English</a>
</p>

# 糯米播放器 · Android Auto 音乐伴侣

**让手机音乐，与车机同行。**

糯米播放器把手机音乐 App 的歌曲信息和播放控制同步到 Android Auto，让你继续使用熟悉的播放器和歌单，在车机查看歌名、歌手、封面、进度，以及支持来源的实时歌词。音乐仍由原来的音乐 App 播放。

> **糯米播放器 3.0 已发布！** [点击下载 3.0 安装包](https://github.com/charlottejas/NuomiPlayer/releases/download/v3.0/nuomi-player-3.0-release.apk) · [查看更新说明](https://github.com/charlottejas/NuomiPlayer/releases/tag/v3.0)。需要 **Android 13 或以上版本**，并搭配支持 Android Auto 的使用环境。

[下载糯米播放器 3.0](https://github.com/charlottejas/NuomiPlayer/releases/download/v3.0/nuomi-player-3.0-release.apk) · [版本与下载](#版本与下载) · [使用指南](#使用指南) · [问题反馈](https://github.com/charlottejas/NuomiPlayer/issues)

## 3.0 界面展示

<table>
  <tr>
    <td><a href="screenshot/v3-preview/android-auto-lyrics.png"><img src="screenshot/v3-preview/android-auto-lyrics.png" width="480" alt="Android Auto 也有实时歌词了：完整车机播放画面" /></a></td>
    <td><a href="screenshot/v3-preview/lyrics-modes.png"><img src="screenshot/v3-preview/lyrics-modes.png" width="480" alt="歌词模式，随心切换：双行歌词与歌名加歌词" /></a></td>
  </tr>
  <tr>
    <td><a href="screenshot/v3-preview/ui-refresh.png"><img src="screenshot/v3-preview/ui-refresh.png" width="480" alt="界面焕新，清爽好用：播放首页和车机设置" /></a></td>
    <td><a href="screenshot/v3-preview/built-in-guide.png"><img src="screenshot/v3-preview/built-in-guide.png" width="480" alt="内置教程，轻松上手：新手指南和连接帮助" /></a></td>
  </tr>
</table>

## 3.0 升级内容

- **实时歌词，新增汽水音乐适配。** 在 QQ 音乐、网易云音乐的基础上加入汽水音乐歌词，随歌曲播放进度显示。是否有歌词取决于具体歌曲、来源与网络。
- **歌词模式，随心切换。** 可选择「双行歌词」或「歌名 + 歌词」；手机端提供显示示意，点击确定后保存。支持连接后默认显示歌词，也可在车机手动开关歌词。
- **界面焕新，清爽好用。** 重新设计手机播放首页、音乐来源选择和车机设置，突出专辑封面，整理连接状态、当前播放器、权限和偏好入口。
- **音乐来源与常用播放器。** 手机端扫描、选择当前活跃的音乐来源；车机端通过「我的偏好」「当前可用」查找播放器。支持收藏常用来源，以及从车机发起打开音乐 App。
- **内置教程，轻松上手。** 五步引导涵盖使用流程、Android Auto 配置、权限、歌词和功能设置；之后可随时重新进入指南。
- **连接帮助，按问题排查。** 针对找不到音乐来源、歌曲信息不更新、车机里找不到糯米、歌词不显示，提供检查步骤和相关设置入口。
- **连接与切歌体验改进。** 优化普通后台回收后的连接恢复、播放器会话切换、播放状态同步、封面闪烁与信息错配；改进 QQ 歌词延迟和歌曲信息漏更新的处理。
- **状态提示更清楚。** 区分歌词加载中、无歌词、视频和加载失败；完善播放器名称、不可用来源、打开失败与权限状态提示。

## 音乐 App 支持范围

糯米通过 Android 系统媒体会话读取歌曲信息、转发控制，可接入多数提供这些能力的音乐 App。**通用播放支持不等于通用歌词支持。** 下表描述 3.0 的支持范围。

| 音乐来源 | 歌曲信息与基本播放控制 | 实时歌词 | 随机 / 循环模式切换 |
| --- | --- | --- | --- |
| QQ 音乐 | 支持 | 支持 | 支持 |
| 网易云音乐 | 支持 | 支持 | 支持 |
| 汽水音乐 | 支持 | 支持 | 当前不提供 |
| 其他音乐 App | 取决于 App 提供的系统媒体能力 | 暂不支持 | 暂不承诺支持 |

基本控制包括播放、暂停、上一首、下一首；歌曲信息包括歌名、歌手、封面和进度。进度拖动取决于来源与车机支持，歌词模式下的拖动兼容问题仍在跟进。

账号、会员、歌曲可用性和实际音频播放均由原音乐 App 决定。音乐 App 或系统更新可能影响兼容性。

## 使用指南

在 Android 13 或以上版本的手机上安装 [3.0 安装包](https://github.com/charlottejas/NuomiPlayer/releases/download/v3.0/nuomi-player-3.0-release.apk)，然后按以下步骤设置。

1. **配置 Android Auto。** 开启开发者模式，在开发者设置中勾选「未知来源」。如自定义启动器中出现糯米播放器，请勾选后重新连接车机。可参考 [Android 官方测试文档](https://developer.android.com/training/cars/testing)。
2. **开启通知读取权限。** 糯米需要通过该权限连接音乐 App 的媒体会话。3.0 会说明权限用途，并提供系统设置入口；「启动通知」用于从车机发起打开音乐 App 的流程。
3. **先在音乐 App 播放一首歌。** 然后返回糯米，点击「切换」并扫描，选择正在播放的音乐来源。
4. **调整车机偏好。** 选择歌词显示模式、是否默认显示歌词，以及是否把当前来源加入「我的偏好」。
5. **连接车机使用。** 打开 Android Auto 中的糯米播放器；遇到问题时，可回到手机端的「使用指南」或「连接帮助」。

### 常见问题

| 问题 | 建议检查 |
| --- | --- |
| 找不到音乐来源 | 检查通知读取权限；先在原音乐 App 播放，再重新扫描。列表显示已发现的活跃播放器，不是全部已安装 App。 |
| 歌曲信息不更新 | 确认原 App 正常播放，重新选择当前音乐来源，同时检查系统后台限制。 |
| 车机里找不到糯米 | 检查 Android Auto 的未知来源和自定义启动器设置，再重新连接。 |
| 歌词不显示 | 检查来源与歌曲是否支持歌词、车机是否已开启歌词；联网歌词还受网络和上游服务影响。 |
| 后台恢复失败 | 系统后台策略可能影响恢复；用户主动「强行停止」应用不属于普通后台回收，需要重新打开应用。 |
| APK 安装受限 | 请参考手机品牌官方安装说明；熟悉 Android 开发的用户也可以通过仓库源码自行构建。 |

## 版本与下载

| 版本 | 状态与下载 |
| --- | --- |
| **3.0** | **最新版本：**[下载安装包](https://github.com/charlottejas/NuomiPlayer/releases/download/v3.0/nuomi-player-3.0-release.apk) · [更新说明](https://github.com/charlottejas/NuomiPlayer/releases/tag/v3.0) |
| 2.0 | 历史版本：[下载糯米播放器 2.0](https://github.com/charlottejas/NuomiPlayer/raw/main/糯米播放器2.0.apk) |

安装包通过 Release 附件提供；仓库源码尚未同步到此 3.0 构建，GitHub 自动生成的源码压缩包不是该安装包的对应源码。

<details>
<summary>历史版本与更新记录</summary>

- [1.4.1](https://github.com/charlottejas/NuomiPlayer/raw/main/糯米播放器1.4.1.apk)：减少部分权限需求。
- [1.4.0](https://github.com/charlottejas/NuomiPlayer/raw/main/糯米播放器1.4.0.apk)：从特定平台适配升级为基于系统媒体会话的通用方案，改进异常处理。
- [1.3.1](https://github.com/charlottejas/NuomiPlayer/raw/main/糯米播放器1.3.1.apk)：修复打开网易云音乐的问题。
- [1.3.0](https://github.com/charlottejas/NuomiPlayer/raw/main/糯米播放器1.3.0.apk)：增加网易云音乐适配。
- [1.2.0](https://github.com/charlottejas/NuomiPlayer/raw/main/糯米播放器1.2.0.apk)：增加播放模式切换与默认开启歌词选项。
- [1.1.0](https://github.com/charlottejas/NuomiPlayer/raw/main/糯米播放器1.1.0.apk)：增加实时歌词。
- [1.0.0](https://github.com/charlottejas/NuomiPlayer/raw/main/糯米播放器%201.0.0.apk)：支持 QQ 音乐、Android Auto 与基本播放控制。

</details>

## 项目初衷

买车后，我发现自己常用的 QQ 音乐没法直接用上 Android Auto，而换成其他播放器又缺少我常听的歌曲。尝试过一些方案后，我决定自己动手，从解决自己的车载听歌需求开始做糯米播放器。

项目由个人维护，欢迎使用、反馈和参与改进。如果糯米帮到了你，也欢迎给项目点一个 ⭐。

## 音乐、歌词与权限说明

[隐私权政策 / Privacy Policy](https://charlottejas.github.io/NuomiPlayer/privacy/)

- 本项目用于个人学习与研究，不内置曲库，不提供音乐下载或会员解锁功能；实际音乐播放由原音乐 App 完成。
- 歌曲信息与播放控制通过 Android 系统媒体会话等机制获取和转发，需要相应的通知读取权限。
- 3.0 的 QQ 歌词来自播放器提供的媒体信息；网易云音乐和汽水音乐歌词按当前歌曲标识联网获取。
- 歌词与封面等内容的相关权利属于各自权利人；上游 App 和服务的可用性、规则变化可能影响功能。

## 反馈与致谢

请通过 [GitHub Issues](https://github.com/charlottejas/NuomiPlayer/issues) 反馈问题，并说明糯米版本、手机型号、Android 版本、音乐 App 及版本、Android Auto/车机环境和复现步骤。

感谢 [Booming Music](https://github.com/mardous/BoomingMusic)：早期手机端界面的部分代码基于该项目。第三方代码保留各自的许可要求；仓库许可信息见 [LICENSE](LICENSE)。
