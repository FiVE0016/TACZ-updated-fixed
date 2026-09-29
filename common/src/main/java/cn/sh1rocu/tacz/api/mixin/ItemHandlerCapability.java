package cn.sh1rocu.tacz.api.mixin;

import cn.sh1rocu.tacz.util.forge.LazyOptional;
import cn.sh1rocu.tacz.util.itemhandler.IItemHandler;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Item handlers mixed into {@code LivingEntity} (and specialised for players and horses).
 * <p>
 * Cast an entity to this interface to use it; the mixin adds the interface at runtime on every loader.
 */
public interface ItemHandlerCapability {

    default LazyOptional<IItemHandler> tacz$getItemHandler(@Nullable Direction facing) {
        return LazyOptional.empty();
    }

    default void tacz$invalidateItemHandler() {
    }

    default void tacz$reviveItemHandler() {
    }

    /**
     * The generic living-entity equipment handlers, which subclasses fall back to.
     */
    default LazyOptional<IItemHandler> tacz$getEquipmentItemHandler(@Nullable Direction facing) {
        return LazyOptional.empty();
    }

    /**
     * Invalidates only the generic living-entity equipment handlers.
     */
    default void tacz$invalidateEquipmentItemHandlers() {
    }
}
