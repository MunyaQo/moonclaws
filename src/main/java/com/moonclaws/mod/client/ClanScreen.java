package com.moonclaws.mod.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moonclaws.mod.CatNames;
import com.moonclaws.mod.CatState;
import com.moonclaws.mod.ModNetwork;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/**
 * The cat card: one screen with a slowly turning cat on the left and all choices on the right
 * (sex, clan, class, coat, name). Opened by the Moon Stone (Shift + right click, or by itself the first time).
 */
public class ClanScreen extends Screen {

    private static final int W = 330;
    private static final int H = 232;
    private static final int LABEL_W = 52;

    private final CatState.Card card;
    private final boolean oldCatWithoutCard;
    private final Random random = new Random();
    private boolean confirmed;

    private int left;
    private int top;
    private int x0;
    private int ctrlX;
    private int ctrlW;

    private Button tomButton;
    private Button sheButton;
    private Button clanButton;
    private Button rankButton;
    private Button skinLabel;
    private Button endingButton;
    private TextFieldWidget kitField;
    private TextFieldWidget stemField;

    private CatEntity preview;
    private int ticks;

    public ClanScreen(CatState.Card card) {
        super(new TranslationTextComponent("gui.moonclaws.card.title"));
        this.card = card;
        this.oldCatWithoutCard = card.clan != CatState.CLAN_NONE && !card.done;
        int clientLang = clientLang();
        // defaults for an empty card
        if (card.clan < CatState.CLAN_THUNDER || card.clan > CatState.CLAN_SHADOW) {
            card.clan = CatState.CLAN_THUNDER;
        }
        if (card.gender != CatState.GENDER_TOM && card.gender != CatState.GENDER_SHE) {
            card.gender = random.nextBoolean() ? CatState.GENDER_TOM : CatState.GENDER_SHE;
        }
        if (card.rank < CatState.RANK_KIT || card.rank > CatState.RANK_ELDER) {
            card.rank = CatState.RANK_WARRIOR;
        }
        if (card.skin < 0 || card.skin >= CatState.SKINS) {
            card.skin = random.nextInt(CatState.SKINS);
        }
        if (!card.done) {
            card.lang = clientLang;
        }
        if (CatNames.clean(card.kitName).isEmpty() && CatNames.clean(card.stem).isEmpty()) {
            randomName();
        }
    }

    private static int clientLang() {
        try {
            String code = Minecraft.getInstance().getLanguageManager().getSelected().getCode();
            return code.startsWith("ru") || code.startsWith("uk") || code.startsWith("be")
                    ? CatNames.LANG_RU : CatNames.LANG_EN;
        } catch (RuntimeException e) {
            return CatNames.LANG_RU;
        }
    }

    private void randomName() {
        card.lang = clientLang();
        String[] name = CatNames.random(card.lang, random);
        card.kitName = name[0];
        card.stem = name[1];
        card.ending = name[2];
        if (kitField != null) {
            kitField.setValue(card.kitName);
            stemField.setValue(card.stem);
        }
    }

