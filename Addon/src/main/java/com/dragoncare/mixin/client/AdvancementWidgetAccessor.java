package com.dragoncare.mixin.client;

import net.minecraft.client.gui.screen.advancement.AdvancementWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvancementWidget.class)
public interface AdvancementWidgetAccessor {
    @Accessor("x")
    int getX();

    @Accessor("y")
    int getY();

    @org.spongepowered.asm.mixin.Mutable
    @Accessor("y")
    void setY(int y);
}
