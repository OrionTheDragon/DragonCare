package com.dragoncare.client.screen;

import com.dragoncare.DragonCare;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;


import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only paper screen for the burnt sheet. Lays out wrapped lines of text
 * inside the inner "paper" area of the {@code burnt_sheet_bg} texture and
 * clips drawing to that area via scissoring so text can never spill onto the
 * burnt edges. Mouse wheel scrolls vertically when content overflows.
 */
public class BurntSheetScreen extends Screen {

    private static final Identifier BG_TEXTURE =
            Identifier.of(DragonCare.MOD_ID, "textures/gui/burnt_sheet_bg.png");

    // Source PNG dimensions.
    private static final int TEX_W = 288;
    private static final int TEX_H = 512;

    // On-screen draw size for the paper. Aspect ratio (9:16) preserved.
    // Picked to fit comfortably on the default GUI scale of typical resolutions.
    private static final int RENDER_W = 192;
    private static final int RENDER_H = 341;

    // Inner content insets, in RENDER pixels (mapped from texture: ~28/40 px in source).
    private static final int PAD_LEFT = 19;
    private static final int PAD_RIGHT = 19;
    private static final int PAD_TOP = 26;
    private static final int PAD_BOTTOM = 26;

    // Sepia-ish ink so text reads on the warm paper.
    private static final int TEXT_COLOR = 0xFF2A1A0A;
    private static final int LINE_SPACING = 1;

    private final ItemStack stack;

    private int bgX;
    private int bgY;
    private int contentX;
    private int contentY;
    private int contentW;
    private int contentH;

    private List<OrderedText> lines = List.of();
    private int totalHeight;
    private int scroll;

    public BurntSheetScreen(ItemStack stack) {
        super(Text.empty());
        this.stack = stack;
    }

    @Override
    protected void init() {
        super.init();
        this.bgX = (this.width - RENDER_W) / 2;
        this.bgY = (this.height - RENDER_H) / 2;
        this.contentX = bgX + PAD_LEFT;
        this.contentY = bgY + PAD_TOP;
        this.contentW = RENDER_W - PAD_LEFT - PAD_RIGHT;
        this.contentH = RENDER_H - PAD_TOP - PAD_BOTTOM;
        rebuildLines();
        this.scroll = 0;
    }

    private void rebuildLines() {
        TextRenderer tr = this.textRenderer;
        Text content = getContent();
        this.lines = new ArrayList<>(tr.wrapLines(content, contentW));
        int lineH = tr.fontHeight + LINE_SPACING;
        this.totalHeight = Math.max(0, lines.size() * lineH - LINE_SPACING);
    }

    /**
     * Returns the text to display from the stack's {@code minecraft:custom_data}
     * NBT component, falling back to the translatable placeholder when absent.
     * <p>Two storage formats are supported:
     * <ul>
     *   <li>{@code text:["line 1","line 2",""]} — list of strings joined with
     *       {@code '\n'}. Use empty strings for blank lines between paragraphs.
     *       Preferred, since SNBT in /give commands cannot encode real newlines
     *       (no {@code \n} escape).</li>
     *   <li>{@code text:"single line"} — plain string, no newlines.</li>
     * </ul>
     */
    private Text getContent() {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null) {
            if (nbt.contains("translate", NbtElement.STRING_TYPE)) {
                return Text.translatable(nbt.getString("translate"));
            }
            if (nbt.contains("text", NbtElement.LIST_TYPE)) {
                NbtList list = nbt.getList("text", NbtElement.STRING_TYPE);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append('\n');
                    sb.append(list.getString(i));
                }
                return Text.literal(sb.toString());
            }
            if (nbt.contains("text", NbtElement.STRING_TYPE)) {
                return Text.literal(nbt.getString("text"));
            }
        }
        return Text.translatable("item.dragoncare.burnt_sheet.content");
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Custom dim instead of super.renderBackground(): a single translucent
        // fill keeps the world crisp behind the paper and avoids any blur shader
        // bleeding into the GUI layer.
        ctx.fill(0, 0, this.width, this.height, 0x90000000);

        // Paper background, scaled from source 288x512 down to RENDER_W x RENDER_H.
        ctx.drawTexture(BG_TEXTURE,
                bgX, bgY, RENDER_W, RENDER_H,
                0f, 0f, TEX_W, TEX_H, TEX_W, TEX_H);

        // Clip everything below to the inner paper area so text never spills
        // onto the burnt edges, even mid-line during scrolling.
        ctx.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        TextRenderer tr = this.textRenderer;
        int lineH = tr.fontHeight + LINE_SPACING;
        int y = contentY - scroll;
        for (OrderedText line : lines) {
            // Cheap culling: skip lines fully outside the clipped band.
            if (y + lineH >= contentY && y <= contentY + contentH) {
                ctx.drawText(tr, line, contentX, y, TEXT_COLOR, false);
            }
            y += lineH;
            if (y > contentY + contentH) break;
        }

        ctx.disableScissor();

        super.render(ctx, mouseX, mouseY, delta);
    }

    /**
     * Disable the menu blur shader so the paper and its text stay crisp.
     * The dim layer in {@link #render} provides enough focus without it.
     */

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int maxScroll = Math.max(0, totalHeight - contentH);
        if (maxScroll == 0) return true;
        int step = (textRenderer.fontHeight + LINE_SPACING) * 2;
        this.scroll = MathHelper.clamp(scroll - (int) Math.round(amount * step), 0, maxScroll);
        return true;
    }

    @Override
    public void resize(net.minecraft.client.MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        // init() repositions bg/content rectangles and rewraps lines for the new width.
        // Preserve scroll position relative to total height.
        int prevTotal = totalHeight;
        int prevScroll = scroll;
        init(client, width, height);
        if (prevTotal > 0) {
            this.scroll = MathHelper.clamp(
                    (int) Math.round((double) prevScroll * totalHeight / prevTotal),
                    0,
                    Math.max(0, totalHeight - contentH));
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}


