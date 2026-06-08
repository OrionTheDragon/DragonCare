package com.dragoncare.mixin.client;

import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.advancement.AdvancementTab;
import net.minecraft.client.gui.screen.advancement.AdvancementWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(AdvancementTab.class)
public abstract class AdvancementTabMixin {

    @Shadow @Final private Map<AdvancementEntry, AdvancementWidget> widgets;
    @Shadow private double originX;
    @Shadow private double originY;

    @org.spongepowered.asm.mixin.Unique
    private boolean dragoncare$layoutFixed = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void dragoncare$onRenderHead(DrawContext context, int x, int y, CallbackInfo ci) {
        if (!this.dragoncare$layoutFixed) {
            this.dragoncare$layoutFixed = true;
            
            AdvancementWidgetAccessor fireAcc = null;
            AdvancementWidgetAccessor iceAcc = null;
            AdvancementWidgetAccessor lightningAcc = null;
            AdvancementWidgetAccessor threeAcc = null;
            AdvancementWidgetAccessor dracoAcc = null;
            
            for (Map.Entry<AdvancementEntry, AdvancementWidget> entry : this.widgets.entrySet()) {
                String path = entry.getKey().id().getPath();
                if ("tamed_fire_dragon".equals(path)) fireAcc = (AdvancementWidgetAccessor) entry.getValue();
                else if ("tamed_ice_dragon".equals(path)) iceAcc = (AdvancementWidgetAccessor) entry.getValue();
                else if ("tamed_lightning_dragon".equals(path)) lightningAcc = (AdvancementWidgetAccessor) entry.getValue();
                else if ("tamed_three_elements".equals(path)) threeAcc = (AdvancementWidgetAccessor) entry.getValue();
                else if ("dracomania".equals(path)) dracoAcc = (AdvancementWidgetAccessor) entry.getValue();
            }
            
            if (fireAcc != null && iceAcc != null && lightningAcc != null) {
                int y1 = fireAcc.getY();
                int y2 = iceAcc.getY();
                int y3 = lightningAcc.getY();
                
                int minY = Math.min(y1, Math.min(y2, y3));
                int maxY = Math.max(y1, Math.max(y2, y3));
                int midY = y1 + y2 + y3 - minY - maxY;
                
                fireAcc.setY(minY);
                iceAcc.setY(midY);
                lightningAcc.setY(maxY);
                
                if (threeAcc != null) threeAcc.setY(midY);
                if (dracoAcc != null) dracoAcc.setY(midY);
            }
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/advancement/AdvancementWidget;renderWidgets(Lnet/minecraft/client/gui/DrawContext;II)V"))
    private void dragoncare$onRender(DrawContext context, int x, int y, CallbackInfo ci) {
        AdvancementWidget fireWidget = null;
        AdvancementWidget lightningWidget = null;
        AdvancementWidget threeWidget = null;

        for (Map.Entry<AdvancementEntry, AdvancementWidget> entry : this.widgets.entrySet()) {
            String path = entry.getKey().id().getPath();
            if ("tamed_fire_dragon".equals(path)) {
                fireWidget = entry.getValue();
            } else if ("tamed_lightning_dragon".equals(path)) {
                lightningWidget = entry.getValue();
            } else if ("tamed_three_elements".equals(path)) {
                threeWidget = entry.getValue();
            }
        }

        if (threeWidget != null) {
            if (fireWidget != null) {
                dragoncare$drawConnection(context, fireWidget, threeWidget, true);
            }
            if (lightningWidget != null) {
                dragoncare$drawConnection(context, lightningWidget, threeWidget, true);
            }

            if (fireWidget != null) {
                dragoncare$drawConnection(context, fireWidget, threeWidget, false);
            }
            if (lightningWidget != null) {
                dragoncare$drawConnection(context, lightningWidget, threeWidget, false);
            }
        }
    }

    private void dragoncare$drawConnection(DrawContext context, AdvancementWidget from, AdvancementWidget to, boolean border) {
        AdvancementWidgetAccessor fromAcc = (AdvancementWidgetAccessor) from;
        AdvancementWidgetAccessor toAcc = (AdvancementWidgetAccessor) to;

        int orgX = net.minecraft.util.math.MathHelper.floor(this.originX);
        int orgY = net.minecraft.util.math.MathHelper.floor(this.originY);

        int startX = orgX + fromAcc.getX() + 13;
        int startY = orgY + fromAcc.getY() + 13;
        int endY = orgY + toAcc.getY() + 13;
        
        int midX = orgX + fromAcc.getX() + 30;

        int color = border ? -16777216 : -1;

        if (border) {
            context.drawHorizontalLine(startX, midX, startY - 1, color);
            context.drawHorizontalLine(startX, midX + 1, startY, color);
            context.drawHorizontalLine(startX, midX, startY + 1, color);

            if (startY < endY) {
                context.drawVerticalLine(midX - 1, startY, endY - 1, color);
                context.drawVerticalLine(midX + 1, startY, endY - 1, color);
            } else if (startY > endY) {
                context.drawVerticalLine(midX - 1, endY + 1, startY, color);
                context.drawVerticalLine(midX + 1, endY + 1, startY, color);
            }
        } else {
            context.drawHorizontalLine(startX, midX, startY, color);
            
            if (startY < endY) {
                context.drawVerticalLine(midX, startY, endY, color);
            } else if (startY > endY) {
                context.drawVerticalLine(midX, endY, startY, color);
            }
        }
    }
}
