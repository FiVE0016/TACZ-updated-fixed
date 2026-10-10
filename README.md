# TACZ-updated-fixed

**Timeless and Classics Guns: Zero（TACZ）的 Minecraft 26.3 多加载器修订版。**

这不是官方作品，也不是 26.3 的源搬运版。它是一个社区搬运版的**修订版**：站在别人的肩膀上，
修掉实机游玩中暴露的问题，并把验证结果如实记录下来。

---

## 代码从哪来

```
MCModderAnchor/TACZ（官方，仅 1.20.1）
  └─ Sh1roCu/TACZ-Refabricated（社区 Fabric 移植）
       └─ q14433686-arch/TaCZ_Refabricated_Unofficial（Fabric 26.x 线）
            └─ fmpapierz/TACZ-updated（母本，四加载器，2026-09-20）
                 └─ 本仓库：母本的 26.3 修订版
```

**绝大部分 26.3 移植工作不是本仓库完成的。** 四加载器架构、`common` + 各加载器模块、
renderpearl（26.3 的新 GPU 抽象）迁移、瞄具画中画链路的 API 重写、Sodium/Iris/Voxy 兼容钩子、
以及 `docs/PORTING_NOTES_26.3.md` 里的移植笔记，都来自上游链条。

本仓库做的是**在母本之上继续加工**：用实机测试暴露问题、定位根因、修复并验证。
逐条区分「母本自带 / 本仓库改动」的清单见 [HANDOFF.md](HANDOFF.md) 第 0 节。

**请不要把本仓库称为「26.3 搬运版」，也不要向 TACZ、TaCZ Refabricated 或母本作者报告本仓库的问题。**

---

## 现状

| 项 | 状态 |
|---|---|
| 四种加载器（Fabric / Quilt / NeoForge / Forge）构建 | ✅ 通过，有 CI（`.github/workflows/build.yml`） |
| Fabric 实机验证 | ✅ 由仓库所有者长期实测：开枪、换弹、开镜、光影开/关、变倍、远景 LOD、开镜手感 |
| 光影下的瞄具画中画 | ✅ 已修复并实测（25 倍镜：镜内有画面、准星与目镜框正常） |
| NeoForge / Quilt / Forge 运行 | 编译与 CI 通过；实机运行需要对应环境再次验证 |

**已修复的问题**（都在实机上游玩中暴露）：

1. **光影下镜片全黑** —— 抓帧时机早于 Iris 的最终合成；现已修复（见下方"光影下画中画"）。
2. **光影下准星消失 / 目镜框被盖** —— 合成排在手持渲染之后；现已后置重画。
3. **无光影下变倍镜（如 5x→25x）镜片不跟随** —— 旗标误删回归；已修复。
4. **Distant Horizons 远景不随镜内倍率缩放** —— 新增 DH 投影同步（原仓库只有 Voxy 兼容）。
5. **开镜 / 收镜瞬间视野抽动（高倍镜）** —— FOV 抑制的一帧延迟；已修复。
6. NeoForge 版本钉扎过旧会导致崩溃 —— 已升级并声明版本范围。
7. 自带 LuaJ 保留的危险类（进程 / `io.popen` / `os.execute` / 反射）—— 已移除并有 CI 守卫。

**未修复 / 已知限制**：

- 日志里的 `Hull-fill: could not read back the projection UBO` 无害，会回退到逐立方体描摹。
- Forge 支持是实验性的，默认不构建（`forge_enabled=false`），未实机验证。
- NeoForge / Quilt 的实机运行仍需重新验证。
- 枪包兼容与 Arcana 的限制见下文"枪包"一节。

---

## 光影下的瞄具画中画（Scope PIP）

这是本仓库改动最集中的地方，值得单独说明。

TACZ 的"镜内画中画"默认关闭；开启路径：

```toml
# config/tacz-client.toml
ScopePipEnable = true              # 开启画中画
ScopePipAllowShaderPacks = true    # 光影下也允许（默认 false）
ScopePipRerender = true            # 二次渲染模式（镜内画质最好，代价是开镜时帧率减半）
```

**在光影下，这条链路曾经完全不能工作**（镜片纯黑）。上游作者在配置注释里也写明
"打开试试是安全的，但不能保证观感"。本仓库把它修到了可用的程度，核心是三个根因：

1. **抓帧早于 Iris 最终合成**：立即执行的纹理拷贝总是抢在 Iris 的最终合成之前，
   拿到的是空画面。改为把拷贝记录进同一命令流、排在最终合成之后执行。
2. **镜内那一遍把枪身也画了进去**：Iris 把手部渲染搬进了世界渲染内部，
   镜内画面中央是一块黑（枪身）。现在镜内那一遍跳过手部渲染。
3. **准星/目镜框被合成盖住**：合成发生在手持渲染之后。现在把准星与目镜框
   冻结快照、在合成之后重画回来。

