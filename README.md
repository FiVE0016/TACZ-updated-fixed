# Timeless and Classics Guns: Zero — Minecraft 26.3（Fabric · Quilt · NeoForge · Forge）

[Timeless and Classics Guns: Zero](https://github.com/MCModderAnchor/TACZ)（简称 TACZ）的**非官方** 26.3 移植版，
基于同一套代码同时支持四种加载器。它 upstream 自 Forge 1.20.1 的原版 TACZ，以及 Fabric 专用的 26.2 移植
[TaCZ Refabricated](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial)
（后者又源自 [Sh1roCu/TACZ-Refabricated](https://github.com/Sh1roCu/TACZ-Refabricated)）。

本移植与 TACZ、TaCZ Refabricated、LRTactical 的作者**都没有关联，也不受他们支持**。
**请不要把本移植的问题报告给他们。**

---

## 关于这个 26.3 移植

26.3 是一次底层大改：GLFW 换成了 SDL，GPU 抽象搬进了新的 `com.mojang.renderpearl` 库，
全局的"渲染输出重定向"被改成了显式的 render pass，第一人称手部渲染也改成了提取式渲染状态。

本移植跨越了上述全部改动：

- 构建通过
- 四种加载器的客户端与独立服务端**都能干净启动，无 mixin 失败**
- 四种加载器都在世界内实测过开枪、换弹、开镜，**除下方"已知问题"里那条无害的 Hull-fill 回退外无 mod 异常**
- 改动最大的**瞄具画中画（scope picture-in-picture）链路**，已在四种加载器上端到端跑通

完整的 API 对照表与已验证项见 `docs/PORTING_NOTES_26.3.md`。

---

## 环境要求

| 加载器 | 需要 |
|---|---|
| Fabric | Fabric Loader 0.19.5+ 与 Fabric API 0.160.7+26.3 |
| Quilt | Quilt Loader 0.31.0-beta.4+ 与 Fabric API 0.160.7+26.3（QSL 没有 26.x 版本） |
| NeoForge | NeoForge 26.3.0.4-beta+（26.3 目前只有 beta） |
| Forge | Forge 26.3-66.0.2+ |

**所有加载器都需要 Java 25。**

请使用与加载器匹配的 jar；每个 jar 都自带依赖库（LuaJ、Commons Math、Mayday Animation Engine），
并且做了重定位（relocate），不会与其他 mod 冲突。

`gradle.properties` 里的 `forge_enabled` 设为 `false` 时，构建会跳过 `forge` 模块——
在等待 Forge 适配新版本时很有用。

---

## 构建

```bash
./gradlew build
```

产物在 `build/libs/`：`tacz-fabric-<version>.jar`、`tacz-quilt-…`、`tacz-neoforge-…`、`tacz-forge-…`。
（各模块自己 `build/libs` 里的 `-slim` jar **不含**自带依赖库。）

开发运行：`./gradlew :fabric:runClient`（或 `:quilt:`、`:neoforge:`、`:forge:`），
专用服务端用 `runServer`，目录在各模块的 `run-server` 下。
开发服务端可以在 Gradle 终端直接敲控制台命令，四种加载器都行。

开发客户端会额外加载 Cloth Config（Fabric / Quilt 上还有 Mod Menu），方便试配置界面。

`-PtaczQuickPlay=<世界目录>` 已接在每个加载器的 `runClient` 上，用来传 `--quickPlaySingleplayer`。
**它在 26.3 上目前不起作用**：参数确实传进了 JVM（进程命令行已验证），但游戏在开发启动下会忽略它——
即使填一个不存在的世界名也不会报错，说明什么都没传到 `QuickPlay`。
保留这套接线是因为它 API 正确、成本为零，万一以后 Minecraft 或开发启动器又开始认这个标志就能直接用；
在那之前请从主菜单打开世界。

只构建单个加载器可以快很多，例如只出 Fabric：

```bash
./gradlew :fabric:build
```

---

## 项目结构

- `common/` —— 全部玩法、网络、资源与渲染代码。它只针对原生 Minecraft 编译，
  **不允许 import 任何加载器 API**；`./gradlew :common:checkLoaderNeutral` 会强制检查这一点。
- `fabric/`（`quilt/` 模块复用它的源码）、`neoforge/`、`forge/` —— 只有入口点和平台服务。

common 代码通过几个接缝（seam）触达加载器：

| 接缝 | 用途 |
|---|---|
| `com.tacz.guns.platform.Platform`、`NetworkPlatform`、`MenuPlatform` | 加载器服务，用 `ServiceLoader` 发现（各模块里 `META-INF/services`） |
| `com.tacz.guns.network.NetworkHandler#payloads()` | 所有网络包及其编解码、方向、处理器 |
| `com.tacz.guns.init.TaczRegistration` | 逐个注册表注册内容（NeoForge/Forge 走注册表事件；Fabric 一次性全注册） |
| `TaczCommonEvents`、`TaczClientEvents`、`ClientSetupEvent`、`ModEntitiesRender` | 生命周期、tick、玩家、命令与客户端注册钩子 |
| `com.tacz.guns.config.spec.TaczConfigSpec` | 自包含的 TOML 配置（**不需要** Forge Config API Port） |

---

## 配置

- `config/tacz-common.toml`、`config/tacz-client.toml`、`config/tacz-pre.toml`
- `<世界>/serverconfig/tacz-server.toml` —— 每个世界一份；当 `defaultconfigs/tacz-server.toml` 存在时会从它复制，
  客户端进服时会同步过去

游戏内：`/tacz config`。装了 Cloth Config 还有配置界面（Fabric / Quilt 走 Mod Menu，NeoForge / Forge 走 mod 列表）。

---

## 可选联动

TACZ 只在**对方 mod 已安装**时才启用联动。

| Mod | Fabric | Quilt | NeoForge | Forge | 说明 |
|---|:---:|:---:|:---:|:---:|---|
| JEI | ✓ | ✓ | ✓ | ✓ | 配方、配件与弹药查询 |
| REI | — | — | — | — | REI 完全没有 26.3 构建；联动代码保留，按 26.2 API 编译 |
| Cloth Config | ✓ | ✓ | ✓ | — | 配置界面。Forge 上用 `/tacz config` 或配置文件 |
| Mod Menu | ✓ | ✓ | — | — | 配置按钮 |
| Iris | ✓ | ✓ | ✓ | — | 光影兼容 |
| Player Animation Library | ✓ | ✓ | ✓ | — | 第三人称动画 |
| Shoulder Surfing Reloaded | — | — | — | — | 尚无 26.3 构建；代码保留，按 26.2 API 编译 |
| Carry On | ✓ | ✓ | ✓ | ✓ | |
| Zoomify | ✓ | ✓ | — | — | Zoomify 只有 Fabric / Quilt 版 |
| Punchy | ✓ | ✓ | ✓ | ✓ | TACZ 的视图模型会跳过 Punchy 的手部动画 |

表中的"—"表示**对方 mod 还没有 26.3 发布**，不代表支持被砍掉了——TACZ 只在对方存在时才启用，
等对方发布了这些行会重新亮起。

瞄具画中画渲染器在透过镜片重画世界时，还会同步 Sodium、Iris、Voxy 和 Physics Mod。
这些钩子用反射实现，mod 不存在时什么都不做。

因对方 mod 没有 26.x 版本而**无法带过来**的联动：KubeJS、Controllable、Accelerated Rendering、
OptiFine，以及 KosmX 的 playerAnimator（已由 Player Animation Library 取代）。

---

## 内置附加内容

TaCZ Refabricated 自带三个附加内容，本移植在**所有加载器**上同样自带：

- **LRTactical**：投掷物（手雷、闪光弹、烟雾弹、毒气弹、粘性雷、飞溅雷，以及带引爆器的 C4）、
  近战武器与消耗品，全部由枪包定义。依赖 `lrtactical` 的枪包仍可加载。
- **TacZ Mesh Loader**：枪包可以使用高模 `"model_type": "mesh"` 模型。
  依赖 `taczmeshloader` 的枪包仍可加载。设置在 `tacz-client.toml` 的 `[mesh_loader]` 段。
- **瞄具画中画**：瞄具可以按自身倍率把世界透过镜片重画一遍。**默认关闭**，
  用 `tacz-client.toml` 的 `[render]` 段里 `ScopePipEnable` 和 `ScopePipRerender` 打开。

在 Fabric 与 Quilt 上，jar 还像 fork 那样提供 `lrtactical` 和 `taczmeshloader` 两个 mod id，
因此如果同时装了这两个 mod 的独立版，加载器会拒绝启动。
NeoForge 与 Forge 上也别装独立版，因为类会冲突。

---

## 枪包

现代布局的枪包放在 `.minecraft/tacz/`：

- zip 可以直接加载，也可以解压成目录
- 无论哪种形式，**包根目录都必须有 `gunpack.meta.json`**；zip 的话该文件必须在压缩包根部，不能再多套一层目录
- 缺少它时 `GunPackLoader` 不会把它当现代枪包加载；zip 情况会在日志里记 `No gunpack.meta.json found`

**不要只凭"适用于 1.20"这类游戏版本标签判断是否需要转换**——部分 1.20.1 枪包已经是现代布局，
可以直接放进 `tacz/`。请以包结构为准。

只有旧布局的包才放进 `.minecraft/tacz_backup/`，然后在客户端执行转换命令。
转换器会生成带 `gunpack.meta.json` 的输出，但它**不能保证**自动修复所有旧资源、配方或脚本差异，
请保留原包备份并检查游戏日志。

枪包可以在 `gunpack.meta.json` 的 `dependencies` 里声明版本谓词。
本移植用 `1.1.8` 作为 SemVer 核心，`+mc26.3` 是构建元数据，不参与版本先后比较。
一个枪包最终能否通过检查，取决于它写下的完整谓词，不能笼统理解为"所有旧包都兼容"。

**本仓库不提供 Arcana**，也没有实现 Arcana 的 API 或资产保护/加载流程。
明确要求 Arcana 的内容不能视为本 26.x 移植的受支持内容。
紫黑贴图或模型缺失**本身不能证明**这个包一定依赖 Arcana，也可能是目录层级、资源路径、
版本谓词或包本身不完整造成的。提交兼容问题时请给出实际包名与版本、完整日志和最小复现环境，
不要只给缺失贴图截图。

---

## 枪包脚本

枪包可以带 Lua 脚本，TACZ 在受限解释器里运行它们：只加载 base、package、bit32、table、string、math 库，
因此 **没有** `io`、`os`、coroutine 和 `luajava`。

自带的 LuaJ 也去掉了它们背后的类——进程、`io.popen`、`os.execute`、Java 反射与运行时字节码编译器代码——
所以枪包脚本**无法执行系统命令，也无法触达任意 Java 类**。

---

## 已知问题

### 1. 瞄具画中画在光影下会出现「黑镜」（未修复）

**触发条件（三个必须同时满足）：**

1. `tacz-client.toml` 里 `ScopePipEnable = true`（**默认是 false**）
2. 开着光影（Iris + Complementary Unbound 已实测复现）
3. 瞄具倍率高于 `ScopePipMinMagnification`（默认 4.0x）

**现象**：开镜过程中会闪过画面，**完全开满后镜片变黑**（不是纯黑，是偏暗的噪点状）。
关掉光影，或换成低倍镜（如 1.5x），一切正常。

**已确认**：整条链路（目镜掩码 → 渲染目标 → 第二遍渲染 → 合成）**每一步都成功、日志零报错**；
掩码形状经目视确认是正圆且不受光影影响。目前**尚未定位到根因**。

**影响范围**：默认配置下不会遇到（画中画默认关闭）。

### 2. 瞄具画中画的画面观感未校验

画中画能跑通，但**没有人检查过画面本身好不好看**。
它和 mesh GPU 渲染器都是在 26.3 的"显式 render pass"模型上重写的
（26.3 移除了它们原本依赖的 `RenderSystem` 输出纹理重定向）。

打开 `ScopePipEnable` 后，整条链路在四种加载器上、在 4.5x 与 25x 下都能在世界内跑完，
没有 render pass 或着色器错误，也不会自我停用。
**未验证的是画面本身**：构图、对齐，以及透过镜片时的视差。详见 `docs/PORTING_NOTES_26.3.md`。

### 3. Hull-fill 投影 UBO 回读失败（无害）

日志里会出现 `[TACZ Scope] Hull-fill: could not read back the projection UBO`，
后面跟着 `Buffer is not readable` 的堆栈。**这是无害的**——目镜渲染器会回退到逐立方体描摹（per-cube tracing）。

在 26.2 上这只发生在 Forge；在 26.3 上它**不再特定于某个加载器**（Quilt 上也观察到了），
这与 26.3 把 GPU 层搬到 `renderpearl` 及其 Vulkan 后端是一致的——在那上面，
把投影 uniform buffer 映射出来做回读通常不可用。

### 4. 其他

- NeoForge 与 Forge 上，没装 Cloth Config 时 mod 列表里的 TACZ 配置按钮会置灰；
  Forge 上请用 `/tacz config` 或配置文件
- 本移植**不带 LRTactical 的展示资产**，所以除非枪包提供模型与动画，近战武器看起来和挥起来都像原版物品

---

## 许可与署名

**本仓库不只有一套资产许可，不应把整个仓库简单概括为"两套许可"**——详见 `LICENSE` 与 `LICENSES.md`。
代码许可不会自动覆盖美术资源；本仓库兼容某个第三方内容包，也不代表取得、转授或改变了该内容包的许可。

- **TACZ**：程序 286799714、TartaricAcid、F1zeiL、xjqsh、ClumsyAlien；美术 NekoCrane、Receke、Pos_2333。
  代码 GPL-3.0；资产（含默认枪包）CC BY-NC-ND 4.0
- **TaCZ Refabricated**（Fabric 移植）：Sh1roCu，26.x 移植由 q14433686-arch 完成。GPL-3.0
- **LRTactical**（LesRaisins Tactical Equipements）：代码部分 GPL-3.0。
  fork 自带的少量 LRTactical 资源（语言文件、物品定义、状态机脚本、两个效果图标与一个音效）按 fork 原样包含
- **TacZ Mesh Loader**：VellEagle，GPL-3.0。经 TaCZ Refabricated 从 v0.1.7 移植
- 自带库：Mayday Animation Engine（MIT）、LuaJ（Figura fork，MIT）、Apache Commons Math（Apache-2.0）

本移植自身的改动沿用其所修改代码的许可，为 **GPL-3.0**。详见 `LICENSE`。

本项目按"原样"提供，不附带任何担保。
