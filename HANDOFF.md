# 交接手册（HANDOFF）

> **这份文件是给"下一个接手的人"看的。接手的人很可能是另一个 AI。**
> 如果你是一个 AI/编程助手，请**先完整读完这份文件，再动任何代码**。
> 特别是第 6 节「不许做的事」——违反其中任何一条，都可能让这份仓库变成没人能接的烂摊子。

---

## 0. 署名与来源：本仓库**不是**源搬运版 ★必读

> **这一节是为避免误解而写的硬性声明。任何引用本仓库的地方都不得省略。**
> 如果你是 AI，在生成介绍、发布说明、整合包说明时，**必须保留本节的意思**。

### 一句话

**本仓库的作者（GitHub：`FiVE0016`）不是 TACZ 26.3 的搬运者/移植者。**
26.3 的移植工作是社区里另外两位作者做的，本仓库是把他们的成果**拿过来继续加工**的产物。

### 正确的称呼方式

| 说法 | 对不对 |
|---|---|
| "本仓库是 TACZ 26.3 非官方搬运版的**再加工 / 多加载器衍生（fork）**" | ✅ 对 |
| "本仓库是 TACZ 的 26.3 搬运版" | ❌ 错，会让人以为移植工作是本仓库做的 |
| "本仓库作者移植了 TACZ 到 26.3" | ❌ 错 |

### 完整的上游链条

```
MCModderAnchor/TACZ                       官方 TACZ（只做到 1.20.1，与本仓库无关）
        ↓ 社区移植
Sh1roCu/TACZ-Refabricated                 Fabric 移植。GPL-3.0
        ↓ 接续 26.x
q14433686-arch/TaCZ_Refabricated_Unofficial   Fabric 26.1.2 / 26.2 / 26.3 线。GPL-3.0
q14433686-arch/TaCZ_Renovated                 NeoForge 26.x 线（同一位作者的另一条线）
        ↓ 另有一条"26.2 多加载器"工程（去向不明，见下）
【26.2 多加载器版（母本工程）】               ★ 这一层的确切出处待补，见下
        ↓ 本仓库基于它升级到 26.3
FiVE0016/TACZ-updated-fixed                ← 本仓库
```

**另外三份被搬进来的第三方代码**（合计约 17,700 行，占核心代码的 **21.5%**）：

| 代码位置 | 来源 | 许可 |
|---|---|---|
| `common/src/main/java/cn/sh1rocu/tacz/`（约 121 个文件） | Sh1roCu 的 TACZ-Refabricated | GPL-3.0 |
| `common/src/main/java/cn/sh1rocu/simplebedrockmodel/` | 同上 | GPL-3.0 |
| `common/src/main/java/me/xjqsh/lrtactical/`（约 114 个文件） | LesRaisins 战术装备（LRTactical） | GPL-3.0 |
| `common/src/main/java/cn/sh1rocu/tacz/compat/meshloader/`（约 20 个文件） | VellEagle/TacZMeshLoader | GPL-3.0 |
| 默认枪包的模型/贴图/音效 | 官方 TACZ | **CC BY-NC-ND 4.0（非商业、禁止演绎）** |

### 证据（如果将来有人质疑，用这些回答）

- 借来的代码保留了原始的 Java 包名 `cn.sh1rocu.*` 和 `me.xjqsh.*`——这是**别人**的命名空间
- 约 20 个 mesh loader 源文件头部写着"移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)"
- `docs/PORTING_NOTES_26.3.md` 第 3 行自述："Written while moving this project from the 26.2 tree"
  —— 说明本仓库是**从一份 26.2 版本升级上来的**，不是从零移植
- 该笔记里多处提到"四个加载器"（`all four loaders`），说明母本工程本身就已经是多加载器工程
- `README.md` 与四个模组描述文件的 `contributors` 里已列出 Sh1roCu 与 q14433686-arch

### 本仓库作者真正做的事（这才是本仓库的价值）

