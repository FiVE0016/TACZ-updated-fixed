package com.tacz.guns.crafting.ingredient;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;
import java.util.function.Predicate;

/**
 * One gun smith table material, independent of any mod loader.
 *
 * <p>Vanilla 26.2 {@link Ingredient} is a final holder set and cannot require NBT. The gun smith table only
 * needs to test stacks and show them, so instead of loader-specific custom ingredients it uses this
 * TACZ-internal abstraction:
 * <ul>
 *   <li>{@link VanillaTaczIngredient}: a vanilla {@link Ingredient} (item, item list or {@code #tag});</li>
 *   <li>{@link NbtTaczIngredient}: items that must carry a required {@code CUSTOM_DATA}.</li>
 * </ul>
 * The interface is sealed so that {@link #CODEC} and {@link #STREAM_CODEC} cover every implementation.
 */
public sealed interface TaczIngredient extends Predicate<ItemStack> permits VanillaTaczIngredient, NbtTaczIngredient {
    /**
     * JSON form: a vanilla ingredient (item id, id list or {@code "#tag"}) or an {@link NbtTaczIngredient} object.
     * Legacy gun pack formats are first rewritten into this form by {@code GunSmithTableIngredient#normalizeLegacy}.
     */
    Codec<TaczIngredient> CODEC = Codec.either(Ingredient.CODEC, NbtTaczIngredient.CODEC)
            .xmap(TaczIngredient::fromEither, TaczIngredient::toEither);

    /**
     * Network form: a boolean discriminator, then either {@link Ingredient#CONTENTS_STREAM_CODEC}
     * or {@link NbtTaczIngredient#STREAM_CODEC}.
     */
    StreamCodec<RegistryFriendlyByteBuf, TaczIngredient> STREAM_CODEC =
            ByteBufCodecs.either(Ingredient.CONTENTS_STREAM_CODEC, NbtTaczIngredient.STREAM_CODEC)
                    .map(TaczIngredient::fromEither, TaczIngredient::toEither);

    /**
     * Whether the stack is this material. The required count is checked by the caller.
     */
    @Override
    boolean test(ItemStack stack);

    /**
     * The stacks shown for this material in the gun smith table screen and in JEI.
     * NBT materials return stacks carrying the required {@code CUSTOM_DATA}, so guns and attachments
     * render as the item that is actually required.
     *
     * @param context slot display context, e.g. {@code SlotDisplayContext.fromLevel(level)};
     *                vanilla item tags are resolved through it
     */
    List<ItemStack> getDisplayStacks(ContextMap context);

    private static TaczIngredient fromEither(Either<Ingredient, NbtTaczIngredient> either) {
        return either.<TaczIngredient>map(VanillaTaczIngredient::new, nbt -> nbt);
    }

    private static Either<Ingredient, NbtTaczIngredient> toEither(TaczIngredient ingredient) {
        return switch (ingredient) {
            case VanillaTaczIngredient vanilla -> Either.left(vanilla.ingredient());
            case NbtTaczIngredient nbt -> Either.right(nbt);
        };
    }
}
