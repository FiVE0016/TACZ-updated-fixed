package com.tacz.guns.crafting.ingredient;

import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * A plain vanilla {@link Ingredient}: a single item, an item list or an item tag.
 */
public record VanillaTaczIngredient(Ingredient ingredient) implements TaczIngredient {
    @Override
    public boolean test(ItemStack stack) {
        return this.ingredient.test(stack);
    }

    @Override
    public List<ItemStack> getDisplayStacks(ContextMap context) {
        return this.ingredient.display().resolveForStacks(context);
    }
}