1. **多加载器改造**：把母本整理成 `common` + 四个加载器胶水层的结构，一份代码同时出四个 jar
2. **加载器隔离**：一个自动检查（`checkLoaderNeutral`）强制 `common` 里不许出现任何加载器专有代码
3. **打包去冲突**：把内置库改名到 `com.tacz.guns.shadow`，避免和其他模组撞车
4. **26.3 适配**：处理 26.3 的底层大改（窗口输入从 GLFW 换成 SDL、渲染改成"显式 render pass"）
5. **安全加固**：从成品里删掉枪包脚本能执行系统命令的能力
6. **文档与实测**：`docs/PORTING_NOTES_26.3.md`、`README.md`、以及开发环境实测记录

### 已知不明之处（如实记录，不要掩盖）

- **那份"26.2 多加载器"母本工程的确切仓库地址未能确认**：作者（仓库主人）本人也记不清当初下载的是哪一位作者的版本。
  可能的出处是 `q14433686-arch` 名下某个已删除/改名/转私有的仓库。
  如果你找到了它，请补进上面那张链条图。
- 代码里能同时看到 Fabric 与 NeoForge 两套来源的痕迹（例如 REI 插件位于 `cn.sh1rocu.tacz.compat.rei`，
  而 NeoForge 专属的 REI 插件位于 `com.tacz.guns.neoforge.compat.rei`），说明母本工程是对两条线做过合流的。
- **如果发现本节的描述与事实不符，先改本节，再改代码。**

---

## 1. 这个项目是什么（大白话）

Minecraft（我的世界）有一个很受欢迎的枪械模组，叫 **TACZ（永恒枪械工坊：零）**。
官方只做到老版本（1.20.1），**没有**为 26.3 这种新版本做。

于是社区里有人做了"移植版"——就是把老模组的代码搬到新版本上让它能跑。
目前有三条这样的线（都跟官方无关）：

| 谁做的 | 支持什么 | 说明 |
|---|---|---|
| `Sh1roCu/TACZ-Refabricated` | 只支持 Fabric | 最早的 Fabric 移植，目前主线还停在 1.20.1 |
| `q14433686-arch/TaCZ_Refabricated_Unofficial` | 只支持 Fabric | 上面那个的"新版本分支"，作者的另一条线 |
| `q14433686-arch/TaCZ_Renovated` | 只支持 NeoForge | 同一个作者，NeoForge 版 |
| **本仓库**（`FiVE0016/TACZ-updated-fixed`） | **Fabric / Quilt / NeoForge / Forge 四个都能跑** | 就是你现在看的这个 |

**本仓库存在的唯一理由**：上面那两位作者都是"一个加载器一个仓库"，
而本仓库是**一份代码同时喂四个加载器**。所以本仓库最值钱的东西不是"移植"，
而是**四加载器架构 + 一份能跑的 26.3 移植**。

### 名词解释（不懂技术也能看懂）

| 词 | 意思 |
|---|---|
| **加载器（Loader）** | 玩家装模组用的工具。Fabric / Quilt / NeoForge / Forge 是四种不同的工具，互不通用。 |
| **Minecraft 26.3** | 游戏版本号。现在叫 26.3，以后会有 26.4、26.5…… |
| **编译 / 构建（build）** | 把代码变成玩家能用的 `.jar` 文件的过程。 |
| **jar 文件** | 就是最终给玩家的成品文件，丢进游戏 `mods` 文件夹就能用。 |
| **common** | 四个加载器**共用**的那份游戏代码（本项目最核心的部分）。 |
| **vendored 代码** | 从别人仓库搬过来的代码（本项目有三份，见第 4 节），不是原创。 |

---

## 2. 现在是什么状态（诚实版，不要美化）

**已经确认能用的（有人实测过）：**

- 四个加载器里的 **Fabric、Quilt、NeoForge** 能编译出成品包
- 这三个的**客户端能启动、能进世界、枪械系统正常工作**（开枪、换弹、开镜、枪包加载）
- `./gradlew build` 跑通，产出包在 `build/libs/`

