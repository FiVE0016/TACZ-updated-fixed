package com.tacz.guns.mixin.accessor;

import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SelectItemModelProperties.class)
public interface SelectItemModelPropertiesAccessor {
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier, SelectItemModelProperty.Type<?, ?>> tacz$getIdMapper() {
        throw new AssertionError();
    }
}
