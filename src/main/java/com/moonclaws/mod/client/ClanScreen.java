package com.moonclaws.mod.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.moonclaws.mod.CatState;
import com.moonclaws.mod.ModNetwork;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.TranslationTextComponent;

/** Simple screen with four buttons: choose your clan. */
public class ClanScreen extends Screen {

    public ClanScreen() {
        super(new TranslationTextComponent("gui.moonclaws.clan.title"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 40;
        this.addButton(new Button(cx - 110, y, 220, 20,
                new TranslationTextComponent("gui.moonclaws.clan.thunder"), b -> choose(CatState.CLAN_THUNDER)));
        this.addButton(new Button(cx - 110, y + 26, 220, 20,
                new TranslationTextComponent("gui.moonclaws.clan.river"), b -> choose(CatState.CLAN_RIVER)));
        this.addButton(new Button(cx - 110, y + 52, 220, 20,
                new TranslationTextComponent("gui.moonclaws.clan.wind"), b -> choose(CatState.CLAN_WIND)));
        this.addButton(new Button(cx - 110, y + 78, 220, 20,
                new TranslationTextComponent("gui.moonclaws.clan.shadow"), b -> choose(CatState.CLAN_SHADOW)));
    }

    private void choose(int clan) {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ChooseClanPacket(clan));
        this.onClose();
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(matrixStack);
        drawCenteredString(matrixStack, this.font, this.title, this.width / 2, this.height / 2 - 65, 0xFFFFFF);
        super.render(matrixStack, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
