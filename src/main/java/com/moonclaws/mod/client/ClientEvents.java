package com.moonclaws.mod.client;

import com.moonclaws.mod.CatState;
import com.moonclaws.mod.ClientCatData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Pose;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderNameplateEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Client events: draws the cat instead of the player, hides the hand, highlights the hotbar slot. */
public class ClientEvents {

    /** One invisible "dummy" vanilla cat per cat-player. It is never added to the world, only rendered. */
    private final Map<UUID, CatEntity> dummies = new HashMap<>();

    @SubscribeEvent
    public void onRenderPlayer(RenderPlayerEvent.Pre event) {
        PlayerEntity p = event.getPlayer();
        ClientCatData.Info info = ClientCatData.get(p.getUUID());
        if (info == null || !info.cat) {
            return;
        }
        event.setCanceled(true);
        if (p.isInvisible()) {
            return;
        }

        CatEntity cat = dummies.get(p.getUUID());
        if (cat == null || cat.level != p.level) {
            cat = new CatEntity(EntityType.CAT, p.level);
            dummies.put(p.getUUID(), cat);
        }

        cat.setCatType(Math.max(0, info.skin));

        // position and rotation
        cat.absMoveTo(p.getX(), p.getY(), p.getZ(), p.yRot, p.xRot);
        cat.yRotO = p.yRotO;
        cat.xRotO = p.xRotO;
        cat.yBodyRot = p.yBodyRot;
        cat.yBodyRotO = p.yBodyRotO;
        cat.yHeadRot = p.yHeadRot;
        cat.yHeadRotO = p.yHeadRotO;

        // walking animation and state
        cat.animationPosition = p.animationPosition;
        cat.animationSpeed = p.animationSpeed;
        cat.animationSpeedOld = p.animationSpeedOld;
        cat.tickCount = p.tickCount;
        cat.hurtTime = p.hurtTime;
        cat.deathTime = p.deathTime;
        cat.setSprinting(p.isSprinting());
        cat.setShiftKeyDown(p.isShiftKeyDown());
        cat.setPose(p.isCrouching() ? Pose.CROUCHING : Pose.STANDING);
        cat.setCustomName(p.getDisplayName());

        EntityRenderer<? super CatEntity> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(cat);
        renderer.render(cat, p.yRot, event.getPartialRenderTick(), event.getMatrixStack(), event.getBuffers(), event.getLight());
    }

    /** No name tag above your own cat in third person view. */
    @SubscribeEvent
    public void onNameplate(RenderNameplateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && event.getEntity() == dummies.get(mc.player.getUUID())) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** Hide the hand in first person while being a cat. */
    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && CatState.isCat(mc.player)) {
            event.setCanceled(true);
        }
    }

    /** Blue highlight on the selected hotbar slot while being a cat. */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.HOTBAR) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !CatState.isCat(mc.player)) {
            return;
        }
        int selected = mc.player.inventory.selected;
        int x = event.getWindow().getGuiScaledWidth() / 2 - 88 + selected * 20;
        int y = event.getWindow().getGuiScaledHeight() - 19;
        AbstractGui.fill(event.getMatrixStack(), x, y, x + 16, y + 16, 0x6655AAFF);
    }

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        ClientCatData.clear();
        dummies.clear();
    }
}
