package com.dragoncare.dragonphone.client;

import com.dragoncare.dragonphone.net.DragonInfo;
import com.dragoncare.dragonphone.net.RefreshListPayload;
import com.dragoncare.dragonphone.net.SelectDragonPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * UI «Драконьего телефона»: сверху — текущая позиция выбранного дракона,
 * затем кнопки сортировки + обновления, ниже скроллируемый список с
 * порядковым номером, именем, расстоянием и стадией роста.
 *
 * <p>Все данные обновляются по нажатию «Обновить» (запрос на сервер) —
 * это снижает трафик. Выбор дракона отправляется на сервер немедленно.</p>
 */
public class DragonPhoneScreen extends Screen {

    private static final int ROW_HEIGHT = 14;
    private static final int LIST_TOP = 70;
    private static final int LIST_BOTTOM_PADDING = 16;

    /** Возможные режимы сортировки. */
    public enum SortMode {
        NAME,
        STAGE,
        DISTANCE_ASC,
        DISTANCE_DESC
    }

    private List<DragonInfo> dragons;
    @Nullable
    private UUID selected;

    private SortMode sort = SortMode.DISTANCE_ASC;
    private double scrollY;

    public DragonPhoneScreen(List<DragonInfo> dragons, @Nullable UUID selected) {
        super(Text.translatable("screen.dragoncare.dragon_phone.title"));
        this.dragons = new ArrayList<>(dragons);
        this.selected = selected;
    }

    public void updateData(List<DragonInfo> dragons, @Nullable UUID selected) {
        this.dragons = new ArrayList<>(dragons);
        this.selected = selected;
    }

