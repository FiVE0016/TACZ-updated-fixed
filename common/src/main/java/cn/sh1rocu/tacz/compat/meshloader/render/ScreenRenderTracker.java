package cn.sh1rocu.tacz.compat.meshloader.render;

/**
 * 「此刻是否正在执行 Screen 的提取（extract）阶段」的追踪器。
 *
 * <h2>为什么不能用 {@code minecraft.screen != null} 或
 * {@code RenderDistance.isGuiRender()}</h2>
 *
 * <p>世界提取与 GUI 提取在同一帧内是<b>分时</b>进行的。「菜单开着」
 * （{@code screen != null}）或「最近 100ms 内画过 GUI」（{@code
 * RenderDistance.isGuiRender()} 的时间戳窗口）这两种判定在<b>世界提取阶段</b>
 * 也为 true —— 拿它们做世界 GPU 路径的闸门，等于玩家一开背包，地上/别人手里
 * 的全部 mesh 枪瞬间跌回 collector 重路径。这正是上游 TML
 * {@code ScreenRenderTracker} 注释里记载的实机事故（菜单一开全场景掉帧），
 * 上游的解法就是本类的 1.21.1 版：用 Fabric 的 {@code ScreenEvents} 精确框住
 * Screen 渲染窗口。</p>
 *
 * <p>Multiloader port: the Fabric-only fork used {@code ScreenEvents.beforeExtract/afterExtract},
 * which wrap the call to {@code Screen#extractRenderStateWithTooltipAndSubtitles} in
 * {@code Gui#extractRenderState}. NeoForge and Forge move that call into their own screen render
 * hooks, so {@code ScreenExtractTrackerMixin} brackets the method itself instead; every loader still
 * calls it, and the window is the same. GUI-embedded 3D (the inventory player model, the gun smith
 * table preview) is submitted inside it.</p>
 *
 * <p>世界 GPU 表（{@code PolyMeshGpuRenderer#WORLD_DRAWS}）用它挡住的事故是
 * 关 PR #33 的复刻版：Screen 内嵌 3D 预览的 submit 若落进世界表，要么被
 * 世界投影画到错误位置，要么因世界 pass 已消费而整层消失。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
public final class ScreenRenderTracker {

    private static volatile boolean extractingScreen = false;

    private ScreenRenderTracker() {
    }

    /** 此刻是否正在 Screen 的 extract 阶段内部（而不是「有菜单开着」）。 */
    public static boolean isExtractingScreen() {
        return extractingScreen;
    }

    /** Set by {@code ScreenExtractTrackerMixin} at the start and end of a screen's extract pass. */
    public static void setExtractingScreen(boolean value) {
        extractingScreen = value;
    }
}
