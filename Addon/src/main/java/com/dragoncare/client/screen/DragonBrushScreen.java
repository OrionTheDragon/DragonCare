package com.dragoncare.client.screen;

import com.dragoncare.network.DragonBrushCleanPayload;
import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Random;

/**
 * Client-side QTE mini-game screen for cleaning tamed dragons with the Dragon Brush.
 * Moves in real time, does not pause the world, and provides visual zones and point metrics.
 */
public class DragonBrushScreen extends Screen {

    private final DragonBaseEntity dragon;
    private final int startDirtLevel;
    private final Random random = new Random();

    // Game statistics
    private double currentScore = 0.0;
    private int failures = 0;
    private int greenHits = 0;
    private int yellowHits = 0;

    // Configured values based on stage
    private double greenSize;
    private double yellowSize;
    private double targetScore;
    private double greenHitPoints;
    private int maxAllowedFailures; // -1 for infinite
    private double speed;

    // Timing and rendering
    private long startTime = 0;
    private double pointer = 0.0;
    private double targetCenter = 0.5;
    private boolean isGameOver = false;

    public DragonBrushScreen(DragonBaseEntity dragon, int dirtLevel) {
        super(Text.translatable("gui.dragoncare.dragon_brush.title"));
        this.dragon = dragon;
        this.startDirtLevel = MathHelper.clamp(dirtLevel, 1, 5);
        setupStageParameters();
    }

    private void setupStageParameters() {
        switch (startDirtLevel) {
            case 1:
                this.greenSize = 0.55;
                this.yellowSize = 0.30;
                this.targetScore = 4.0;
                this.greenHitPoints = 2.0;
                this.maxAllowedFailures = -1; // Infinite
                this.speed = 0.57;
                break;
            case 2:
                this.greenSize = 0.45;
                this.yellowSize = 0.30;
                this.targetScore = 7.0;
                this.greenHitPoints = 2.33;
                this.maxAllowedFailures = 8;
                this.speed = 0.71;
                break;
            case 3:
                this.greenSize = 0.35;
                this.yellowSize = 0.25;
                this.targetScore = 10.0;
                this.greenHitPoints = 2.5;
                this.maxAllowedFailures = 7;
                this.speed = 0.91;
                break;
            case 4:
                this.greenSize = 0.25;
                this.yellowSize = 0.20;
                this.targetScore = 13.0;
                this.greenHitPoints = 2.6;
                this.maxAllowedFailures = 5;
                this.speed = 1.18;
                break;
            case 5:
            default:
                this.greenSize = 0.15;
                this.yellowSize = 0.15;
                this.targetScore = 16.0;
                this.greenHitPoints = 1.6;
                this.maxAllowedFailures = 4;
                this.speed = 1.67;
                break;
        }
        if (com.dragoncare.config.AddonConfig.DIRT_EASY_MINIGAME.get()) {
            this.greenSize = Math.min(1.0, this.greenSize * 1.5);
            this.yellowSize = Math.min(1.0 - this.greenSize, this.yellowSize * 1.5);
        }
        randomizeCenter();
    }

    private void randomizeCenter() {
        double activeHalf = (greenSize + yellowSize) / 2.0;
        double minCenter = activeHalf;
        double maxCenter = 1.0 - activeHalf;
        if (maxCenter > minCenter) {
            this.targetCenter = minCenter + random.nextDouble() * (maxCenter - minCenter);
        } else {
            this.targetCenter = 0.5;
        }
    }

    private double calculatePointerAt(long curTime) {
        if (startTime == 0) return 0.0;
        double elapsedSeconds = (curTime - startTime) / 1000.0;
        double cycle = elapsedSeconds * speed;
        return Math.abs((cycle % 2.0) - 1.0);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 32) { // GLFW_KEY_SPACE
            onAction();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left Click
            onAction();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void onAction() {
        if (isGameOver) return;

        long curTime = System.currentTimeMillis();
        double currentPointer = calculatePointerAt(curTime);
        double renderedPointer = this.pointer; // Last rendered value what player visually saw

        double distCurrent = Math.abs(currentPointer - targetCenter);
        double distRendered = Math.abs(renderedPointer - targetCenter);

        double greenLimit = greenSize / 2.0;
        double yellowLimit = (greenSize + yellowSize) / 2.0;

        var player = MinecraftClient.getInstance().player;

        // Lag-compensated check: if either exact click time or rendered position hit green, reward green!
        if (distCurrent <= greenLimit || distRendered <= greenLimit) {
            // Green hit!
            currentScore += greenHitPoints;
            greenHits++;
            if (player != null) {
                player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.8F, 1.5F);
            }
            randomizeCenter();
        } else if (distCurrent <= yellowLimit || distRendered <= yellowLimit) {
            // Yellow hit!
            currentScore += 1.0;
            yellowHits++;
            if (player != null) {
                player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.8F, 1.0F);
            }
            randomizeCenter();
        } else {
            // Red hit (Miss)!
            failures++;
            if (player != null) {
                player.playSound(SoundEvents.ENTITY_ITEM_BREAK, 0.6F, 0.8F);
            }
        }