**没确认 / 有问题的：**

| 问题 | 严重程度 | 细节 |
|---|---|---|
| **NeoForge 版本钉得太旧** | 🔴 会让玩家进不去游戏 | 见任务 1 |
| **Forge 根本没参与打包** | 🟠 "四加载器"名不副实 | 见任务 2 |
| **没有自动检查** | 🟠 包坏了没人知道 | 见任务 3 |
| **主页链接指向不存在的网址** | 🟡 玩家点开 404 | 见任务 4 |
| **开光影时高倍镜是黑镜片** | 🟡 已知缺陷，上游也没解决 | 见任务 5 |
| **有些代码借来的，没记录来源** | 🟡 以后难维护 | 见任务 7 |

**没有验证过的东西（不要对外声称）：**

- Forge 从来没实际跑起来过（代码在，但从没成功构建过）
- 高倍镜的"画中画"功能只在**关光影**时确认可用
- 四种加载器的**多人联机**都只是在开发环境里起过服务器，没有真实多人测试

---

## 3. 五个收尾任务（按顺序做，做完一个再下一个）

> 每个任务都写了「怎么做」「怎么验证」。
> **验证通不过就不要继续下一个任务**，不要靠猜。

### 任务 1：换掉会崩的 NeoForge 版本号 ★最优先

**要做什么：** 打开根目录的 `gradle.properties`，找到这一行：

```
neoforge_version=26.3.0.4-beta
```

改成：

```
neoforge_version=26.3.0.51-beta
```

**为什么要做：** 现在钉的这个版本太旧。新版 NeoForge 删掉了一个旧接口，
用旧版本编译出来的成品，玩家装上**新版 NeoForge 会直接崩溃、进不去游戏**。
（这是上游作者 2026-10-05 发现并修掉的问题，本仓库还没跟上。）

**怎么验证（三步，缺一不可）：**

1. 命令行运行：`./gradlew neoforge:build --console=plain`
   → 必须看到 `BUILD SUCCESSFUL`
2. 如果编译报错提到 `ModConfig` 或 `Type.COMMON` / `Type.SERVER`：
   说明光改版本号不够，还需要改代码（新版把这两个名字换成了 `LOCAL` / `SYNCED`）。
   在 `common/src/main/java/com/tacz/guns/config/` 下按报错信息改。
3. 运行 `./gradlew neoforge:runClient --console=plain`，等到出现游戏主菜单，
   然后**手动关掉窗口**。日志文件在 `neoforge/run/logs/latest.log`，
   搜索 `ERROR` 和 `Exception`，**除了登录/联网类的报错，其他都不能有**。

**做完的标准：** 编译成功 + 客户端能开到主菜单 + 日志无新异常。

---

### 任务 2：让 Forge 也参与打包

**要做什么：** 在 `gradle.properties` 里找到：

```
forge_enabled=false
```

改成 `true`。

**为什么要做：** 现在打包默认跳过 Forge，所以"四个加载器都能用"这句话是虚的。
而且因为没有自动检查（任务 3），Forge 的代码可能已经悄悄烂掉了。

**注意：** 这个任务可能需要联网下载 Forge 的工具。如果卡住不动，
不要用 `--offline` 参数（离线模式会一直等到超时）。用普通命令，耐心等。

**怎么验证：**

1. `./gradlew build --console=plain` → 看到 `BUILD SUCCESSFUL`
2. 打开 `build/libs` 文件夹，**必须同时看到四个文件**：
   `tacz-fabric-*.jar`、`tacz-quilt-*.jar`、`tacz-neoforge-*.jar`、`tacz-forge-*.jar`
3. 如果 Forge 编译失败：**优先选择"把开关改回 false，并在 README 里写明 Forge 暂不支持"**，
   而不是花几天去硬修。诚实的"三个加载器"比虚假的"四个加载器"有价值。

