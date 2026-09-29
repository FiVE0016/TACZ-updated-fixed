# Timeless and Classics Guns: Zero — Minecraft 26.3 (Fabric · Quilt · NeoForge · Forge)

An **unofficial** port of [Timeless and Classics Guns: Zero](https://github.com/MCModderAnchor/TACZ) (TACZ) to
Minecraft 26.3, built from one shared codebase for four mod loaders. It is based on the upstream Forge 1.20.1
mod and on the Fabric-only 26.2 port [TaCZ Refabricated](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial)
(itself derived from [Sh1roCu/TACZ-Refabricated](https://github.com/Sh1roCu/TACZ-Refabricated)).

This port is not affiliated with or supported by the TACZ, TaCZ Refabricated or LRTactical authors. Please do not
report problems with it to them.

> **About the 26.3 port.** 26.3 replaced GLFW with SDL, moved the GPU abstraction into a new
> `com.mojang.renderpearl` library, swapped global render-output redirection for explicit render
> passes, and converted first-person hand rendering to extracted render states. The port is across
> all of that: the build is green, all four loaders' clients and dedicated servers start cleanly
> with no mixin failures, and in-world sessions on all four — firing, reloading and aiming — ran
> with no mod exception beyond the harmless Hull-fill read-back fallback noted under Known issues.
> The scope picture-in-picture path, the most heavily rewritten area, has now been driven end to
> end in world on all four. `docs/PORTING_NOTES_26.3.md` has the full API map and what was
> verified.

## Requirements

| Loader   | Needs                                                                                     |
|----------|-------------------------------------------------------------------------------------------|
| Fabric   | Fabric Loader 0.19.5+ and Fabric API 0.160.7+26.3                                          |
| Quilt    | Quilt Loader 0.31.0-beta.4+ and Fabric API 0.160.7+26.3 (QSL has no 26.x release)          |
| NeoForge | NeoForge 26.3.0.4-beta+ (26.3 has only beta builds so far)                                 |
| Forge    | Forge 26.3-66.0.2+                                                                         |

All loaders need Java 25. Use the jar that matches your loader; each one bundles its libraries (LuaJ,
Commons Math, Mayday Animation Engine), relocated so they cannot clash with other mods.

`forge_enabled` in `gradle.properties` drops the `forge` module from the build when set to `false`,
which is useful while waiting for Forge to release for a new Minecraft version.

## Building

```bash
./gradlew build
```

The release jars end up in `build/libs/`: `tacz-fabric-<version>.jar`, `tacz-quilt-…`, `tacz-neoforge-…`
and `tacz-forge-…`. (The `-slim` jars inside each module's own `build/libs` lack the bundled libraries.)

Development runs: `./gradlew :fabric:runClient` (or `:quilt:`, `:neoforge:`, `:forge:`), and `runServer` for a
dedicated server in the module's `run-server` folder. The dev server reads console commands typed into the Gradle
terminal on every loader. Dev clients also load Cloth Config (plus Mod Menu on Fabric and Quilt), so the config
screen can be tried there.

`-PtaczQuickPlay=<world folder>` is wired on every loader's `runClient` to pass
`--quickPlaySingleplayer`. **It does not currently do anything on 26.3**: the flag reaches the JVM (verified on the
process command line) but the game ignores it in a dev launch — even a nonexistent world name draws no complaint,
so nothing is reaching `QuickPlay`. The wiring is kept because it is the correct API and costs nothing if a later
Minecraft or dev-launcher build starts honouring it again; until then, open worlds from the main menu.

## Project layout

- `common/` — all gameplay, networking, resource and rendering code. It compiles against plain Minecraft and must
  not import any loader API; `./gradlew :common:checkLoaderNeutral` enforces that.
- `fabric/` (whose sources the `quilt/` module reuses), `neoforge/`, `forge/` — entrypoints and platform services only.

Common code reaches the loader through a few seams:

| Seam | Purpose |
|------|---------|
| `com.tacz.guns.platform.Platform`, `NetworkPlatform`, `MenuPlatform` | Loader services, found with `ServiceLoader` (`META-INF/services` in each loader module) |
| `com.tacz.guns.network.NetworkHandler#payloads()` | Every network payload with its codec, direction and handler |
| `com.tacz.guns.init.TaczRegistration` | Registers content one registry at a time (NeoForge/Forge registry events; Fabric registers everything at once) |
| `TaczCommonEvents`, `TaczClientEvents`, `ClientSetupEvent`, `ModEntitiesRender` | Lifecycle, tick, player, command and client registration hooks the loaders call |
| `com.tacz.guns.config.spec.TaczConfigSpec` | Self-contained TOML config (no Forge Config API Port) |

## Configuration

- `config/tacz-common.toml`, `config/tacz-client.toml`, `config/tacz-pre.toml`
- `<world>/serverconfig/tacz-server.toml` — per world, copied from `defaultconfigs/tacz-server.toml` when present,
  and sent to clients when they join a server.
- In game: `/tacz config`. With Cloth Config installed there is also a config screen (Mod Menu on Fabric/Quilt,
  the mod list on NeoForge/Forge).

## Optional integrations

TACZ only activates an integration when the other mod is installed.

| Mod | Fabric | Quilt | NeoForge | Forge | Notes |
|-----|:------:|:-----:|:--------:|:-----:|-------|
| JEI | ✓ | ✓ | ✓ | ✓ | Recipes, attachment and ammo queries |
| REI | — | — | — | — | REI has no 26.3 build at all; the integration code is kept and compiles against the 26.2 API |
| Cloth Config | ✓ | ✓ | ✓ | — | Config screen. On Forge use `/tacz config` or the config files |
| Mod Menu | ✓ | ✓ | — | — | Config button |
| Iris | ✓ | ✓ | ✓ | — | Shader compatibility |
| Player Animation Library | ✓ | ✓ | ✓ | — | Third-person animations |
| Shoulder Surfing Reloaded | — | — | — | — | No 26.3 build yet; the integration code is kept and compiles against the 26.2 API |
| Carry On | ✓ | ✓ | ✓ | ✓ | |
| Zoomify | ✓ | ✓ | — | — | Zoomify is Fabric/Quilt only |
| Punchy | ✓ | ✓ | ✓ | ✓ | TACZ viewmodels opt out of Punchy's hand animations |

A dash above means the other mod has no 26.3 release, not that support was dropped — TACZ activates
each integration only when the other mod is present, so those rows light up again once it ships.

The scope picture-in-picture renderer also keeps Sodium, Iris, Voxy and Physics Mod in step when it redraws the
world through a lens. Those hooks use reflection and do nothing when the mod is absent.

Integrations from TACZ or the fork that could not come back because the other mod has no 26.x release: KubeJS,
Controllable, Accelerated Rendering, OptiFine, and KosmX's playerAnimator (replaced by Player Animation Library).

## Built-in add-ons

TaCZ Refabricated bundles three add-ons, and so does this port, on every loader:

- **LRTactical**: throwables (grenades, flashbangs, smoke, gas, sticky and splash grenades, C4 with a detonator),
  melee weapons and consumables, all defined by gun packs. Packs that depend on `lrtactical` still load.
- **TacZ Mesh Loader**: gun packs can use high-poly `"model_type": "mesh"` models. Packs that depend on
  `taczmeshloader` still load. Settings live in the `[mesh_loader]` section of `tacz-client.toml`.
- **Scope picture-in-picture**: a scope can redraw the world through its lens at the scope's magnification. It is
  off by default. Turn it on with `ScopePipEnable` and `ScopePipRerender` in the `[render]` section of
  `tacz-client.toml`.

On Fabric and Quilt the jar also provides the mod ids `lrtactical` and `taczmeshloader`, as the fork did, so the
loader refuses to start if a standalone copy of either mod is installed alongside it. Don't install standalone copies
on NeoForge or Forge either, because their classes would clash.

## Gun pack scripts

Gun packs can ship Lua scripts, and TACZ runs them in a restricted interpreter: it loads only the base,
package, bit32, table, string and math libraries, so the `io`, `os`, coroutine and `luajava` libraries are
absent. The bundled LuaJ also ships without the classes behind them — its process, `io.popen`,
`os.execute`, Java reflection and runtime bytecode compiler code — so a pack script cannot run OS commands
or reach arbitrary Java classes.

## Known issues

- Scope picture-in-picture runs, but nobody has checked how the image *looks*. It and the mesh GPU
  renderer were rebuilt onto 26.3's explicit-render-pass model (26.3 removed the `RenderSystem`
  output-texture overrides they relied on). With `ScopePipEnable` on, the whole chain — ocular
  mask, render target, second render pass, composite — completed in world on all four loaders, at
  4.5x and 25x, with no render-pass or shader errors and no self-deactivation. What is unverified
  is the picture itself: framing, alignment and parallax through the lens.
  See `docs/PORTING_NOTES_26.3.md`.
- NeoForge and Forge grey out TACZ's Config button in the mod list when Cloth Config is not installed;
  on Forge use `/tacz config` or the config files.
- The port ships no LRTactical display assets, so melee weapons look and swing like vanilla items unless a gun pack
  provides their models and animations.
- `[TACZ Scope] Hull-fill: could not read back the projection UBO`, followed by a
  `Buffer is not readable` stack trace. Harmless — the scope renderer falls back to per-cube tracing.
  On 26.2 this only ever happened on Forge; on 26.3 it is **no longer loader-specific** (observed on
  Quilt as well), which fits 26.3 moving the GPU layer to `renderpearl` and its Vulkan-capable
  backend, where mapping the projection uniform buffer for read-back is not generally available.

## Credits and licenses

- **TACZ**: programmers 286799714, TartaricAcid, F1zeiL, xjqsh and ClumsyAlien; artists NekoCrane, Receke and
  Pos_2333. Code is GPL-3.0. Assets, including the default gun pack, are CC BY-NC-ND 4.0.
- **TaCZ Refabricated** (Fabric port): Sh1roCu, with the 26.x port by q14433686-arch. GPL-3.0.
- **LRTactical** (LesRaisins Tactical Equipements): the code portions are GPL-3.0. The few LRTactical resources
  the fork ships (language files, item definitions, state-machine scripts, two effect icons and one sound) are
  included as the fork ships them.
- **TacZ Mesh Loader**: VellEagle, GPL-3.0. Ported from v0.1.7 by way of TaCZ Refabricated.
- Bundled libraries: Mayday Animation Engine (MIT), LuaJ (Figura fork, MIT) and Apache Commons Math (Apache-2.0).

The port's own changes are GPL-3.0, like the code they modify. See `LICENSE`.
