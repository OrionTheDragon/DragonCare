package com.dragoncare.mixin.client;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.iafenvoy.iceandfire.screen.gui.DragonScreen;
import com.iafenvoy.iceandfire.screen.menu.DragonMenu;
import com.dragoncare.client.ClientBondCache;
import com.dragoncare.taming.BondManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws an affection-bond bar above the dragon preview in CE's dragon stats screen.
 * The bar shows the dragon's current bond level (0..5) and its progress toward the
 * next level. Bond data is supplied by {@link ClientBondCache}, populated via
 * {@code BondSyncPayload} packets.
 */
@Mixin(DragonScreen.class)
public abstract class DragonScreenMixin extends HandledScreen<DragonMenu> {

    private DragonScreenMixin() {
        super(null, null, null);
    }

    @Inject(method = "drawBackground", at = @At("TAIL"))
    private void dragoncare$drawBondBar(DrawContext ctx, float partialTicks, int mouseX, int mouseY,
                                             CallbackInfo ci) {
        DragonBaseEntity dragon = ((DragonScreenHandlerAccessor) this.handler).dragoncare$getDragon();
        if (dragon == null || !dragon.isTamed()) return;

        int points = ClientBondCache.get(dragon.getUuid());
        int level = BondManager.getLevel(points);
        int progress = BondManager.progressInLevel(points);
        int need = BondManager.needForNextLevel(points);

        int k = (this.width - this.backgroundWidth) / 2;
        int l = (this.height - this.backgroundHeight) / 2;

        int barX = k + 45;
        int barY = l + 18;
        int barW = 86;
        int barH = 6;

        // Black border + dark fill
        ctx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF000000);
        ctx.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);

        // Filled portion
        float fraction = (level >= BondManager.MAX_LEVEL) ? 1f
                : (need <= 0 ? 0f : Math.min(1f, (float) progress / (float) need));
        int fillW = Math.round(barW * fraction);
        if (fillW > 0) {
            int color = (level >= BondManager.MAX_LEVEL) ? 0xFFE040FB : 0xFFFFAA00;
            ctx.fill(barX, barY, barX + fillW, barY + barH, color);
        }

        // Label centered above the bar
        Text label;
        if (level >= BondManager.MAX_LEVEL) {
            label = Text.translatable("dragoncare.bond.label_max", level)
                    .formatted(Formatting.LIGHT_PURPLE);
        } else {
            label = Text.translatable("dragoncare.bond.label", level, progress, need)
                    .formatted(Formatting.GOLD);
        }
        int labelW = this.textRenderer.getWidth(label);
        ctx.drawText(this.textRenderer, label,
                k + (this.backgroundWidth - labelW) / 2, barY - 9, 0xFFFFFFFF, true);
    }
}