    @Override
    protected void init() {
        left = (this.width - W) / 2;
        top = Math.max(2, (this.height - H) / 2);
        x0 = left + 118;
        ctrlX = x0 + LABEL_W;
        ctrlW = W - 118 - LABEL_W;

        int y = top + 16;
        int half = (ctrlW - 4) / 2;
        tomButton = addButton(new Button(ctrlX, y, half, 20, StringTextComponent.EMPTY, b -> {
            card.gender = CatState.GENDER_TOM;
            refreshLabels();
        }));
        sheButton = addButton(new Button(ctrlX + half + 4, y, half, 20, StringTextComponent.EMPTY, b -> {
            card.gender = CatState.GENDER_SHE;
            refreshLabels();
        }));

        y += 24;
        clanButton = addButton(new Button(ctrlX, y, ctrlW, 20, StringTextComponent.EMPTY, b -> {
            card.clan = card.clan % 4 + 1;
            refreshLabels();
        }));

        y += 34;
        rankButton = addButton(new Button(ctrlX, y, ctrlW, 20, StringTextComponent.EMPTY, b -> {
            card.rank = card.rank % 7 + 1;
            refreshLabels();
        }));

        y += 46;
        addButton(new Button(ctrlX, y, 20, 20, new StringTextComponent("<"), b -> {
            card.skin = (card.skin + CatState.SKINS - 1) % CatState.SKINS;
            refreshLabels();
        }));
        int skinW = ctrlW - 20 - 20 - 50 - 4;
        skinLabel = addButton(new Button(ctrlX + 20, y, skinW, 20, StringTextComponent.EMPTY, b -> { }));
        skinLabel.active = false;
        addButton(new Button(ctrlX + 20 + skinW, y, 20, 20, new StringTextComponent(">"), b -> {
            card.skin = (card.skin + 1) % CatState.SKINS;
            refreshLabels();
        }));
        addButton(new Button(ctrlX + ctrlW - 50, y, 50, 20, new TranslationTextComponent("gui.moonclaws.card.random"), b -> {
            card.skin = random.nextInt(CatState.SKINS);
            refreshLabels();
        }));

        y += 26;
        kitField = new TextFieldWidget(this.font, ctrlX + 1, y, ctrlW - 2, 16, new TranslationTextComponent("gui.moonclaws.card.kit"));
        kitField.setMaxLength(CatNames.MAX_LENGTH);
        kitField.setValue(card.kitName);
        kitField.setResponder(s -> {
            String oldGuess = CatNames.guessStem(card.kitName, card.lang);
            boolean stemWasGuessed = stemField != null && (stemField.getValue().isEmpty() || stemField.getValue().equals(oldGuess));
            card.kitName = s;
            // keep the adult name start in sync while the player did not change it by hand
            if (stemWasGuessed) {
                stemField.setValue(CatNames.guessStem(s, card.lang));
            }
        });
        addWidget(kitField);

        y += 20;
        int stemW = ctrlW - 4 - 74;
        stemField = new TextFieldWidget(this.font, ctrlX + 1, y + 2, stemW - 2, 16, new TranslationTextComponent("gui.moonclaws.card.stem"));
        stemField.setMaxLength(CatNames.MAX_LENGTH);
        stemField.setValue(card.stem);
        stemField.setResponder(s -> card.stem = s);
        addWidget(stemField);
        endingButton = addButton(new Button(ctrlX + stemW + 4, y, 74, 20, StringTextComponent.EMPTY, b -> {
            String[] endings = CatNames.endings(card.lang);
            int index = 0;
            for (int i = 0; i < endings.length; i++) {
                if (endings[i].equalsIgnoreCase(card.ending)) {
                    index = i + 1;
                    break;
                }
            }
            card.ending = endings[index % endings.length];
            refreshLabels();
        }));

        y += 24;
        addButton(new Button(x0, y, 104, 20, new TranslationTextComponent("gui.moonclaws.card.random_name"), b -> {
            randomName();
            refreshLabels();
        }));

        addButton(new Button(x0 + (W - 118 - 150) / 2, top + H - 20, 150, 20,
                new TranslationTextComponent(card.done ? "gui.moonclaws.card.save" : "gui.moonclaws.card.become"),
                b -> confirm()));

        refreshLabels();
    }

    private void refreshLabels() {
        tomButton.setMessage(selected(new TranslationTextComponent("gui.moonclaws.gender.1"), card.gender == CatState.GENDER_TOM));
        sheButton.setMessage(selected(new TranslationTextComponent("gui.moonclaws.gender.2"), card.gender == CatState.GENDER_SHE));
        clanButton.setMessage(new TranslationTextComponent("gui.moonclaws.clan." + card.clan));
        rankButton.setMessage(new TranslationTextComponent("gui.moonclaws.rank." + card.rank + "." + card.gender));
        skinLabel.setMessage(new TranslationTextComponent("gui.moonclaws.skin." + card.skin));
        String ending = CatNames.clean(card.ending);
        if (ending.isEmpty()) {
            ending = CatNames.endings(card.lang)[0];
            card.ending = ending;
        }
        endingButton.setMessage(new StringTextComponent("-" + ending.toLowerCase()));
    }

    private static ITextComponent selected(ITextComponent text, boolean on) {
        return on ? new StringTextComponent("» ").append(text).append(" «") : text;
    }

