package com.dragoncare.dragonphone.client;

import com.dragoncare.DragonCare;
import com.dragoncare.dragonphone.net.PhoneHudPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.Locale;

/**
 * HUD-оверлей «Драконьего телефона»: пока выбран дракон и телефон активен,
 * сверху по центру экрана отображается имя/вид, координаты и расстояние.
 *
 * <p>Данные приходят с сервера пакетом {@link PhoneHudPayload} через
 * {@link ClientPhoneHudState}; рендерер только отрисовывает и считает дистанцию
 * до игрока (для real-time индикатора).</p>
 */
@EventBusSubscriber(modid = DragonCare.MOD_ID, value = Dist.CLIENT)
public final class PhoneHudOverlay {

    private PhoneHudOverlay() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        PhoneHudPayload p = ClientPhoneHudState.get();
        if (p == null || !p.active()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (player == null) return;

        DrawContext ctx = event.getGuiGraphics();
        TextRenderer tr = mc.textRenderer;

        int screenW = mc.getWindow().getScaledWidth();
        int top = 4;

        // Линия 1: имя / вид (золотом).
        String name = p.hasCustomName() && p.customName() != null && !p.customName().isEmpty()
                ? p.customName()
                : (p.species() != null && !p.species().isEmpty()
                    ? Text.translatable(p.species()).getString()
                    : "Dragon");
        Text title = Text.translatable("hud.dragoncare.dragon_phone.tracking", name)
                .formatted(Formatting.GOLD);

        // Линия 2: координаты + измерение + дистанция.
        double dist;
        boolean sameDim = player.getWorld().getRegistryKey().getValue().equals(p.dimension());
        if (sameDim) {
            dist = player.getPos().distanceTo(new Vec3d(p.x(), p.y(), p.z()));
        } else {
            dist = Double.POSITIVE_INFINITY;
        }

        String coords = String.format(Locale.ROOT, "[%d, %d, %d] (%s)",
                (int) p.x(), (int) p.y(), (int) p.z(), shortDim(p.dimension().toString()));
        Text info = Text.literal(coords).formatted(Formatting.WHITE)
                .append(Text.literal("  " + formatDistance(dist)).formatted(Formatting.AQUA));

        int titleW = tr.getWidth(title);
        int infoW = tr.getWidth(info);

        // Полупрозрачная подложка для читаемости.
        int pad = 3;
        int boxW = Math.max(titleW, infoW) + pad * 2;
        int boxH = tr.fontHeight * 2 + 4 + pad * 2;
        int boxX = (screenW - boxW) / 2;
        int boxY = top;
        ctx.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0x88000000);

        ctx.drawTextWithShadow(tr, title, (screenW - titleW) / 2, boxY + pad, 0xFFFFFFFF);
        ctx.drawTextWithShadow(tr, info,  (screenW - infoW)  / 2, boxY + pad + tr.fontHeight + 4, 0xFFFFFFFF);
    }

    private static String shortDim(String dim) {
        int sl = dim.indexOf(':');
        return sl >= 0 ? dim.substring(sl + 1) : dim;
    }

    private static String formatDistance(double dist) {
        if (Double.isInfinite(dist) || Double.isNaN(dist)) return "∞";
        if (dist >= 1000.0) return String.format(Locale.ROOT, "%.1fkm", dist / 1000.0);
        return String.format(Locale.ROOT, "%dm", (int) Math.round(dist));
    }
}
