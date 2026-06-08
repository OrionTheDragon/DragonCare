package com.dragoncare.mixin.client;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.iafenvoy.iceandfire.screen.menu.DragonMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the private {@code dragon} field of CE's {@link DragonMenu}. */
@Mixin(DragonMenu.class)
public interface DragonScreenHandlerAccessor {

    @Accessor(value = "dragon", remap = false)
    DragonBaseEntity dragoncare$getDragon();
}
