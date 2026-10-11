package com.tacz.guns.client.gameplay;

import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.ModelRendererWrapper;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.animation.bedrock.AnimationBone;
import com.tacz.guns.client.resource.pojo.animation.bedrock.AnimationKeyframes;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimation;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 开火瞬间隐藏「被消耗掉的那一发」（火箭弹头 / 枪榴弹 / 弩箭 …）。
 *
 * <h2>要解决的现象</h2>
 * 火箭筒开火后，管子里的弹头要等到<b>射击动画放完、状态机切进"打空"状态</b>才消失，
 * 于是弹丸已经飞出去了、膛里还挂着一发 —— 看起来像"延迟消失"。
 *
 * <h2>为什么不能靠猜骨骼名字</h2>
 * 各枪包给弹头起的名字五花八门（{@code rocket} / {@code warhead} / {@code round} /
 * {@code bullet} / {@code arrow1..5} / {@code head} …），写死名单必然漏。
 *
 * <h2>做法：让枪包自己说话</h2>
 * 枪包的动画里已经写明了「打空之后长什么样」—— 例如默认包 RPG7：
 * <pre>
 *   static_bolt_caught.rocket.scale = { "0.0": [0,0,0], "0.0333": [0,0,0] }
 * </pre>
 * 所以：<b>凡是"打空状态（*bolt_caught* 片段）第一个关键帧就把 scale 设成 0"的骨骼，
 * 就是枪包自己声明"打完后不该出现"的那部分</b>。开火瞬间提前把这套隐藏姿态应用上，
 * 等状态机真正进入该状态时数值完全一致 —— 不会有跳变；换弹时枪包照常把它缩放回来。
 *
 * <p>可以用启动参数 {@code -Dtacz.hideConsumedRound=false} 关掉。</p>
 */
public final class ConsumedRoundHider {

    private static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("tacz.hideConsumedRound", "true"));

    /** 拿不到 shoot 片段时长时的兜底窗口。 */
    private static final long DEFAULT_WINDOW_MS = 800L;

    private static volatile long fireTimeMs = Long.MIN_VALUE;
    private static volatile long windowMs = DEFAULT_WINDOW_MS;

    private ConsumedRoundHider() {
    }

    /** 扫描结果：需要隐藏的骨骼 + 该枪 shoot 片段的时长（秒）。 */
    public record Result(Set<String> bones, double shootClipLengthS) {
        public static final Result EMPTY = new Result(Set.of(), 0.0);
    }

    /**
     * 从一个动画文件里推导「打空后应当消失的骨骼」。
     *
     * @param file 该枪的基岩版动画文件（可为 null）
     */
    public static Result scan(@Nullable BedrockAnimationFile file) {
        if (file == null || file.getAnimations() == null) {
            return Result.EMPTY;
        }
        Set<String> bones = new HashSet<>();
        double shootLength = 0.0;
        for (Map.Entry<String, BedrockAnimation> entry : file.getAnimations().entrySet()) {
            String clip = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
            BedrockAnimation animation = entry.getValue();
            if (animation == null || animation.getBones() == null) {
                continue;
            }
            if ("shoot".equals(clip)) {
                shootLength = animation.getAnimationLength();
            }
            // 只看"打空"状态：这是枪包自己声明「打完之后不该出现」的骨骼
            if (!clip.contains("bolt_caught")) {
                continue;
            }
            for (Map.Entry<String, AnimationBone> boneEntry : animation.getBones().entrySet()) {
                AnimationKeyframes keyframes = boneEntry.getValue() == null ? null : boneEntry.getValue().getScale();
                if (keyframes == null || keyframes.getKeyframes() == null || keyframes.getKeyframes().isEmpty()) {
                    continue;
                }
                // Double2ObjectRBTreeMap 的 values() 按时间升序 → 第一个就是最早的关键帧
                AnimationKeyframes.Keyframe first = keyframes.getKeyframes().values().iterator().next();
                Vector3f value = first == null ? null : first.data();
                if (value != null
                        && Math.abs(value.x()) < 1.0E-4f
                        && Math.abs(value.y()) < 1.0E-4f
                        && Math.abs(value.z()) < 1.0E-4f) {
                    bones.add(boneEntry.getKey());
                }
            }
        }
        return new Result(Set.copyOf(bones), shootLength);
    }

    /** 本地玩家开火时调用。 */
    public static void markFired() {
        fireTimeMs = System.currentTimeMillis();
    }

    /** 窗口时长（秒），由渲染侧按该枪的 shoot 片段时长校正。 */
    public static void setWindowSeconds(double seconds) {
        if (seconds > 0.05 && seconds < 3.0) {
            windowMs = (long) (seconds * 1000.0);
        }
    }

    /** 是否还在"刚开火"的窗口内。 */
    public static boolean isActive() {
        return ENABLED && System.currentTimeMillis() - fireTimeMs <= windowMs;
    }

    /**
     * 渲染前调用：把该枪"打空后应当消失的骨骼"缩放清零。
     *
     * <p>时序上必须在状态机应用完动画之后、模型提交之前调用 —— 见调用点的说明。</p>
     */
    public static void applyTo(@Nullable BedrockGunModel model, @Nullable GunDisplayInstance display) {
        if (model == null || display == null || !isActive()) {
            return;
        }
        Set<String> bones = display.getConsumedRoundBones();
        if (bones.isEmpty()) {
            return;
        }
        // 用该枪自身的 shoot 片段时长作为窗口：片段放完时状态机本来就会隐藏它们，衔接无跳变
        setWindowSeconds(display.getShootClipLengthS());
        for (String bone : bones) {
            ModelRendererWrapper wrapper = model.getBoneWrapper(bone);
            if (wrapper != null) {
                wrapper.setScaleX(0.0f);
                wrapper.setScaleY(0.0f);
                wrapper.setScaleZ(0.0f);
            }
        }
    }
}
