package com.moonclaws.mod.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moonclaws.mod.CatTradeContainer;
import com.moonclaws.mod.MoonclawsMod;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MerchantOffer;
import net.minecraft.item.MerchantOffers;
import net.minecraft.network.play.client.CSelectTradePacket;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/**
 * Trade window on birch bark: offers on the left (prey -> thing, and how many trades are left today),
 * the usual two payment slots and the result slot on the right.
 */
public class CatTradeScreen extends ContainerScreen<CatTradeContainer> {

    private static final ResourceLocation BG = new ResourceLocation(MoonclawsMod.MODID, "textures/gui/cat_trade.png");
    private static final int ROWS = 7;
    private static final int ROW_X = 5;
    private static final int ROW_Y = 18;
    private static final int ROW_W = 87;
    private static final int ROW_H = 20;

    private static final int TEXT = 0xFF2E1E10;
    private static final int TEXT_LIGHT = 0xFFFAF4E6;
    private static final int PLAQUE = 0xFFD6C6A6;
    private static final int PLAQUE_EDGE = 0xFF96805F;

    private int selected;
    private int scroll;

    public CatTradeScreen(CatTradeContainer container, PlayerInventory inventory, ITextComponent title) {
        super(container, inventory, title);
        this.imageWidth = 276;
        this.imageHeight = 166;
        this.inventoryLabelX = 108;
        this.inventoryLabelY = 72;
    }

    private MerchantOffers offers() {
        return this.menu.getOffers();
    }

    @Override
    protected void renderBg(MatrixStack ms, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bind(BG);
        blit(ms, leftPos, topPos, 0, 0.0F, 0.0F, imageWidth, imageHeight, 256, 512);

        MerchantOffers offers = offers();
        for (int i = 0; i < ROWS && scroll + i < offers.size(); i++) {
            int index = scroll + i;
            MerchantOffer offer = offers.get(index);
            int x = leftPos + ROW_X;
            int y = topPos + ROW_Y + i * ROW_H;
            boolean hover = mouseX >= x && mouseX < x + ROW_W && mouseY >= y && mouseY < y + ROW_H;
            int fillColor = index == selected ? 0xFFC4D6AA : (hover ? 0xFFEADCBE : 0xFFE0D3B8);
            if (offer.isOutOfStock()) {
                fillColor = index == selected ? 0xFFD8B0A0 : 0xFFD9C3B4;
            }
            fill(ms, x, y, x + ROW_W, y + ROW_H, 0xFFA08C6C);
            fill(ms, x + 1, y + 1, x + ROW_W - 1, y + ROW_H - 1, fillColor);
            // twig arrow
            int ay = y + 10;
            int color = offer.isOutOfStock() ? 0xFF9A2A20 : 0xFF6E5434;
            fill(ms, x + 41, ay, x + 57, ay + 1, color);
            fill(ms, x + 55, ay - 2, x + 56, ay - 1, color);
            fill(ms, x + 54, ay - 3, x + 55, ay - 2, color);
            fill(ms, x + 55, ay + 2, x + 56, ay + 3, color);
            fill(ms, x + 54, ay + 3, x + 55, ay + 4, color);
        }
        // scroll marks
        if (offers.size() > ROWS) {
            int barH = ROWS * ROW_H;
            int knob = Math.max(10, barH * ROWS / offers.size());
            int maxScroll = offers.size() - ROWS;
            int ky = topPos + ROW_Y + (barH - knob) * scroll / Math.max(1, maxScroll);
            fill(ms, leftPos + 93, ky, leftPos + 96, ky + knob, 0xFF8C7454);
        }
    }

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(ms);
        super.render(ms, mouseX, mouseY, partialTicks);

