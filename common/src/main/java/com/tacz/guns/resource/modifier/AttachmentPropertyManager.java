package com.tacz.guns.resource.modifier;

import com.google.common.collect.Maps;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.event.ChangeGunPropertyEvent;
import com.tacz.guns.resource.modifier.custom.*;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.MathLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.luaj.vm2.compiler.LuaC;

public class AttachmentPropertyManager {
    /**
     * luaj core globals. Deliberately loads ONLY BaseLib / MathLib / TableLib / StringLib:
     * no IoLib, no OsLib, no LuajavaLib, so gun-pack scripts cannot reach the filesystem.
     */
    private static final Globals LUA_GLOBALS = createLuaGlobals();

    private static Globals createLuaGlobals() {
        Globals globals = new Globals();
        globals.STDOUT = System.out;
        globals.load(new BaseLib());
        globals.load(new MathLib());
        globals.load(new TableLib());
        globals.load(new StringLib());
                // 26.3: luaj looks the compiler up by a hard-coded class-name string
        // ("org.luaj.vm2.compiler.LuaC"), which the relocation does not rewrite.
        // Assign it through a real import instead, so the relocated class is used.
                // luaj resolves its compiler lazily via a hard-coded class-name string,
        // which relocation does not rewrite -> install it explicitly instead.
        // (LuaC() is protected, so LuaC.install(...) is the way in.)
        LuaC.install(globals);

return globals;
    }
    private static final Map<String, IAttachmentModifier<?, ?>> MODIFIERS = Maps.newLinkedHashMap();

    public static void registerModifier() {
        MODIFIERS.put(AdsModifier.ID, new AdsModifier());
        MODIFIERS.put(AmmoSpeedModifier.ID, new AmmoSpeedModifier());
        MODIFIERS.put(ArmorIgnoreModifier.ID, new ArmorIgnoreModifier());
        MODIFIERS.put(DamageModifier.ID, new DamageModifier());
        MODIFIERS.put(EffectiveRangeModifier.ID, new EffectiveRangeModifier());
        MODIFIERS.put(ExplosionModifier.ID, new ExplosionModifier());
        MODIFIERS.put(HeadShotModifier.ID, new HeadShotModifier());
        MODIFIERS.put(IgniteModifier.ID, new IgniteModifier());
        MODIFIERS.put(InaccuracyModifier.ID, new InaccuracyModifier());
        MODIFIERS.put(KnockbackModifier.ID, new KnockbackModifier());
        MODIFIERS.put(PierceModifier.ID, new PierceModifier());
        MODIFIERS.put(RecoilModifier.ID, new RecoilModifier());
        MODIFIERS.put(RpmModifier.ID, new RpmModifier());
        MODIFIERS.put(SilenceModifier.ID, new SilenceModifier());
        MODIFIERS.put(WeightModifier.ID, new WeightModifier());
        MODIFIERS.put(ExtraMovementModifier.ID, new ExtraMovementModifier());
    }

    public static Map<String, IAttachmentModifier<?, ?>> getModifiers() {
        return MODIFIERS;
    }

    public static void postChangeEvent(LivingEntity shooter, ItemStack gunItem) {
        if (!(gunItem.getItem() instanceof IGun iGun)) {
            return;
        }
        Identifier gunId = iGun.getGunId(gunItem);
        TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> {
            AttachmentCacheProperty cacheProperty = new AttachmentCacheProperty();
            // 发布事件
            AttachmentPropertyEvent event = new AttachmentPropertyEvent(gunItem, cacheProperty);
            ChangeGunPropertyEvent.internalOnAttachmentPropertyEvent(event);
            event.postEventToKubeJS(event);
            AttachmentPropertyEvent.CALLBACK.invoker().post(event);
            // 让脚本更新缓存
            IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
            ShooterDataHolder dataHolder = operator.getDataHolder();
            GunProperties.allCacheModifiableByScript().forEach((id, property) -> {
                // noinspection rawtypes,unchecked
                iGun.modifyProperty(dataHolder, gunItem, shooter, "modify_cached_property", property.name(), (Class) property.type(), cacheProperty.getCache(property));
            });
            // 更新实体的缓存对象
            operator.updateCacheProperty(cacheProperty);
        });
    }

    public static double eval(Modifier modifier, double defaultValue) {
        return eval(Collections.singletonList(modifier), defaultValue);
    }

    public static double eval(List<Modifier> modifiers, double defaultValue) {
        double addend = defaultValue;
        double percent = 1;
        double multiplier = 1;
        for (Modifier modifier : modifiers) {
            addend += modifier.getAddend();
            percent += modifier.getPercent();
            multiplier *= Math.max(modifier.getMultiplier(), 0f);
        }
        percent = Math.max(percent, 0f);
        double value = addend * percent * multiplier;
        for (Modifier modifier : modifiers) {
            String function = modifier.getFunction();
            if (StringUtils.isEmpty(function)) {
                continue;
            }
            value = functionEval(value, defaultValue, function);
        }
        return value;
    }

    public static boolean eval(List<Boolean> modified, boolean defaultValue) {
        if (defaultValue) {
            // 如果默认值为 true，那么只要有一个 false 就返回 false
            return modified.stream().allMatch(s -> s);
        } else {
            // 如果默认值为 false，那么只要有一个 true 就返回 true
            return modified.stream().anyMatch(s -> s);
        }
    }

    public static synchronized double functionEval(double value, double defaultValue, String script) {
        script = script.toLowerCase(Locale.ENGLISH);
        try {
            LUA_GLOBALS.set("x", LuaValue.valueOf(value));
            LUA_GLOBALS.set("r", LuaValue.valueOf(defaultValue));
            LuaValue chunk = LUA_GLOBALS.load(script);
            chunk.call();
            LuaValue result = LUA_GLOBALS.get("y");
            if (result.isnumber()) {
                return result.todouble();
            }
        } catch (Exception e) {
            GunMod.LOGGER.error(e.getMessage(), e);
        }
        return value;
    }
}