**做完的标准：** `build/libs` 里四个包齐全。做不到就回到三个包 + 改文档。

---

### 任务 3：装一个自动检查（防止以后悄悄烂掉）

**要做什么：** 在仓库里新建文件 `.github/workflows/build.yml`，
让 GitHub 每次有人改代码时，自动跑一次编译，成功/失败会显示在仓库页面上。

**为什么要做：** 现在**没有任何自动检查**。这意味着：改坏了一个加载器，
可能过几个月才有人发现（Forge 现在就是这么烂掉的）。
有了它，任何人（或 AI）改完代码，机器会立刻告诉你"还能不能编译"。

**必须包含的检查项：**

1. `./gradlew build` 能通过
2. `build/libs` 里四个 jar 都存在（如果任务 2 没做到，就检查三个）
3. **产物里不能含这几个危险文件**（用一条命令检查 jar 内容）：
   - 任何含 `luajc` 的文件
   - 任何含 `OsLib`、`IoLib`、`Javajava`、`JseProcess` 的文件

   **为什么：** 这个模组允许玩家下载"枪包"，枪包里可以带脚本。
   为了安全，本项目故意把脚本能执行系统命令的能力**从成品里删掉了**。
   但现在的删除方式靠"手写一串文件名"，已经漏掉了三个文件
   （实测成品里有 `luajc$1.class` 等三个文件还留着）。
   装上这条检查后，以后漏删会立刻报警。

**做完的标准：** GitHub 仓库页面能看到一个绿色的勾（通过），
并且**故意改坏一个文件后能看到红色的叉**（证明检查真的在工作）。

---

### 任务 4：改掉接不到的链接

**要做什么：** 把下面四个文件里所有的 `fmpapierz/TACZ-updated` 改成 `FiVE0016/TACZ-updated-fixed`：

- `fabric/src/main/resources/fabric.mod.json`
- `quilt/src/main/resources/quilt.mod.json`
- `neoforge/src/main/resources/META-INF/neoforge.mods.toml`
- `forge/src/main/resources/META-INF/mods.toml`

**为什么要做：** 这些是写给玩家看的"主页 / 问题反馈"地址，现在指向一个**不存在的网址**。
玩家点了是 404，也没法给你反馈问题。

**怎么验证：** 编译一次，然后看 `build/libs` 里任一 jar 内部的那四个文件（用压缩软件打开），
确认里面写的是你自己的仓库地址。

**做完的标准：** 四个文件里没有 `fmpapierz` 字样。

---

### 任务 5：把"黑镜片"从 bug 变成"说明"

**要做什么：** 在 `README.md` 的「已知问题」第 1 条里，明确写成：

> 开光影时，高倍镜（4 倍以上）**不提供镜内放大**，会退回整屏变焦。
> 这是有意的降级（不是故障）。想要镜内放大，请关闭光影，或把
> `tacz-client.toml` 里的 `ScopePipMinMagnification` 调到高于你所用瞄具的倍率。

**为什么要做：** 现在的写法是"根因尚未定位"——这会让玩家觉得"这是个坏了的模组"。
而实际上：**官方版和另外两个移植版同样解决不了这个问题**，
它的成因是"要在光影渲染流程里插一脚"，而 26.3 刚好重写了整个渲染底层。
把它写成"有意的降级"，用户能预期，接手的人也不用背这个包袱。

**不要做的事：** 不要为了修它去重写渲染代码。
如果你（AI）打算这么做，**先停下来问仓库主人**。
理由：这块代码有 1000 多行，作者自己在注释里写了"靠静态分析已经连错四次"，
属于投入极大、收益极小的深坑。

**做完的标准：** README 里这一条的措辞从"未修复"变成"有意降级 + 如何绕过"。

---

### 任务 6（可选，但强烈建议）：把版本号集中到一处

**要做什么：** 记下一件事即可：**所有版本号都在根目录的 `gradle.properties` 一个文件里**。
（`minecraft_version`、`neoforge_version`、`fabric_api_version` 等十来个。）

