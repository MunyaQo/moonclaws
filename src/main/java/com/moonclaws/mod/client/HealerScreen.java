package com.moonclaws.mod.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.moonclaws.mod.CatState;
import com.moonclaws.mod.HealerContainer;
import com.moonclaws.mod.Medicine;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/** Healer's Stone window, drawn without a texture: grey stone with moss on the edges. */
public class HealerScreen extends ContainerScreen<HealerContainer> {

    private static final int STONE = 0xFF85847F;
    private static final int STONE_DARK = 0xFF55544F;
    private static final int STONE_LIGHT = 0xFFA9A8A2;
    private static final int[] MOSS = {0xFF4E6C34, 0xFF5C7C3C, 0xFF42602C};

    public HealerScreen(HealerContainer container, PlayerInventory inventory, ITextComponent title) {
        super(container, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 93;
    }

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(ms);
        super.render(ms, mouseX, mouseY, partialTicks);
        this.renderTooltip(ms, mouseX, mouseY);
    }

    @Override
    protected void renderBg(MatrixStack ms, float partialTicks, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        int w = this.imageWidth;
        int h = this.imageHeight;
        // stone body with a light top-left and dark bottom-right edge
        fill(ms, x, y, x + w, y + h, STONE_DARK);
        fill(ms, x + 1, y + 1, x + w - 1, y + h - 1, STONE_LIGHT);
        fill(ms, x + 2, y + 2, x + w - 1, y + h - 1, STONE);
        // speckles
        for (int i = 0; i < 70; i++) {
            int px = x + 4 + (i * 37) % (w - 8);
            int py = y + 4 + (i * 53) % (h - 8);
            fill(ms, px, py, px + 1, py + 1, (i % 2 == 0) ? 0xFF77766F : 0xFF94938C);
        }
        // moss on the edges
        for (int i = 0; i < w; i += 3) {
            int len = 2 + (i * 7) % 5;
            fill(ms, x + i, y, x + Math.min(i + 3, w), y + len, MOSS[(i / 3) % 3]);
            if ((i / 3) % 4 == 0) {
                fill(ms, x + i, y + h - 3, x + Math.min(i + 3, w), y + h, MOSS[(i / 3) % 3]);
            }
        }
        for (int j = 0; j < h; j += 4) {
            if ((j / 4) % 3 == 0) {
                fill(ms, x, y + j, x + 3, y + j + 4, MOSS[(j / 4) % 3]);
                fill(ms, x + w - 3, y + j + 2, x + w, y + j + 6, MOSS[(j / 4 + 1) % 3]);
            }
        }
        // slots
        for (int i = 0; i < this.menu.slots.size(); i++) {
            Slot slot = this.menu.slots.get(i);
            if (i == HealerContainer.RESULT) {
                slotFrame(ms, x + slot.x - 5, y + slot.y - 5, 26);
            } else {
                slotFrame(ms, x + slot.x - 1, y + slot.y - 1, 18);
            }
        }
        // arrow to the result
        this.font.drawShadow(ms, "→", x + 100, y + 37, 0xFFE8E0C0);
    }

    private static void slotFrame(MatrixStack ms, int x, int y, int size) {
        fill(ms, x, y, x + size, y + size, 0xFF373737);
        fill(ms, x + 1, y + 1, x + size, y + size, 0xFFF0F0F0);
        fill(ms, x + 1, y + 1, x + size - 1, y + size - 1, 0xFF6B6A65);
    }

    @Override
    protected void renderLabels(MatrixStack ms, int mouseX, int mouseY) {
        this.font.draw(ms, this.title, (float) this.titleLabelX, (float) this.titleLabelY, 0xFF2A2A26);
        this.font.draw(ms, this.inventory.getDisplayName(), (float) this.inventoryLabelX, (float) this.inventoryLabelY, 0xFF2A2A26);
        // small labels
        this.font.draw(ms, new TranslationTextComponent("gui.moonclaws.healer.bandage"), 66.0F, 50.0F, 0xFF3A3A34);

        int herbs = 0;
        for (int i = 0; i < 3; i++) {
            if (Medicine.isHerb(this.menu.getIngredient(i))) {
                herbs++;
            }
        }
        boolean web = Medicine.isWeb(this.menu.getIngredient(HealerContainer.WEB));
        int rank = this.minecraft != null && this.minecraft.player != null ? CatState.getRank(this.minecraft.player) : 0;
        String problem = Medicine.problem(rank, herbs, web);
        if (problem != null) {
            ITextComponent text = new TranslationTextComponent(problem);
            int tw = this.font.width(text);
            this.font.draw(ms, text, (this.imageWidth - tw) / 2.0F, 63.0F, 0xFF8B1A1A);
        } else {
            ItemStack result = this.menu.getSlot(HealerContainer.RESULT).getItem();
            if (!result.isEmpty() && rank == CatState.RANK_HEALER) {
                ITextComponent text = new TranslationTextComponent("gui.moonclaws.healer.healer_bonus");
                int tw = this.font.width(text);
                this.font.draw(ms, text, (this.imageWidth - tw) / 2.0F, 63.0F, 0xFF1F5A2A);
            }
        }
    }
}