        MerchantOffers offers = offers();
        ItemStack hovered = ItemStack.EMPTY;
        this.itemRenderer.blitOffset = 100.0F;
        for (int i = 0; i < ROWS && scroll + i < offers.size(); i++) {
            MerchantOffer offer = offers.get(scroll + i);
            int x = leftPos + ROW_X;
            int y = topPos + ROW_Y + i * ROW_H + 2;
            ItemStack a = offer.getCostA();
            ItemStack b = offer.getCostB();
            ItemStack r = offer.getResult();
            this.itemRenderer.renderAndDecorateFakeItem(a, x + 4, y);
            this.itemRenderer.renderGuiItemDecorations(this.font, a, x + 4, y);
            if (!b.isEmpty()) {
                this.itemRenderer.renderAndDecorateFakeItem(b, x + 22, y);
                this.itemRenderer.renderGuiItemDecorations(this.font, b, x + 22, y);
            }
            this.itemRenderer.renderAndDecorateFakeItem(r, x + 60, y);
            this.itemRenderer.renderGuiItemDecorations(this.font, r, x + 60, y);
            if (mouseY >= y && mouseY < y + 16) {
                if (mouseX >= x + 4 && mouseX < x + 20) {
                    hovered = a;
                } else if (!b.isEmpty() && mouseX >= x + 22 && mouseX < x + 38) {
                    hovered = b;
                } else if (mouseX >= x + 60 && mouseX < x + 76) {
                    hovered = r;
                }
            }
        }
        this.itemRenderer.blitOffset = 0.0F;

        // how many trades are left today, small, above the twig arrow
        ms.pushPose();
        ms.translate(0.0D, 0.0D, 300.0D);
        for (int i = 0; i < ROWS && scroll + i < offers.size(); i++) {
            MerchantOffer offer = offers.get(scroll + i);
            int left = Math.max(0, offer.getMaxUses() - offer.getUses());
            String s = "\u00d7" + left;
            int x = leftPos + ROW_X + 49 - this.font.width(s) / 2;
            int y = topPos + ROW_Y + i * ROW_H + 1;
            ms.pushPose();
            ms.translate(x, y, 0.0D);
            ms.scale(0.75F, 0.75F, 1.0F);
            this.font.draw(ms, s, 0.0F, 0.0F, left == 0 ? 0xFF9A2A20 : 0xFF6B5A3C);
            ms.popPose();
        }
        ms.popPose();

        if (!hovered.isEmpty()) {
            this.renderTooltip(ms, hovered, mouseX, mouseY);
        } else {
            this.renderTooltip(ms, mouseX, mouseY);
        }
    }

    private void plaque(MatrixStack ms, ITextComponent text, int x, int y, int color) {
        int w = this.font.width(text);
        fill(ms, x - 3, y - 2, x + w + 3, y + 9, PLAQUE_EDGE);
        fill(ms, x - 2, y - 1, x + w + 2, y + 8, PLAQUE);
        this.font.draw(ms, text, x + 1, y + 1, TEXT_LIGHT);
        this.font.draw(ms, text, x, y, color);
    }

    @Override
    protected void renderLabels(MatrixStack ms, int mouseX, int mouseY) {
        plaque(ms, new TranslationTextComponent("gui.moonclaws.trade.offers"), 9, 6, TEXT);
        int tw = this.font.width(this.title);
        plaque(ms, this.title, 107 + (162 - tw) / 2, 6, TEXT);
        plaque(ms, this.inventory.getDisplayName(), this.inventoryLabelX + 2, this.inventoryLabelY, TEXT);
        MerchantOffers offers = offers();
        if (selected >= 0 && selected < offers.size()) {
            MerchantOffer o = offers.get(selected);
            int left = Math.max(0, o.getMaxUses() - o.getUses());
            ITextComponent t = new TranslationTextComponent("gui.moonclaws.trade.left", left);
            plaque(ms, t, 136, 60, left == 0 ? 0xFF8B1A1A : 0xFF28501E);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MerchantOffers offers = offers();
        for (int i = 0; i < ROWS && scroll + i < offers.size(); i++) {
            int x = leftPos + ROW_X;
            int y = topPos + ROW_Y + i * ROW_H;
            if (mouseX >= x && mouseX < x + ROW_W && mouseY >= y && mouseY < y + ROW_H) {
                select(scroll + i);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void select(int index) {
        selected = index;
        this.menu.setSelectionHint(index);
        this.menu.tryMoveItems(index);
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().send(new CSelectTradePacket(index));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, offers().size() - ROWS);
        if (max > 0) {
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