**为什么要做：** 以后升级到 26.4 时，你只需要面对这一个文件，而不是满仓库找。
这条不用改代码，只要在 README 里加一句提示就好。

---

### 任务 7：给借来的代码登记来源

**要做什么：** 新建 `THIRD_PARTY.md`，写清楚本仓库里有三份代码是**从别人那里搬来的**：

| 位置 | 来源 | 许可 |
|---|---|---|
| `common/src/main/java/cn/sh1rocu/tacz/`（约 121 个文件） | `Sh1roCu/TACZ-Refabricated` | GPL-3.0 |
| `common/src/main/java/me/xjqsh/lrtactical/`（约 114 个文件） | LesRaisins 战术装备（LRTactical） | GPL-3.0 |
| `common/src/main/java/cn/sh1rocu/tacz/compat/meshloader/`（约 20 个文件） | `VellEagle/TacZMeshLoader` | GPL-3.0 |

**为什么要做：** 这三份加起来约 2 万行，占了核心代码的四分之一。
没有登记的话，接手的人会以为全是原创，改错了也不知道该跟谁对。

**做完的标准：** `THIRD_PARTY.md` 存在，且上面三个来源、许可写清楚了。

---

## 4. 以后 Minecraft 出新版本（26.4、26.5……）时怎么办

**这是本仓库的长期成本，必须有心理准备：这不是一次性的活。**

Minecraft 每次更新都可能改底层，而 TACZ 是靠"扎进游戏内部"来实现开镜、
手部动画这些效果的——扎得越深，新版本一动就越容易断。26.3 就是一次大改
（换掉了整个图形底层），上游两位作者为了这一个版本花了很久。

### 升级操作清单（照做即可）

**第 1 步：只改 `gradle.properties` 里的版本号**

把 `minecraft_version`、`neoforge_version`、`fabric_api_version`、
`quilt_loader_version`、`forge_version`、`cloth_config_version`、
`jei_version`、`modmenu_version` 等，逐个换成新版本对应的号。
每个号去对应官网/发布页查当前值，**不要猜**。

**第 2 步：编译，看报什么错**

```
./gradlew build --console=plain
```

**第 3 步：按报错改代码**

每次大版本更新，通常集中在这些地方（本仓库踩过的 26.2→26.3 的坑，
全部记录在 `docs/PORTING_NOTES_26.3.md`，**升级前先读那份**）：

- 游戏换掉了窗口/输入底层（26.2→26.3 是 GLFW 换成 SDL）
- 渲染流程被改写（26.3 改成了"显式 render pass"）
- 第一人称手部渲染方式变了

**第 4 步：实机跑一遍**（这一步不能省）

对每个加载器分别运行：
`./gradlew <加载器名>:runClient --console=plain`（如 `fabric:runClient`），
进主菜单、进世界、开一枪。日志在 `<加载器名>/run/logs/latest.log`。

**第 5 步：更新文档**

- `README.md` 顶部的版本号
- `docs/PORTING_NOTES_<新版本>.md`：新写一份，记录这次改了哪些东西
  （照抄 `PORTING_NOTES_26.3.md` 的格式，那份写得很清楚，是好模板）

### ⚠️ 升级时绝对不要做的事

**不要在全仓库搜索替换 `26.3` 这个数字！**

实测过的坑：默认枪包的动画文件里有**几百处** `26.3`，但那些是**动画的坐标数值**，
跟游戏版本毫无关系（例如 `ai_awp.animation.json` 里有 31 处）。
全仓库替换会一次性毁掉所有枪械动画，而且很难查出来。

**只改这些地方**：`gradle.properties`、`README.md`、以及四个模组描述文件
（`fabric.mod.json` / `quilt.mod.json` / `neoforge.mods.toml` / `mods.toml`）。

---

## 5. 怎么确认"我改完没弄坏"（任何人都能跑的检查）

每次改完任何东西，跑这三条：

