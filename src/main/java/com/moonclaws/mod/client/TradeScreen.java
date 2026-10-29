package com.moonclaws.mod.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.moonclaws.mod.ModNetwork;
import com.moonclaws.mod.StrayTrades;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/** Simple demo trade window with a stray cat: one button per trade. */
public class TradeScreen extends Screen {

    public TradeScreen() {
        super(new TranslationTextComponent("gui.moonclaws.trade.title"));
    }

    @Override
    protected void init() {
        StrayTrades.Trade[] trades = StrayTrades.get();
        int cx = this.width / 2;
        int y = this.height / 2 - 55;
        for (int i = 0; i < trades.length; i++) {
            final int index = i;
            StrayTrades.Trade t = trades[i];
            ITextComponent label = new StringTextComponent(t.wantsCount + " ")
                    .append(new ItemStack(t.wants).getHoverName())
                    .append(new StringTextComponent("  ->  " + t.givesCount + " "))
                    .append(new ItemStack(t.gives).getHoverName());
            this.addButton(new Button(cx - 110, y + i * 26, 220, 20, label,
                    b -> ModNetwork.CHANNEL.sendToServer(new ModNetwork.TradePacket(index))));
        }
        this.addButton(new Button(cx - 50, y + trades.length * 26 + 8, 100, 20,
                new TranslationTextComponent("gui.moonclaws.trade.close"), b -> this.onClose()));
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(matrixStack);
        drawCenteredString(matrixStack, this.font, this.title, this.width / 2, this.height / 2 - 75, 0xFFFFFF);
        super.render(matrixStack, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