        checkGameConditions();
    }

    private void checkGameConditions() {
        var player = MinecraftClient.getInstance().player;

        // Check Success
        if (currentScore >= targetScore - 0.01) {
            isGameOver = true;

            int totalHits = yellowHits + greenHits;
            double yellowRatio = totalHits > 0 ? (double) yellowHits / totalHits : 0.0;

            int newDirtLevel;
            Text msg;

            if (yellowRatio >= 0.50) {
                // Partial cleaning: decrease stage by 2, capped at 0
                newDirtLevel = Math.max(0, startDirtLevel - 2);
                msg = Text.translatable("gui.dragoncare.dragon_brush.partial").formatted(Formatting.YELLOW);
                if (player != null) {
                    player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.7F, 0.8F);
                }
            } else {
                // Complete cleaning: stage 0
                newDirtLevel = 0;
                msg = Text.translatable("gui.dragoncare.dragon_brush.success").formatted(Formatting.GREEN);
                if (player != null) {
                    player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
                }
            }

            PacketDistributor.sendToServer(new DragonBrushCleanPayload(dragon.getUuid(), newDirtLevel, 0));

            if (player != null) {
                player.sendMessage(msg, true);
            }

            this.close();
        }
        // Check Failure
        else if (maxAllowedFailures != -1 && failures >= maxAllowedFailures) {
            isGameOver = true;

            PacketDistributor.sendToServer(new DragonBrushCleanPayload(dragon.getUuid(), startDirtLevel, 1));

            if (player != null) {
                player.playSound(SoundEvents.BLOCK_ANVIL_LAND, 0.5F, 0.8F);
                player.sendMessage(
                        Text.translatable("gui.dragoncare.dragon_brush.failed").formatted(Formatting.RED),
                        true
                );
            }

            this.close();
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Dim the background
        ctx.fill(0, 0, this.width, this.height, 0x90000000);

        int cardWidth = 240;
        int cardHeight = 140;
        int cardX = (this.width - cardWidth) / 2;
        int cardY = (this.height - cardHeight) / 2;

        // Draw Card background (sleek charcoal gray)
        ctx.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 0xE0111115);

        // Header gold highlight
        ctx.fill(cardX, cardY, cardX + cardWidth, cardY + 2, 0xFFE5C158);

        // Subtle white border
        ctx.fill(cardX - 1, cardY, cardX, cardY + cardHeight, 0x40FFFFFF);
        ctx.fill(cardX + cardWidth, cardY, cardX + cardWidth + 1, cardY + cardHeight, 0x40FFFFFF);
        ctx.fill(cardX, cardY - 1, cardX + cardWidth, cardY, 0x40FFFFFF);
        ctx.fill(cardX, cardY + cardHeight, cardX + cardWidth, cardY + cardHeight + 1, 0x40FFFFFF);

        // 1. Draw Title
        Text titleText = Text.translatable("gui.dragoncare.dragon_brush.title");
        ctx.drawCenteredTextWithShadow(textRenderer, titleText, this.width / 2, cardY + 12, 0xFFE5C158);

        // Update pointer position smoothly based on system time
        long curTime = System.currentTimeMillis();
        if (startTime == 0) startTime = curTime;
        pointer = calculatePointerAt(curTime);

        // 2. Draw Slider bar
        int sliderX = cardX + 15;
        int sliderY = cardY + 45;
        int sliderWidth = cardWidth - 30;
        int sliderHeight = 16;

        // Draw deep crimson for red zone (slider background)
        ctx.fill(sliderX, sliderY, sliderX + sliderWidth, sliderY + sliderHeight, 0xFF5E1919);

        // Draw Yellow zone
        int yellowStart = (int) (sliderX + (targetCenter - (greenSize + yellowSize) / 2.0) * sliderWidth);
        int yellowEnd = (int) (sliderX + (targetCenter + (greenSize + yellowSize) / 2.0) * sliderWidth);
        ctx.fill(yellowStart, sliderY, yellowEnd, sliderY + sliderHeight, 0xFFCCA23A);

        // Draw Green zone
        int greenStart = (int) (sliderX + (targetCenter - greenSize / 2.0) * sliderWidth);
        int greenEnd = (int) (sliderX + (targetCenter + greenSize / 2.0) * sliderWidth);
        ctx.fill(greenStart, sliderY, greenEnd, sliderY + sliderHeight, 0xFF2EA246);

        // Draw slider border
        ctx.fill(sliderX - 1, sliderY - 1, sliderX, sliderY + sliderHeight + 1, 0x40FFFFFF);
        ctx.fill(sliderX + sliderWidth, sliderY - 1, sliderX + sliderWidth + 1, sliderY + sliderHeight + 1, 0x40FFFFFF);
        ctx.fill(sliderX, sliderY - 1, sliderX + sliderWidth, sliderY, 0x40FFFFFF);
        ctx.fill(sliderX, sliderY + sliderHeight, sliderX + sliderWidth, sliderY + sliderHeight + 1, 0x40FFFFFF);

        // Draw Pointer
        int pointerX = (int) (sliderX + pointer * sliderWidth);
        // Drop shadow for pointer
        ctx.fill(pointerX - 2, sliderY - 2, pointerX + 3, sliderY + sliderHeight + 2, 0x40000000);
        // White glow
        ctx.fill(pointerX - 1, sliderY - 1, pointerX + 2, sliderY + sliderHeight + 1, 0xA0FFFFFF);
        // Solid pointer core
        ctx.fill(pointerX, sliderY - 2, pointerX + 1, sliderY + sliderHeight + 2, 0xFFFFFFFF);

        // 3. Draw Progress text & mini progress bar
        String progressStr = Text.translatable("gui.dragoncare.dragon_brush.progress",
                String.format(java.util.Locale.ROOT, "%.1f", currentScore),
                String.format(java.util.Locale.ROOT, "%.1f", targetScore)).getString();
        ctx.drawTextWithShadow(textRenderer, progressStr, cardX + 15, cardY + 74, 0xFFE0E0E0);

        int progressX = cardX + 15;
        int progressY = cardY + 86;
        int progressW = sliderWidth;
        int progressH = 4;
        ctx.fill(progressX, progressY, progressX + progressW, progressY + progressH, 0xFF141416); // progress track
        int fillW = (int) (progressW * Math.min(1.0, currentScore / targetScore));
        ctx.fill(progressX, progressY, progressX + fillW, progressY + progressH, 0xFF3A89FF); // neon blue fill

        // 4. Draw Failures text
        String maxFailStr = maxAllowedFailures == -1
                ? Text.translatable("gui.dragoncare.dragon_brush.infinite").getString()
                : String.valueOf(maxAllowedFailures);
        String failuresStr = Text.translatable("gui.dragoncare.dragon_brush.failures", failures, maxFailStr).getString();

        int failuresColor = 0xFFE0E0E0;
        if (maxAllowedFailures != -1 && failures >= maxAllowedFailures - 1) {
            failuresColor = 0xFFFF3333; // glowing danger red!
        }
        ctx.drawTextWithShadow(textRenderer, failuresStr, cardX + 15, cardY + 98, failuresColor);

        // 5. Draw Pulsing Prompt Text at bottom
        double pulse = Math.sin((System.currentTimeMillis() / 250.0)) * 0.25 + 0.75;
        int alpha = (int) (pulse * 255);
        int promptColor = (alpha << 24) | 0x00A0A0A0;

        String promptStr = Text.translatable("gui.dragoncare.dragon_brush.press_space").getString();
        ctx.drawTextWithShadow(textRenderer, promptStr, cardX + (cardWidth - textRenderer.getWidth(promptStr)) / 2, cardY + 118, promptColor);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void applyBlur(float delta) {
        // intentionally empty to keep the world and dragon visible during the real-time QTE
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