| 步骤 | 命令 | 通过标准 |
|---|---|---|
| 1. 编译 | `./gradlew build --console=plain` | 显示 `BUILD SUCCESSFUL` |
| 2. 看产物 | 打开 `build/libs` | 四个（或三个）jar 都在，且日期是今天 |
| 3. 起一次游戏 | `./gradlew fabric:runClient --console=plain` | 能看到游戏主菜单 |

**日志里可以忽略的报错**（这些是正常的，不是坏了）：

- `401`、`InvalidCredentials`、`Realms`、`Failed to fetch user properties`
  → 开发环境的登录问题，跟你无关
- `Requested post effect does not exist: minecraft:end_of_frame` → 游戏自己的冗余信息

**日志里必须追查的报错：**

- 任何 `Exception`、`NoClassDefFoundError`、`NoSuchMethodError`、`mixin` 失败
- 任何带 `[TACZ` 开头的 `ERROR` 行

---

## 6. 不许做的事（给 AI 助手的硬性约束）

> 这一节是写给 AI 的。仓库主人没有编程基础，无法判断你的改动是否危险，
> 所以**以下每一条都是硬约束**。

1. **不许重构。** 不要"顺便优化一下结构"、"把这段代码整理得更清晰"。
   这个项目能跑是多方因素恰好凑齐的结果，大改结构极易破坏它。
2. **不许碰 `common/src/main/resources/assets/tacz/` 里的动画、模型、贴图文件。**
   换版本时尤其注意：这些文件里含有大量与游戏版本无关的数字。
3. **不许碰 `common/src/main/java/me/xjqsh/lrtactical/` 和
   `common/src/main/java/cn/sh1rocu/tacz/`**（借来的代码）。
   要改就先问仓库主人，并在 `THIRD_PARTY.md` 记录。
4. **不许在光影下的"瞄具画中画"上做重写。** 见任务 5。
5. **不许改模组 id（`tacz`）和版本号里的 `1.1.8`。**
   全世界的枪包都靠 `>=1.1.8` 这个规则来判断能不能用，改了会导致所有枪包失效。
6. **不许用 `--offline` 参数跑需要联网的任务**（比如 Forge 相关构建）。
   它会一直等到超时，看起来像"卡死了"。
7. **不许把"没实测过"的事情写成"已完成"。** 本文档第 2 节的"没验证过"清单
   就是这个规矩的来源。
8. **每完成一个任务，就更新这份文件的第 2 节状态表。**
   这份文件如果和现实不符，它就变成了新的坑。

---

## 7. 一页速查

| 我想…… | 去哪 |
|---|---|
| 改版本号 | 根目录 `gradle.properties`（所有版本号都在这一个文件里） |
| 编译出成品 | `./gradlew build`，产物在 `build/libs` |
| 起游戏测试 | `./gradlew fabric:runClient`（或 `quilt:` / `neoforge:` / `forge:`） |
| 看游戏日志 | `<加载器名>/run/logs/latest.log` |
| 看 26.3 移植时踩过的坑 | `docs/PORTING_NOTES_26.3.md`（写得很详细，是好模板） |
| 给玩家看的说明 | `README.md` |
| 搞不清某块代码是谁的 | 本文档第 3 节任务 7 / `THIRD_PARTY.md` |
| 出问题了想回退 | `git log` 看历史，`git revert` 回退（不要用 `git reset --hard`） |

---

## 8. 这份文件本身怎么维护

- 每次做完任务 1–7 中的任何一个，**回来改第 2 节的状态表**（把 ✅ 打上）
- 每次升级 Minecraft 版本后，**回来更新第 4 节的版本号和坑**
- 如果你发现这份文件写错了，**先改这份文件**，再改代码

**最后一句给接手的人**：这个仓库的原始作者没有编程基础，是靠 AI 一步步做出来的。
它的价值在于"一份代码支持四个加载器"和"一份能跑的 26.3 移植"。
请**保守地改动**，宁可慢，不要让它变成没人能接的烂摊子。