验证环境：Fabric 26.3 + Iris 1.11.7 + Complementary 光影，25 倍镜，光影开/关均正常。

**这些修复不是从零发明的**：上游 1.21.11 分支就曾用同类思路修过同一个问题
（commit `2710c7c`，`IrisFinalScopeOverlayMixin`），26.3 母本里的
`ScopeFinalOverlayState`、掩码桥接、Sodium/Voxy 兼容钩子也都是现成的基础设施。
本仓库做的是把这些机制**接对时机、补上缺口、并在实机上验证**。

---

## 环境要求

| 加载器 | 需要 |
|---|---|
| Fabric | Fabric Loader 0.19.5+ 与 Fabric API 0.160.7+26.3 |
| Quilt | Quilt Loader 0.31.0-beta.4+ 与 Fabric API 0.160.7+26.3 |
| NeoForge | **26.3.0.51-beta 或更新的 26.3 beta**（更旧的会崩溃，jar 已声明版本范围主动拒绝） |
| Forge | Forge 26.3-66.0.2（默认不构建，见"构建"） |

所有加载器都需要 **Java 25**。

---

## 构建

```bash
./gradlew build          # 全部模块
./gradlew :fabric:build  # 只出 Fabric，快很多
```

产物在 `build/libs/`（`fabric/build/libs/` 等模块目录里）。各模块的 `-slim` jar 不含自带依赖库。

开发运行：`./gradlew :fabric:runClient`（`:quilt:` / `:neoforge:` / `:forge:` 同理）。

Forge 包默认不构建：`gradle.properties` 里的 `forge_enabled=false`；打开它并联网构建即可出包。

---

## 项目结构

- `common/` —— 全部玩法、网络、资源与渲染代码，只针对原生 Minecraft 编译，
  不允许 import 加载器 API（`:common:checkLoaderNeutral` 强制检查）。
- `fabric/`、`quilt/`、`neoforge/`、`forge/` —— 入口点与平台服务，经 `ServiceLoader` 接缝触达。

---

## 配置

- `config/tacz-common.toml`、`config/tacz-client.toml`、`config/tacz-pre.toml`
- `<世界>/serverconfig/tacz-server.toml`（每世界一份）

游戏内：`/tacz config`；装了 Cloth Config 时还有图形配置界面。

### 性能提示（镜内画中画）

二次渲染模式（`ScopePipRerender=true`）在**开镜期间**会把世界多渲染一遍，
光影下就是整套光影管线多跑一遍——这就是开镜时帧率下降的来源；不开镜时开销接近零。

- `ScopePipRerenderInterval = 2`：镜内画面每两帧重画一次，开镜开销减半，观感几乎无差。
- `ScopePipResolutionScale`：无光影时可降低镜内渲染分辨率（如 0.5）；
  **光影下被强制为 1.0**（Iris 画进自己的缓冲，缩小省不掉那遍开销）。
- `ScopePipShadowScale = 0.5`：降低镜内那一遍的阴影分辨率。

---

## 可选联动

TACZ 只在对方 mod 已安装时才启用联动：JEI、Cloth Config、Mod Menu、Iris、
Player Animation Library、Carry On、Zoomify、Punchy 等（见各加载器可用性）。

镜内画中画会同步 **Sodium、Iris、Voxy、Distant Horizons 与 Physics Mod** 的投影，
钩子全部走反射，mod 不存在时什么都不做。

---

## 枪包与脚本

- 现代布局枪包放 `.minecraft/tacz/`，包根必须有 `gunpack.meta.json`。
- 旧布局放 `tacz_backup/` 后用客户端命令转换；转换器不保证修复所有差异，请保留备份。
- 本仓库**不提供 Arcana**，也不实现其 API；明确依赖 Arcana 的内容不受支持。
- 枪包 Lua 脚本运行在受限解释器里（无 `io` / `os` / `luajava`），
  自带 LuaJ 也已移除进程、`io.popen`、`os.execute`、反射与字节码编译相关类。

---

## 许可与署名

代码许可不自动覆盖美术资源。主要条目：

- **TACZ**：程序 286799714、TartaricAcid、F1zeiL、xjqsh、ClumsyAlien；
  美术 NekoCrane、Receke、Pos_2333。代码 GPL-3.0；资产（含默认枪包）CC BY-NC-ND 4.0
- **TaCZ Refabricated**：Sh1roCu；26.x 线由 q14433686-arch 移植。GPL-3.0
- **LRTactical**（LesRaisins Tactical Equipements）：代码部分 GPL-3.0
- **TacZ Mesh Loader**：VellEagle，GPL-3.0
- 自带库：Mayday Animation Engine（MIT）、LuaJ（Figura fork，MIT）、Apache Commons Math（Apache-2.0）

本仓库的改动沿用所改代码的许可，为 **GPL-3.0**。详见 `LICENSE`、`LICENSES.md` 与 `HANDOFF.md`。

本项目按"原样"提供，不附带任何担保。
