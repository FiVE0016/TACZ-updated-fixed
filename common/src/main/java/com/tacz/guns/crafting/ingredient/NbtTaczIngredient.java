package com.tacz.guns.crafting.ingredient;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.GunMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A material that must be one of {@link #items} carrying the required {@code CUSTOM_DATA}.
 *
 * <p>Covers the custom ingredient types gun packs use: {@code forge:partial_nbt} and {@code forge:nbt}
 * (Forge 1.20.1 packs) and {@code tacz:nbt} (upstream TACZ 1.21.1+, also what the community TaCZPackUpgrader
 * converts the Forge types into). {@code GunSmithTableIngredient#normalizeLegacy} rewrites all of them into
 * the JSON form read by {@link #CODEC}:
 * <pre>{"type": "tacz:nbt", "items": ["tacz:modern_kinetic_gun"], "nbt": {"GunId": "hamster:coltm1892"}, "partial": true}</pre>
 *
 * <h2>Matching</h2>
 * {@code partial=true} is a subset match ({@link CustomData#matchedBy}, i.e. {@code forge:partial_nbt});
 * {@code partial=false} requires the stack's {@code CUSTOM_DATA} to equal {@code nbt} exactly
 * (i.e. {@code forge:nbt}). A stack without {@code CUSTOM_DATA} never matches.
 */
public final class NbtTaczIngredient implements TaczIngredient {
    public static final Identifier TYPE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "nbt");

    public static final Codec<NbtTaczIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.validate(NbtTaczIngredient::checkType).fieldOf("type").forGetter(ingredient -> TYPE),
            ExtraCodecs.nonEmptyList(BuiltInRegistries.ITEM.byNameCodec().listOf()).fieldOf("items")
                    .forGetter(ingredient -> List.copyOf(ingredient.items)),
            CustomData.COMPOUND_TAG_CODEC.fieldOf("nbt").forGetter(ingredient -> ingredient.nbt),
            Codec.BOOL.optionalFieldOf("partial", false).forGetter(ingredient -> ingredient.partial)
    ).apply(instance, (type, items, nbt, partial) -> new NbtTaczIngredient(items, nbt, partial)));

    public static final StreamCodec<RegistryFriendlyByteBuf, NbtTaczIngredient> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.ITEM).apply(ByteBufCodecs.list()),
            ingredient -> List.copyOf(ingredient.items),
            ByteBufCodecs.TRUSTED_COMPOUND_TAG,
            ingredient -> ingredient.nbt,
            ByteBufCodecs.BOOL,
            ingredient -> ingredient.partial,
            (items, nbt, partial) -> new NbtTaczIngredient(items, nbt, partial)
    );

    private final Set<Item> items;
    private final CompoundTag nbt;
    private final boolean partial;
    /** {@link #nbt} as a component: compared against in strict mode and put on the display stacks. */
    private final CustomData requiredData;

    public NbtTaczIngredient(Collection<Item> items, CompoundTag nbt, boolean partial) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Cannot create an NbtTaczIngredient with no items");
        }
        // Insertion order is kept so the material slot cycles through the items in the order the pack lists them.
        this.items = Collections.unmodifiableSet(new LinkedHashSet<>(items));
        this.nbt = nbt.copy();
        this.partial = partial;
        this.requiredData = CustomData.of(this.nbt);
    }

    @Override
    public boolean test(ItemStack stack) {
        if (!this.items.contains(stack.getItem())) {
            return false;
        }
        // 26.2: 使用 CUSTOM_DATA component 进行 NBT 匹配
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }
        return this.partial ? customData.matchedBy(this.nbt) : customData.equals(this.requiredData);
    }

    /**
     * 让材料格显示<b>带上要求 NBT 的</b>物品，而不是光秃秃的基础物品。
     *
     * <p>只给裸物品的话，对 TACZ 而言就是一把「空枪 ID」的 {@code tacz:modern_kinetic_gun}，
     * 图标是缺省模型、名字也不对，玩家根本看不出要交什么。
     *
     * <p>这里把 {@code nbt} 塞进 {@code CUSTOM_DATA} 再交给显示层，
     * 于是材料格会正确渲染成「柯尔特 M1892」本身。
     * 这只影响<b>显示</b>，匹配逻辑仍由 {@link #test} 负责，两者互不干扰。
     */
    @Override
    public List<ItemStack> getDisplayStacks(ContextMap context) {
        return this.items.stream().map(item -> {
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponents.CUSTOM_DATA, this.requiredData);
            return stack;
        }).toList();
    }

    private static DataResult<Identifier> checkType(Identifier type) {
        return TYPE.equals(type)
                ? DataResult.success(type)
                : DataResult.error(() -> "Unsupported gun smith table ingredient type: " + type);
    }
}
