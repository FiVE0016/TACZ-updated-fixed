package cn.sh1rocu.tacz.compat.rei.display;

import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.ingredient.TaczIngredient;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class GunSmithTableDisplay extends BasicDisplay {
    private final GunSmithTableRecipe recipe;
    private final Map.Entry<Identifier, CategoryIdentifier<GunSmithTableDisplay>> entry;

    public GunSmithTableDisplay(GunSmithTableRecipe recipe, Map.Entry<Identifier, CategoryIdentifier<GunSmithTableDisplay>> entry) {
        super(recipe.getInputs().stream().map(GunSmithTableDisplay::toEntryIngredient).toList(),
                Collections.singletonList(EntryIngredients.of(recipe.getOutput())), Optional.ofNullable(entry.getKey()));
        this.recipe = recipe;
        this.entry = entry;
    }

    /**
     * TACZ ingredients can require NBT that vanilla ingredients cannot express, so REI gets their display stacks.
     */
    private static EntryIngredient toEntryIngredient(GunSmithTableIngredient ingredient) {
        TaczIngredient resolved = ingredient.getIngredient();
        if (resolved == null) {
            return EntryIngredient.empty();
        }
        return EntryIngredients.ofItemStacks(resolved.getDisplayStacks(EntryIngredients.slotDisplayContext()));
    }

    public GunSmithTableRecipe getRecipe() {
        return recipe;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return entry.getValue();
    }


    @Override
    public DisplaySerializer<? extends GunSmithTableDisplay> getSerializer() {
        return null;
    }
}
