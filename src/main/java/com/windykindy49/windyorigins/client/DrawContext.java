package com.windykindy49.windyorigins.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 1.19.4 has no DrawContext yet. This is the part of it the mod draws with, laid over the old static helpers,
 * so the HUD code is the same on every version.
 */
public class DrawContext extends DrawableHelper {
    private final MatrixStack matrices;

    public DrawContext(MatrixStack matrices) {
        this.matrices = matrices;
    }

    public MatrixStack getMatrices() {
        return this.matrices;
    }

    public int getScaledWindowWidth() {
        return MinecraftClient.getInstance().getWindow().getScaledWidth();
    }

    public int getScaledWindowHeight() {
        return MinecraftClient.getInstance().getWindow().getScaledHeight();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(this.matrices, x1, y1, x2, y2, color);
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        DrawableHelper.fillGradient(this.matrices, x1, y1, x2, y2, top, bottom);
    }

    public int drawTextWithShadow(TextRenderer renderer, String text, int x, int y, int color) {
        return renderer.drawWithShadow(this.matrices, text, (float)x, (float)y, color);
    }

    public int drawTextWithShadow(TextRenderer renderer, Text text, int x, int y, int color) {
        return renderer.drawWithShadow(this.matrices, text, (float)x, (float)y, color);
    }

    public int drawTextWithShadow(TextRenderer renderer, OrderedText text, int x, int y, int color) {
        return renderer.drawWithShadow(this.matrices, text, (float)x, (float)y, color);
    }

    public int drawText(TextRenderer renderer, String text, int x, int y, int color, boolean shadow) {
        return shadow ? renderer.drawWithShadow(this.matrices, text, (float)x, (float)y, color) : renderer.draw(this.matrices, text, (float)x, (float)y, color);
    }

    public int drawText(TextRenderer renderer, Text text, int x, int y, int color, boolean shadow) {
        return shadow ? renderer.drawWithShadow(this.matrices, text, (float)x, (float)y, color) : renderer.draw(this.matrices, text, (float)x, (float)y, color);
    }

    public int drawText(TextRenderer renderer, OrderedText text, int x, int y, int color, boolean shadow) {
        return shadow ? renderer.drawWithShadow(this.matrices, text, (float)x, (float)y, color) : renderer.draw(this.matrices, text, (float)x, (float)y, color);
    }

    public void drawCenteredTextWithShadow(TextRenderer renderer, String text, int x, int y, int color) {
        DrawableHelper.drawCenteredTextWithShadow(this.matrices, renderer, text, x, y, color);
    }

    public void drawCenteredTextWithShadow(TextRenderer renderer, Text text, int x, int y, int color) {
        DrawableHelper.drawCenteredTextWithShadow(this.matrices, renderer, text, x, y, color);
    }

    public void drawTexture(Identifier texture, int x, int y, int u, int v, int width, int height) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        DrawableHelper.drawTexture(this.matrices, x, y, u, v, width, height);
    }

    public void drawTexture(Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        DrawableHelper.drawTexture(this.matrices, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    public void drawSprite(int x, int y, int z, int width, int height, Sprite sprite) {
        RenderSystem.enableBlend();
        DrawableHelper.drawSprite(this.matrices, x, y, z, width, height, sprite);
    }

    public void setShaderColor(float red, float green, float blue, float alpha) {
        RenderSystem.setShaderColor(red, green, blue, alpha);
    }

    public void drawItem(ItemStack stack, int x, int y) {
        MinecraftClient.getInstance().getItemRenderer().renderInGui(this.matrices, stack, x, y);
    }

    public void drawItemInSlot(TextRenderer renderer, ItemStack stack, int x, int y) {
        MinecraftClient.getInstance().getItemRenderer().renderGuiItemOverlay(this.matrices, renderer, stack, x, y);
    }

    /** Nothing is batched in 1.19.4: everything is on screen the moment it is drawn. */
    public void draw() {
    }
}
