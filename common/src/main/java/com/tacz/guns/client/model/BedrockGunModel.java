package com.tacz.guns.client.model;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationListener;
import com.tacz.guns.api.client.animation.ObjectAnimationChannel;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.bedrock.ModelRendererWrapper;
import com.tacz.guns.client.model.functional.*;
import com.tacz.guns.client.model.listener.model.ModelAdditionalMagazineListener;
import com.tacz.guns.client.resource.pojo.display.gun.TextShow;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

import static com.tacz.guns.client.model.GunModelConstant.*;

public class BedrockGunModel extends BedrockAnimatedModel {
    protected final EnumMap<AttachmentType, List<BedrockPart>> refitAttachmentViewPath = Maps.newEnumMap(AttachmentType.class);
    private final EnumMap<AttachmentType, ItemStack> currentAttachmentItem = Maps.newEnumMap(AttachmentType.class);
    private final Set<String> adapterToRender = Sets.newHashSet();
    private final ArrayList<ShellRender> shellRenderList = new ArrayList<>();

    // 第一人称机瞄摄像机定位组的路径
    protected @Nullable List<BedrockPart> ironSightPath;
    // 第一人称idle状态摄像机定位组的路径
    protected @Nullable List<BedrockPart> idleSightPath;
    // 第三人称手部物品渲染原点定位组的路径
    protected @Nullable List<BedrockPart> thirdPersonHandOriginPath;
    // 展示框渲染原点定位组的路径
    protected @Nullable List<BedrockPart> fixedOriginPath;
    // 地面实体渲染原点定位组的路径
    protected @Nullable List<BedrockPart> groundOriginPath;
    // 瞄具配件定位组的路径。其他配件不需要存路径，只需要替换渲染。但是瞄具定位组需要用来辅助第一人称瞄准的摄像机定位。
    protected @Nullable List<BedrockPart> scopePosPath;
    // 枪口火焰定位组
    protected @Nullable List<BedrockPart> muzzleFlashPosPath;
    // 根组
    protected @Nullable BedrockPart root;
    // 弹匣定位组
    protected @Nullable BedrockPart magazineNode;
    // 换弹时第二个弹匣定位组
    protected @Nullable BedrockPart additionalMagazineNode;
    protected @Nullable List<BedrockPart> laserBeamPaths;

    private boolean renderHand = true;
    private boolean renderMount;
    private ItemStack currentGunItem;
    private int currentExtendMagLevel = 0;