    @Override
    protected void init() {
        // Кнопки сортировки + обновления — горизонтальный ряд под заголовком.
        int y = 36;
        int x = 16;
        int btnW = 78;
        int btnH = 18;
        int gap = 4;

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.dragoncare.dragon_phone.sort_name"),
                b -> { sort = SortMode.NAME; }).dimensions(x, y, btnW, btnH).build());
        x += btnW + gap;
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.dragoncare.dragon_phone.sort_stage"),
                b -> { sort = SortMode.STAGE; }).dimensions(x, y, btnW, btnH).build());
        x += btnW + gap;
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.dragoncare.dragon_phone.sort_near"),
                b -> { sort = SortMode.DISTANCE_ASC; }).dimensions(x, y, btnW, btnH).build());
        x += btnW + gap;
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.dragoncare.dragon_phone.sort_far"),
                b -> { sort = SortMode.DISTANCE_DESC; }).dimensions(x, y, btnW, btnH).build());
        x += btnW + gap;
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.dragoncare.dragon_phone.refresh"),
                b -> PacketDistributor.sendToServer(new RefreshListPayload()))
                .dimensions(x, y, btnW, btnH).build());
    }

    private List<DragonInfo> sorted() {
        List<DragonInfo> copy = new ArrayList<>(dragons);
        Vec3d ref = referencePosForSort();
        Comparator<DragonInfo> cmp = switch (sort) {
            case NAME -> Comparator.comparing(this::displayName, String.CASE_INSENSITIVE_ORDER);
            case STAGE -> Comparator.<DragonInfo>comparingInt(d -> d.stage()).reversed()
                    .thenComparingDouble(d -> distanceTo(d, ref));
            case DISTANCE_ASC -> Comparator.comparingDouble(d -> distanceTo(d, ref));
            case DISTANCE_DESC -> Comparator.<DragonInfo>comparingDouble(d -> distanceTo(d, ref)).reversed();
        };
        copy.sort(cmp);
        return copy;
    }

    private Vec3d referencePosForSort() {
        PlayerEntity p = MinecraftClient.getInstance().player;
        return p != null ? p.getPos() : Vec3d.ZERO;
    }

    private String displayName(DragonInfo d) {
        if (d.hasCustomName()) return d.customName();
        // Без имени — показываем вид (огненный дракон / ледяной / ...).
        String species = d.species();
        if (species != null && !species.isEmpty()) {
            return Text.translatable(species).getString();
        }
        // Fallback для legacy-записей без species.
        return d.dragonId().toString().substring(0, 6);
    }

    /**
     * Расстояние до дракона. Если игрок и дракон в разных измерениях — возвращаем
     * {@link Double#POSITIVE_INFINITY}, чтобы такие записи всегда уходили вниз
     * при сортировке «ближе» и наверх при «дальше».
     */
    private double distanceTo(DragonInfo d, Vec3d ref) {
        PlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return 0;
        if (!p.getWorld().getRegistryKey().getValue().equals(d.dimension())) return Double.POSITIVE_INFINITY;
        return ref.distanceTo(new Vec3d(d.x(), d.y(), d.z()));
    }

    private static String formatDistance(double dist) {
        if (Double.isInfinite(dist) || Double.isNaN(dist)) return "∞";
        if (dist >= 1000.0) return String.format(java.util.Locale.ROOT, "%.1fkm", dist / 1000.0);
        return String.format(java.util.Locale.ROOT, "%dm", (int) Math.round(dist));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Заголовок
        context.drawCenteredTextWithShadow(textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);

        // Сверху — данные выбранного дракона: координаты + расстояние.
        DragonInfo sel = selectedInfo();
        if (sel != null) {
            String name = displayName(sel);
            double dist = distanceTo(sel, referencePosForSort());
            String coords = String.format(java.util.Locale.ROOT, "[%d, %d, %d] (%s) %s",
                    (int) sel.x(), (int) sel.y(), (int) sel.z(), shortDim(sel.dimension().toString()),
                    formatDistance(dist));
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(name + " — ").formatted(Formatting.GOLD)
                            .append(Text.literal(coords).formatted(Formatting.WHITE)),
                    this.width / 2, 22, 0xFFFFFFFF);
        } else {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("screen.dragoncare.dragon_phone.hint").formatted(Formatting.GRAY),
                    this.width / 2, 22, 0xFFFFFFFF);
        }

        // Список
        int listLeft = 16;
        int listRight = this.width - 16;
        int listTop = LIST_TOP;
        int listBottom = this.height - LIST_BOTTOM_PADDING;

        context.fill(listLeft, listTop, listRight, listBottom, 0x88000000);

        List<DragonInfo> view = sorted();
        int totalRows = view.size();
        int visibleRows = (listBottom - listTop) / ROW_HEIGHT;

        // Скроллинг — clamp.
        double maxScroll = Math.max(0, totalRows - visibleRows) * ROW_HEIGHT;
        scrollY = MathHelper.clamp(scrollY, 0, maxScroll);

        context.enableScissor(listLeft, listTop, listRight, listBottom);

        Vec3d ref = referencePosForSort();
        int yPos = listTop - (int) scrollY;
        for (int i = 0; i < view.size(); i++) {
            DragonInfo d = view.get(i);
            if (yPos + ROW_HEIGHT < listTop || yPos > listBottom) {
                yPos += ROW_HEIGHT;
                continue;
            }
            // Подсветка выбранного / hover
            boolean isSelected = selected != null && selected.equals(d.dragonId());
            boolean isHover = mouseX >= listLeft && mouseX < listRight
                    && mouseY >= yPos && mouseY < yPos + ROW_HEIGHT;
            if (isSelected) context.fill(listLeft, yPos, listRight, yPos + ROW_HEIGHT, 0x55FFCC00);
            else if (isHover) context.fill(listLeft, yPos, listRight, yPos + ROW_HEIGHT, 0x33FFFFFF);

            int textY = yPos + 3;
            // # — порядковый
            context.drawTextWithShadow(textRenderer, "#" + (i + 1), listLeft + 4, textY, 0xFFAAAAAA);
            // имя
            String name = displayName(d);
            int color = d.hasCustomName() ? 0xFFFFFFFF : 0xFFCCCCCC;
            context.drawTextWithShadow(textRenderer, name, listLeft + 32, textY, color);
            // дистанция (правее)
            String distStr = formatDistance(distanceTo(d, ref));
            int distW = textRenderer.getWidth(distStr);
            context.drawTextWithShadow(textRenderer, distStr, listRight - 64 - distW, textY, 0xFFAACCFF);
            // стадия
            String stageStr = "S" + d.stage();
            int sw = textRenderer.getWidth(stageStr);
            context.drawTextWithShadow(textRenderer, stageStr, listRight - 8 - sw, textY, 0xFFFFCC55);

            yPos += ROW_HEIGHT;
        }

        context.disableScissor();
    }

    @Nullable
    private DragonInfo selectedInfo() {
        if (selected == null) return null;
        for (DragonInfo d : dragons) if (d.dragonId().equals(selected)) return d;
        return null;
    }

    private static String shortDim(String dim) {
        int sl = dim.indexOf(':');
        return sl >= 0 ? dim.substring(sl + 1) : dim;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listLeft = 16;
            int listRight = this.width - 16;
            int listTop = LIST_TOP;
            int listBottom = this.height - LIST_BOTTOM_PADDING;
            if (mouseX >= listLeft && mouseX < listRight && mouseY >= listTop && mouseY < listBottom) {
                int idx = (int) Math.floor((mouseY - listTop + scrollY) / ROW_HEIGHT);
                List<DragonInfo> view = sorted();
                if (idx >= 0 && idx < view.size()) {
                    DragonInfo d = view.get(idx);
                    UUID newSel = d.dragonId().equals(selected) ? null : d.dragonId();
                    selected = newSel;
                    PacketDistributor.sendToServer(new SelectDragonPayload(
                            newSel != null ? newSel : new UUID(0L, 0L)));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollY = Math.max(0, scrollY - verticalAmount * ROW_HEIGHT);
        return true;
    }

    @Override
    public boolean shouldPause() { return false; }
}