    private void confirm() {
        confirmed = true;
        card.kitName = kitField.getValue();
        card.stem = stemField.getValue();
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ApplyCardPacket(card, false));
        this.onClose();
    }

    @Override
    public void onClose() {
        if (!confirmed && oldCatWithoutCard) {
            // a cat from an older version closed the card: become a warrior with a random name
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.ApplyCardPacket(card, true));
        }
        super.onClose();
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        kitField.tick();
        stemField.tick();
    }

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(ms);
        // frame
        fill(ms, left - 6, top - 6, left + W + 6, top + H + 6, 0xC0101418);
        fill(ms, left - 6, top - 6, left + W + 6, top - 5, 0xFF6E8BA8);
        fill(ms, left + 110, top + 10, left + 111, top + H - 4, 0x40FFFFFF);

        drawCenteredString(ms, this.font, this.title, left + W / 2, top, 0xFFE8D9A8);

        // labels on the right
        int y = top + 16;
        label(ms, "gui.moonclaws.card.sex", y);
        y += 24;
        label(ms, "gui.moonclaws.card.clan", y);
        small(ms, new TranslationTextComponent("gui.moonclaws.clan." + card.clan + ".desc"), y + 22, 1);
        y += 34;
        label(ms, "gui.moonclaws.card.rank", y);
        small(ms, new TranslationTextComponent("gui.moonclaws.rank." + card.rank + ".desc"), y + 22, 2);
        y += 46;
        label(ms, "gui.moonclaws.card.coat", y);
        y += 26;
        label(ms, "gui.moonclaws.card.kit", y - 2);
        kitField.render(ms, mouseX, mouseY, partialTicks);
        y += 20;
        label(ms, "gui.moonclaws.card.adult", y);
        stemField.render(ms, mouseX, mouseY, partialTicks);
        y += 24;

        // name preview
        String name = CatNames.compose(card.rank, card.lang, kitField.getValue(), stemField.getValue(), card.ending);
        ITextComponent preview = new TranslationTextComponent("gui.moonclaws.card.name_preview", name);
        this.font.drawShadow(ms, preview, x0 + 110, y + 6, 0xFFFFE680);

        // the cat
        renderCat(left + 55, top + 168, partialTicks);
        drawCenteredString(ms, this.font, new StringTextComponent(name), left + 55, top + 180, 0xFFFFFFFF);
        drawCenteredString(ms, this.font,
                new TranslationTextComponent("gui.moonclaws.rank." + card.rank + "." + card.gender), left + 55, top + 192, 0xFFAAAAAA);

        super.render(ms, mouseX, mouseY, partialTicks);
    }

    private void label(MatrixStack ms, String key, int y) {
        this.font.drawShadow(ms, new TranslationTextComponent(key), x0, y + 6, 0xFFDDDDDD);
    }

    private void small(MatrixStack ms, ITextComponent text, int y, int maxLines) {
        List<IReorderingProcessor> lines = this.font.split(text, W - 118);
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            this.font.drawShadow(ms, lines.get(i), x0, y + i * 10, 0xFF9FB8A0);
        }
    }

    private void renderCat(int x, int y, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (preview == null || preview.level != mc.level) {
            preview = new CatEntity(EntityType.CAT, mc.level);
        }
        preview.setCatType(card.skin);
        preview.setAge(card.rank == CatState.RANK_KIT ? -24000 : 0);
        float angle = (ticks + partialTicks) * 1.5F;
        int scale = card.rank == CatState.RANK_KIT ? 70 : 55;

        RenderSystem.pushMatrix();
        RenderSystem.translatef((float) x, (float) y, 1050.0F);
        RenderSystem.scalef(1.0F, 1.0F, -1.0F);
        MatrixStack ms = new MatrixStack();
        ms.translate(0.0D, 0.0D, 1000.0D);
        ms.scale((float) scale, (float) scale, (float) scale);
        Quaternion flip = Vector3f.ZP.rotationDegrees(180.0F);
        Quaternion tilt = Vector3f.XP.rotationDegrees(-12.0F);
        flip.mul(tilt);
        ms.mulPose(flip);
        preview.yBodyRot = angle;
        preview.yBodyRotO = angle;
        preview.yRot = angle;
        preview.yRotO = angle;
        preview.yHeadRot = angle;
        preview.yHeadRotO = angle;
        preview.xRot = 0.0F;
        EntityRendererManager dispatcher = mc.getEntityRenderDispatcher();
        tilt.conj();
        dispatcher.overrideCameraOrientation(tilt);
        dispatcher.setRenderShadow(false);
        IRenderTypeBuffer.Impl buffers = mc.renderBuffers().bufferSource();
        RenderSystem.runAsFancy(() ->
                dispatcher.render(preview, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, ms, buffers, 15728880));
        buffers.endBatch();
        dispatcher.setRenderShadow(true);
        RenderSystem.popMatrix();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