    public BedrockGunModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);

        this.magazineNode = Optional.ofNullable(modelMap.get(MAG_NORMAL_NODE)).map(ModelRendererWrapper::getModelRenderer).orElse(null);
        this.additionalMagazineNode = Optional.ofNullable(modelMap.get(MAG_ADDITIONAL_NODE)).map(ModelRendererWrapper::getModelRenderer).orElse(null);

        // 左手手臂
        this.setFunctionalRenderer(LEFTHAND_POS_NODE, bedrockPart -> new LeftHandRender(this));
        // 右手手臂
        this.setFunctionalRenderer(RIGHTHAND_POS_NODE, bedrockPart -> new RightHandRender(this));
        // 枪口火焰
        this.setFunctionalRenderer(MUZZLE_FLASH_ORIGIN_NODE, bedrockPart -> new MuzzleFlashRender(this));
        // 枪管内的子弹，用于闭膛待机枪械
        this.setFunctionalRenderer(BULLET_IN_BARREL, bedrockPart -> ammoHiddenRender(bedrockPart, iGun -> iGun.hasBulletInBarrel(currentGunItem)));
        // 弹匣内子弹
        this.setFunctionalRenderer(BULLET_IN_MAG, bedrockPart -> ammoHiddenRender(bedrockPart, iGun -> iGun.getCurrentAmmoCount(currentGunItem) > 0));
        // 机枪弹链
        this.setFunctionalRenderer(BULLET_CHAIN, bedrockPart -> ammoHiddenRender(bedrockPart, iGun -> iGun.getCurrentAmmoCount(currentGunItem) > 0));
        // 有通用瞄具时显示，用于放瞄具的导轨（如 AKM 的导轨）
        this.setFunctionalRenderer(MOUNT, bedrockPart -> scopeHiddenRender(bedrockPart, scopeItem -> scopeItem != null && !scopeItem.isEmpty() && renderMount));
        // 无瞄具时可见，通常用于 M4 上
        this.setFunctionalRenderer(CARRY, bedrockPart -> scopeHiddenRender(bedrockPart, scopeItem -> scopeItem == null || scopeItem.isEmpty()));
        // 有瞄具时显示，折叠的机械瞄具
        this.setFunctionalRenderer(SIGHT_FOLDED, bedrockPart -> scopeHiddenRender(bedrockPart, scopeItem -> scopeItem != null && !scopeItem.isEmpty()));
        // 无瞄具时可见，机械瞄具
        this.setFunctionalRenderer(SIGHT, bedrockPart -> scopeHiddenRender(bedrockPart, scopeItem -> scopeItem == null || scopeItem.isEmpty()));
        // 安装一级扩容弹匣时显示
        this.setFunctionalRenderer(MAG_EXTENDED_1, bedrockPart -> extendedMagHiddenRender(bedrockPart, 1));
        // 安装二级扩容弹匣时显示
        this.setFunctionalRenderer(MAG_EXTENDED_2, bedrockPart -> extendedMagHiddenRender(bedrockPart, 2));
        // 安装三级扩容弹匣时显示
        this.setFunctionalRenderer(MAG_EXTENDED_3, bedrockPart -> extendedMagHiddenRender(bedrockPart, 3));
        // 没有安装扩容弹匣时显示
        this.setFunctionalRenderer(MAG_STANDARD, bedrockPart -> extendedMagHiddenRender(bedrockPart, 0));
        // 部分枪械换弹动画播放时，会同时出现两个弹匣，这个就是程序自动渲染另一个弹匣的代码
        this.setFunctionalRenderer(MAG_ADDITIONAL_NODE, this::renderAdditionalMagazine);
        // 默认护木渲染
        this.setFunctionalRenderer(HANDGUARD_DEFAULT_NODE, this::handguardDefaultRender);
        // 战术护木渲染
        this.setFunctionalRenderer(HANDGUARD_TACTICAL_NODE, this::handguardTacticalRender);
        // 缓存其他定位组
        this.cacheOtherPath();
        // 缓存改装 UI 下各个配件的特写视角定位组
        this.cacheRefitAttachmentViewPath();
        // 缓存抛壳窗
        this.cacheShellOriginNodes();
        // 准备各个配件的渲染
        this.allAttachmentRender();
        // 配件转接口渲染
        this.setFunctionalRenderer(ATTACHMENT_ADAPTER_NODE, this::attachmentAdapterNodeRender);
    }

    private void cacheOtherPath() {
        ironSightPath = getPath(modelMap.get(IRON_VIEW_NODE));
        idleSightPath = getPath(modelMap.get(IDLE_VIEW_NODE));
        thirdPersonHandOriginPath = getPath(modelMap.get(THIRD_PERSON_HAND_ORIGIN_NODE));
        fixedOriginPath = getPath(modelMap.get(FIXED_ORIGIN_NODE));
        groundOriginPath = getPath(modelMap.get(GROUND_ORIGIN_NODE));
        muzzleFlashPosPath = getPath(modelMap.get(MUZZLE_FLASH_ORIGIN_NODE));
        scopePosPath = getPath(modelMap.get(AttachmentType.SCOPE.name().toLowerCase() + ATTACHMENT_POS_SUFFIX));
        laserBeamPaths = getPath(modelMap.get("laser_beam"));
        root = Optional.ofNullable(modelMap.get(ROOT_NODE)).map(ModelRendererWrapper::getModelRenderer).orElse(null);
    }

    private void cacheRefitAttachmentViewPath() {
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                refitAttachmentViewPath.put(type, getPath(modelMap.get(REFIT_VIEW_NODE)));
                continue;
            }
            String nodeName = REFIT_VIEW_PREFIX + type.name().toLowerCase() + REFIT_VIEW_SUFFIX;
            refitAttachmentViewPath.put(type, getPath(modelMap.get(nodeName)));
        }
    }

    private void cacheShellOriginNodes() {
        ModelRendererWrapper rendererWrapper = modelMap.get(SHELL_ORIGIN_NODE);
        int i = 1;
        while (rendererWrapper != null) {
            String shellNodeName = rendererWrapper.getModelRenderer().name;
            ShellRender shellRender = new ShellRender(this, shellNodeName, shellRenderList.size());
            this.setFunctionalRenderer(shellNodeName, bedrockPart -> shellRender);
            shellRenderList.add(shellRender);
            rendererWrapper = modelMap.get(SHELL_ORIGIN_NODE_PREFIX + i);
            i++;
        }
    }

    @Nullable
    private IFunctionalRenderer attachmentAdapterNodeRender(BedrockPart bedrockPart) {
        for (BedrockPart child : bedrockPart.children) {
            if (child.name == null) {
                child.visible = false;
                continue;
            }
            child.visible = adapterToRender.contains(child.name);
        }
        return null;
    }

    private void allAttachmentRender() {
        for (AttachmentType type : AttachmentType.values()) {
            // 瞄具的渲染需要提前
            if (type == AttachmentType.NONE || type == AttachmentType.SCOPE) {
                continue;
            }
            String positionNodeName = type.name().toLowerCase() + ATTACHMENT_POS_SUFFIX;
            String defaultNodeName = type.name().toLowerCase() + DEFAULT_ATTACHMENT_SUFFIX;
            this.setFunctionalRenderer(positionNodeName, bedrockPart -> {
                bedrockPart.visible = false;
                return new AttachmentRender(this, type);
            });
            this.setFunctionalRenderer(defaultNodeName, bedrockPart -> {
                ItemStack attachmentItem = currentAttachmentItem.get(type);
                if (type == AttachmentType.MUZZLE && checkShowMuzzle(bedrockPart, attachmentItem)) {
                    return null;
                }
                bedrockPart.visible = attachmentItem == null || attachmentItem.isEmpty();
                return null;
            });
        }
    }

    private static boolean checkShowMuzzle(BedrockPart bedrockPart, ItemStack attachmentItem) {
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (iAttachment != null) {
            Identifier attachmentId = iAttachment.getAttachmentId(attachmentItem);
            var attachmentIndex = TimelessAPI.getClientAttachmentIndex(attachmentId);
            if (attachmentIndex.isPresent()) {
                bedrockPart.visible = attachmentIndex.get().isShowMuzzle();
                return true;
            }
        }
        return false;
    }

    @Nullable
    private IFunctionalRenderer handguardTacticalRender(BedrockPart bedrockPart) {
        ItemStack laserItem = currentAttachmentItem.get(AttachmentType.LASER);
        ItemStack gripItem = currentAttachmentItem.get(AttachmentType.GRIP);
        bedrockPart.visible = !laserItem.isEmpty() || !gripItem.isEmpty();
        return null;
    }

    @Nullable
    private IFunctionalRenderer handguardDefaultRender(BedrockPart bedrockPart) {
        ItemStack laserItem = currentAttachmentItem.get(AttachmentType.LASER);
        ItemStack gripItem = currentAttachmentItem.get(AttachmentType.GRIP);
        bedrockPart.visible = laserItem.isEmpty() && gripItem.isEmpty();
        return null;
    }

    /**
     * {@code additional_magazine} 节点 —— 换弹动画中<b>留在枪身上</b>的那一个弹匣。
     *
     * <p><b>第 8 轮修复：撤销第 2 轮的错误改动。</b></p>
     *
     * <p>模型里两个弹匣节点语义不同：{@code magazine} 是换弹时<b>跟着手走</b>的那一个，
     * {@code additional_magazine} 是<b>留在枪上</b>的那一个。上游 1.21.1 的做法是
     * 在本节点的变换下，把 {@code magazine} 的网格<b>再画一遍</b>（同一份几何渲染两次）。
     * 默认枪包的 {@code reload_tactical}/{@code reload_empty}/{@code inspect}
     * 等动画同时驱动这两个节点，正依赖该行为。</p>
     *
     * <p>第 2 轮我改成了 {@code return null}，理由是"{@code magazine} 本来就在模型树里
     * 会被遍历到"—— <b>那个判断是错的</b>：树里那份是跟手的，留在枪上的那份只能靠这里补画。
     * 症状即你反馈的：换弹/空仓换弹时<b>枪上的弹匣不渲染，只剩手里那个</b>。</p>
     *
     * <p>现改为返回 {@link IMirrorGeometry}，由 {@code BedrockRenderSnapshot} 原生处理：
     * 在本节点变换下先画自己、再画 {@code magazine}，且与枪身共用同一 RenderType
     * 与 DrawCommand 批次，保证材质与渲染顺序正确。</p>
     */
    @Nullable
    private IFunctionalRenderer renderAdditionalMagazine(BedrockPart bedrockPart) {
        return (IMirrorGeometry) () -> magazineNode;
    }

    /**
     * 添加枪械自定义的文本显示
     */
    public void setTextShowList(Map<String, TextShow> textShowList) {
        textShowList.forEach((name, textShow) -> this.setFunctionalRenderer(name, bedrockPart -> new TextShowRender(this, textShow, currentGunItem)));
    }

    /** Prepares all stack-dependent visibility state before an immediate render or snapshot extraction. */
    private boolean prepareRenderState(ItemStack gunItem) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            return false;
        }
        currentGunItem = gunItem;
        currentExtendMagLevel = 0;
        adapterToRender.clear();
        // 更新配件物品的缓存，以供渲染使用
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                continue;
            }
            ItemStack attachmentItem = iGun.getAttachment(gunItem, type);
            if (attachmentItem.isEmpty()) {
                attachmentItem = iGun.getBuiltinAttachment(gunItem, type);
            }
            currentAttachmentItem.put(type, attachmentItem);
            IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentItem);
            if (attachment != null) {
                TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(attachmentItem)).ifPresent(index -> {
                    if (type == AttachmentType.EXTENDED_MAG) {
                        currentExtendMagLevel = index.getData().getExtendedMagLevel();
                    }
                    if (type == AttachmentType.SCOPE) {
                        renderMount = index.isShowMount();
                    }
                    if (index.getAdapterNodeName() != null) {
                        adapterToRender.add(index.getAdapterNodeName());
                    }
                });
            }
        }
        return true;
    }


    /**
     * Backend-neutral 26.2 submission path. Stack-dependent state is prepared before the parent
     * class freezes all part matrices into an immutable BedrockRenderSnapshot.
     */
    public void submit(PoseStack poseStack,
                       ItemStack gunItem,
                       ItemDisplayContext transformType,
                       SubmitNodeCollector collector,
                       RenderType renderType,
                       int light,
                       int overlay) {
        submit(poseStack, gunItem, transformType, collector, renderType, null, light, overlay);
    }

    /**
     * 带贴图参数的提交：枪身可能要在「开镜当帧」换成被目镜掩码裁剪的 RenderType，
     * 而裁剪版按贴图构造（见 {@code ScopeBodyRenderTypes#clipForViewmodel}）。
     *
     * @param gunTexture 枪身贴图；为 {@code null} 时不做镜内裁剪（回退旧行为）
     */
    public void submit(PoseStack poseStack,
                       ItemStack gunItem,
                       ItemDisplayContext transformType,
                       SubmitNodeCollector collector,
                       RenderType renderType,
                       @javax.annotation.Nullable net.minecraft.resources.Identifier gunTexture,
                       int light,
                       int overlay) {
        if (!prepareRenderState(gunItem)) {
            return;
        }
        if (laserBeamPaths != null) {
            BeamRenderer.renderLaserBeam(gunItem, poseStack, transformType, laserBeamPaths, collector);
        }

        // A6 baseline: submit the scope as normal geometry without raw-GL stencil clipping. This
        // keeps the scope body/ring/reticle visible on both backends; a PIP implementation can
        // replace this ordered fallback later without blocking the core gun model.
        ItemStack scope = currentAttachmentItem.get(AttachmentType.SCOPE);
        if (scopePosPath != null && scope != null && !scope.isEmpty()) {
            PoseStack scopePose = new PoseStack();
            scopePose.last().pose().set(poseStack.last().pose());
            scopePose.last().normal().set(poseStack.last().normal());
            for (BedrockPart part : scopePosPath) {
                part.translateAndRotateAndScale(scopePose);
            }
            AttachmentRender.submitAttachment(scope.copy(), gunItem.copy(), scopePose,
                    transformType, collector, light, overlay);
        }

        // 【镜内裁切 · 枪身】这一步必须排在瞄具提交之后：是否裁剪取决于
        // 「本帧有没有目镜几何登记进掩码清单」，而登记发生在上面瞄具提交的内部。
        // （调用方 wrapper 在调本方法之前就组好了 renderType，那时清单还是空的，
        //  所以裁剪判断只能在这里做，不能上提到 wrapper。）
        super.submit(poseStack, transformType, collector,
                com.tacz.guns.client.render.scope.ScopeBodyRenderTypes.clipForViewmodel(
                        renderType, gunTexture, transformType != null && transformType.firstPerson()),
                light, overlay);
    }


    @Nullable
    private IFunctionalRenderer ammoHiddenRender(BedrockPart bedrockPart, Predicate<IGun> predicate) {
        IGun iGun = IGun.getIGunOrNull(currentGunItem);
        if (iGun != null) {
            bedrockPart.visible = predicate.test(iGun);
        }
        return null;
    }

    @Nullable
    private IFunctionalRenderer scopeHiddenRender(BedrockPart bedrockPart, Predicate<ItemStack> predicate) {
        // 安装瞄具时可见
        ItemStack scopeItem = currentAttachmentItem.get(AttachmentType.SCOPE);
        bedrockPart.visible = predicate.test(scopeItem);
        return null;
    }

    @Nullable
    private IFunctionalRenderer extendedMagHiddenRender(BedrockPart bedrockPart, int level) {
        bedrockPart.visible = currentExtendMagLevel == level;
        return null;
    }

    @Override
    public AnimationListener supplyListeners(String nodeName, ObjectAnimationChannel.ChannelType type) {
        AnimationListener listener = super.supplyListeners(nodeName, type);
        if (listener == null) {
            return null;
        }
        if (nodeName.equals(MAG_ADDITIONAL_NODE)) {
            // 额外弹匣只有当动画中有它的关键帧的时候才渲染
            return new ModelAdditionalMagazineListener(listener, this);
        }
        return listener;
    }

    @Override
    public void cleanAnimationTransform() {
        super.cleanAnimationTransform();
        if (additionalMagazineNode != null) {
            additionalMagazineNode.visible = false;
        }
    }

    public EnumMap<AttachmentType, ItemStack> getCurrentAttachmentItem() {
        return currentAttachmentItem;
    }

    public ItemStack getCurrentGunItem() {
        return currentGunItem;
    }

    @Nullable
    public BedrockPart getAdditionalMagazineNode() {
        return additionalMagazineNode;
    }

    @Nullable
    public List<BedrockPart> getIronSightPath() {
        return ironSightPath;
    }

    @Nullable
    public List<BedrockPart> getIdleSightPath() {
        return idleSightPath;
    }

    @Nullable
    public List<BedrockPart> getThirdPersonHandOriginPath() {
        return thirdPersonHandOriginPath;
    }

    @Nullable
    public List<BedrockPart> getFixedOriginPath() {
        return fixedOriginPath;
    }

    @Nullable
    public List<BedrockPart> getGroundOriginPath() {
        return groundOriginPath;
    }

    @Nullable
    public List<BedrockPart> getMuzzleFlashPosPath() {
        return muzzleFlashPosPath;
    }

    // ─────────────────────────────────────────────────────────────────
    // 【几何自动推算枪口】模型里没有 muzzle_flash 骨骼时，从几何算出枪口
    //
    // 背景：默认枪包的 m320 / rpg7 等模型<b>没有</b>枪口骨骼（110 个枪模型里 6 个缺），
    // 上游代码遇到这种情况只能沿用"上一把枪的枪口偏移"（表现为弹丸从枪口上方飞出）。
    // 社区枪包同样会有这种模型，逐个人工补骨骼不可维护，所以改成让代码自己算。
    //
    // 约定（由默认枪包实测得出）：模型空间里<b>前方 = −Z</b>，16 像素 = 1 格。
    // 因此取"−Z 最靠前的那个立方体"的前端面中心作为枪口，再往 −Z 2 像素
    //（与 AK47 等自带 muzzle_flash 的模型惯例一致：muzzle_pos → muzzle_flash 正好差 2 像素）。
    //
    // ⚠ 初始化顺序：本类的 loadNewModel/loadLegacyModel 是在<b>父类构造体</b>里被调用的，
    //   那时本类的字段初始化式还没跑、root 也还没赋值，所以这里只<b>记录坐标</b>，
    //   真正建骨骼、挂到 root 上推迟到 getEffectiveMuzzlePath()（构造完成后）再做。
    //   下面三个字段刻意<b>不写初始化式</b>，就是这样才不会被"初始化式覆写"抹掉。
    // ─────────────────────────────────────────────────────────────────
    private float virtualMuzzleX;
    private float virtualMuzzleY;
    private float virtualMuzzleZ;
    /** 前端那块几何所属的骨骼名（虚拟枪口应当挂在它下面，才能与几何在同一坐标系里）。 */
    private @Nullable String virtualMuzzleBoneName;
    /** 虚拟枪口父骨骼的 JSON pivot（可能为 null = 没有父骨骼）。 */
    private @Nullable List<Float> virtualMuzzleParentPivot;
    private boolean hasVirtualMuzzlePoint;
    private boolean virtualMuzzleResolved;
    private @Nullable List<BedrockPart> virtualMuzzlePath;

    @Override
    protected void loadNewModel(com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO pojo) {
        super.loadNewModel(pojo);
        if (pojo.getGeometryModelNew() != null) {
            scanFrontMostPoint(pojo.getGeometryModelNew().getBones());
        }
    }

    @Override
    protected void loadLegacyModel(com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO pojo) {
        super.loadLegacyModel(pojo);
        if (pojo.getGeometryModelLegacy() != null) {
            scanFrontMostPoint(pojo.getGeometryModelLegacy().getBones());
        }
    }

    /** 扫描所有立方体，记录 −Z 最靠前那块的前端面中心（排除手/臂/弹匣这类"不是枪管"的骨骼）。 */
    private void scanFrontMostPoint(@Nullable List<com.tacz.guns.client.resource.pojo.model.BonesItem> bones) {
        if (bones == null) {
            return;
        }
        float bestZ = Float.MAX_VALUE;
        float bestX = 0.0f;
        float bestY = 0.0f;
        String bestBone = null;
        // 虚拟枪口的父骨骼（用于复刻加载器 BedrockModel#convertPivot 的坐标换算）
        List<Float> parentJsonPivot = null;
        for (com.tacz.guns.client.resource.pojo.model.BonesItem bone : bones) {
            String name = bone.getName() == null ? "" : bone.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("hand") || name.contains("arm") || name.contains("mag")) {
                continue;
            }
            if (bone.getCubes() == null) {
                continue;
            }
            for (com.tacz.guns.client.resource.pojo.model.CubesItem cube : bone.getCubes()) {
                List<Float> origin = cube.getOrigin();
                List<Float> size = cube.getSize();
                if (origin == null || size == null || origin.size() < 3 || size.size() < 3) {
                    continue;
                }
                float minZ = Math.min(origin.get(2), origin.get(2) + size.get(2));
                if (minZ < bestZ) {
                    bestZ = minZ;
                    bestX = origin.get(0) + size.get(0) / 2.0f;
                    bestY = origin.get(1) + size.get(1) / 2.0f;
                    bestBone = bone.getName();
                }
            }
        }
        if (bestZ != Float.MAX_VALUE) {
            this.virtualMuzzleX = bestX;
            this.virtualMuzzleY = bestY;
            this.virtualMuzzleZ = bestZ - 2.0f;
            this.virtualMuzzleBoneName = bestBone;
            this.virtualMuzzleParentPivot = parentJsonPivot;
            this.hasVirtualMuzzlePoint = true;
            // 找一个 pivot 为原点的辅助骨骼当父骨骼（与枪包自带枪口的写法一致），
            // 并把它的 JSON pivot 记下来 —— 下面要按加载器的公式做换算。
            for (String helper : new String[]{"positioning", "views"}) {
                for (com.tacz.guns.client.resource.pojo.model.BonesItem b : bones) {
                    if (helper.equals(b.getName()) && b.getPivot() != null && b.getPivot().size() >= 3) {
                        parentJsonPivot = b.getPivot();
                        break;
                    }
                }
                if (parentJsonPivot != null) {
                    break;
                }
            }
        }
    }

    /**
     * @return 实际可用的枪口路径：优先模型自带的 {@code muzzle_flash} 骨骼，
     *         没有则用几何推算出的虚拟枪口；两者都不行时返回 null。
     */
    @Nullable
    public List<BedrockPart> getEffectiveMuzzlePath() {
        // ⚠ 判据必须是「模型**真有** muzzle_flash 骨骼」（hasMuzzleFlashBone），
        // 绝不能写成「muzzleFlashPosPath != null」—— setFunctionalRenderer 会给缺失的节点
        // 造一个占位对象，于是它<b>永远非 null</b>，几何推算就永远不会被执行，
        // 结果是沿着占位对象算出垃圾坐标（实测：弹丸从头顶上方飞出）。
        if (hasMuzzleFlashBone()) {
            return muzzleFlashPosPath;
        }
        if (virtualMuzzleResolved) {
            return virtualMuzzlePath;
        }
        virtualMuzzleResolved = true;
        if (!hasVirtualMuzzlePoint) {
            return null;
        }
        // 【父骨骼的选择 —— 这里踩过两次坑，结论是"必须挂在 pivot 为原点的辅助骨骼上"】
        //
        // 参照物是枪包自带枪口的写法：AK47 的 muzzle_flash 挂在 positioning2 下，
        // 而 positioning2 的 pivot 正是 [0, 0, 0]。此前手工给 M320 / RPG7 补骨骼时
        // （已实测位置正确）用的也是这种父骨骼：
        //     M320: parent = views        (pivot [0,0,0])  pivot = (-0.0608, 9.875, -10.75)
        //     RPG7: parent = positioning  (pivot [0,0,0])  pivot = (-0.2048, 5.4531, -24.5)
        //
        // 为什么必须是原点 pivot：挂到 pivot 非原点的骨骼（例如几何所在的那个骨骼）时，
        // 子骨骼的坐标到底按"绝对模型坐标"还是"相对父骨骼枢轴"解释会有歧义，
        // 实测会差出一层坐标系（枪口跑到眼睛上方 0.65 格）。挂在原点骨骼上，
        // 两种解释完全等价 —— 于是行为可预测，也与枪包惯例一致。
        BedrockPart parent = null;
        float parentPivotX = 0.0f;
        float parentPivotY = 0.0f;
        float parentPivotZ = 0.0f;
        for (String helper : new String[]{"positioning", "views", "root"}) {
            ModelRendererWrapper wrapper = modelMap.get(helper);
            if (wrapper != null) {
                parent = wrapper.getModelRenderer();
                break;
            }
        }
        if (parent == null) {
            parent = root;
        }
        if (parent != null) {
            // 父骨骼不是原点 pivot 时，子骨骼坐标按其相对量给（与父骨骼同为原点时等价于绝对值）
            parentPivotX = parent.x;
            parentPivotY = parent.y;
            parentPivotZ = parent.z;
        }
        // 【坐标换算 —— 复刻 BedrockModel#convertPivot】
        // 加载器读 JSON 骨骼时的规则是：
        //   有父骨骼：X/Z 用「子 − 父」，Y 用「父 − 子」
        //   无父骨骼：X/Z 不变，Y 用「24 − 子」  （24 = 渲染原点，就是那句 translate(0, 1.5)）
        // 我的虚拟骨骼是运行时建的、不走加载器，所以必须自己按同一公式换算 ——
        // 之前漏了这一步，Y 的符号是反的，于是枪口跑到眼睛上方 0.65 格。
        List<Float> pp = virtualMuzzleParentPivot;
        float px;
        float py;
        float pz;
        if (pp != null && pp.size() >= 3) {
            px = virtualMuzzleX - pp.get(0);
            py = pp.get(1) - virtualMuzzleY;
            pz = virtualMuzzleZ - pp.get(2);
        } else {
            px = virtualMuzzleX;
            py = 24.0f - virtualMuzzleY;
            pz = virtualMuzzleZ;
        }
        BedrockPart part = new BedrockPart("tacz_virtual_muzzle");
        part.setPos(px, py, pz);
        if (parent != null) {
            parent.addChild(part);
        }
        virtualMuzzlePath = getPath(new ModelRendererWrapper(part));
        return virtualMuzzlePath;
    }

    private @Nullable List<BedrockPart> alternateMuzzlePath;
    private boolean alternateMuzzleResolved;

    /**
     * 诊断用：另一种候选（挂在 {@code root} 下）的路径。
     *
     * <p>只用于对比日志 —— 一次性构建并缓存，绝不能每次调用都 {@code addChild}
     * （那会往模型树里不断塞节点）。</p>
     */
    @Nullable
    public List<BedrockPart> getAlternateMuzzlePath() {
        if (hasMuzzleFlashBone() || !hasVirtualMuzzlePoint || root == null) {
            return null;
        }
        if (alternateMuzzleResolved) {
            return alternateMuzzlePath;
        }
        alternateMuzzleResolved = true;
        BedrockPart part = new BedrockPart("tacz_virtual_muzzle_alt");
        part.setPos(virtualMuzzleX, virtualMuzzleY, virtualMuzzleZ);
        root.addChild(part);
        alternateMuzzlePath = getPath(new ModelRendererWrapper(part));
        return alternateMuzzlePath;
    }

    /** @return 本次枪口是否来自几何推算（仅用于日志/诊断）。 */
    public boolean isMuzzleFromGeometry() {
        return !hasMuzzleFlashBone() && hasVirtualMuzzlePoint;
    }

    /**
     * @return 模型 JSON 里是否<b>真的</b>有 {@code muzzle_flash} 骨骼。
     *
     * <p>不能只看 {@link #getMuzzleFlashPosPath()} 是否为 null：{@code setFunctionalRenderer}
     * 会为缺失的节点造占位对象（m320 / rpg7 就是这样），路径永远非 null，而沿着占位对象
     * 算出来的坐标是垃圾 —— 实测枪口跑到眼睛上方约一格，榴弹就从那里飞出去。</p>
     */
    public boolean hasMuzzleFlashBone() {
        return hasRealBone(GunModelConstant.MUZZLE_FLASH_ORIGIN_NODE);
    }

    @Nullable
    public List<BedrockPart> getScopePosPath() {
        return scopePosPath;
    }

    @Nullable
    public List<BedrockPart> getRefitAttachmentViewPath(AttachmentType type) {
        return refitAttachmentViewPath.get(type);
    }

    @Nullable
    public ShellRender getShellRender(int index) {
        if (index < 0 || index >= shellRenderList.size()) {
            return null;
        }
        return shellRenderList.get(index);
    }

    @Nullable
    public BedrockPart getRootNode() {
        return root;
    }

    public boolean getRenderHand() {
        return renderHand;
    }

    public void setRenderHand(boolean renderHand) {
        this.renderHand = renderHand;
    }
}
