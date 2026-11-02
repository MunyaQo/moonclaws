package com.moonclaws.mod.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.moonclaws.mod.CatState;
import com.moonclaws.mod.ClientCatData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Pose;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import com.moonclaws.mod.ModNetwork;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderNameplateEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
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
        // other players see the name above the cat all the time, like above a normal player
        cat.setCustomNameVisible(true);

        EntityRenderer<? super CatEntity> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(cat);
        if (p.isSleeping()) {
            // The game lifts a sleeping player to bed height and turns him like a human in a bed.
            // A cat curls up on its side in the hollow of the nest instead.
            cat.setLying(false);
            float yaw = p.getBedOrientation() != null ? p.getBedOrientation().toYRot() : p.yRot;
            double floor = p.getSleepingPos().map(pos -> pos.getY() + 0.125D).orElse(p.getY());
            MatrixStack ms = event.getMatrixStack();
            ms.pushPose();
            ms.translate(0.0D, floor - p.getY() + 0.08D, 0.0D);
            ms.mulPose(Vector3f.YP.rotationDegrees(-yaw));
            ms.mulPose(Vector3f.ZP.rotationDegrees(90.0F));
            ms.translate(0.0D, -0.35D, 0.0D);
            cat.yBodyRot = 0.0F;
            cat.yBodyRotO = 0.0F;
            cat.yHeadRot = 0.0F;
            cat.yHeadRotO = 0.0F;
            cat.yRot = 0.0F;
            cat.yRotO = 0.0F;
            cat.xRot = 0.0F;
            cat.xRotO = 0.0F;
            cat.animationSpeed = 0.0F;
            cat.animationSpeedOld = 0.0F;
            renderer.render(cat, 0.0F, event.getPartialRenderTick(), ms, event.getBuffers(), event.getLight());
            ms.popPose();
            return;
        }
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

    /** Silent steps: mute step and landing sounds that come from a cat-player (any player on this client). */
    @SubscribeEvent
    public void onPlaySound(PlaySoundEvent event) {
        ISound sound = event.getSound();
        Minecraft mc = Minecraft.getInstance();
        if (sound == null || mc.level == null) {
            return;
        }
        String path = sound.getLocation().getPath();
        if (!(path.endsWith(".step") || path.endsWith(".fall") || path.endsWith("_fall"))) {
            return;
        }
        for (PlayerEntity player : mc.level.players()) {
            if (CatState.isCat(player) && player.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) < 2.25D) {
                event.setResultSound(null);
                return;
            }
        }
    }

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        ClientCatData.clear();
        dummies.clear();
    }

    // ------------------------------------------------------------ healer: sparkles above herbs while crouching

    private static final int HERB_RADIUS = 16;

    private static boolean isHerbBlock(BlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.SWEET_BERRY_BUSH) {
            return state.getValue(SweetBerryBushBlock.AGE) >= 2;
        }
        return b == Blocks.DANDELION || b == Blocks.POPPY || b == Blocks.CORNFLOWER || b == Blocks.OXEYE_DAISY
                || b == Blocks.KELP || b == Blocks.KELP_PLANT;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) {
            return;
        }
        PlayerEntity p = mc.player;
        if (p.tickCount % 10 != 0 || !p.isCrouching() || !CatState.isCat(p)
                || CatState.getRank(p) != CatState.RANK_HEALER) {
            return;
        }
        BlockPos center = p.blockPosition();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int shown = 0;
        // every herb sparkles every time (steady, no random blinking); nearest rings first
        for (int ring = 0; ring <= HERB_RADIUS && shown < 120; ring++) {
            for (int dx = -ring; dx <= ring && shown < 120; dx++) {
                for (int dz = -ring; dz <= ring && shown < 120; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    for (int dy = -6; dy <= 6; dy++) {
                        pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                        if (isHerbBlock(mc.level.getBlockState(pos))) {
                            mc.level.addParticle(ParticleTypes.HAPPY_VILLAGER,
                                    pos.getX() + 0.2D + mc.level.random.nextDouble() * 0.6D,
                                    pos.getY() + 0.6D + mc.level.random.nextDouble() * 0.5D,
                                    pos.getZ() + 0.2D + mc.level.random.nextDouble() * 0.6D,
                                    0.0D, 0.02D, 0.0D);
                            shown++;
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ elder: Shift + right click with an empty paw

    @SubscribeEvent
    public void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (event.getHand() != Hand.MAIN_HAND) {
            return;
        }
        PlayerEntity p = event.getPlayer();
        if (p.isShiftKeyDown() && p.getMainHandItem().isEmpty() && CatState.isCat(p)
                && CatState.getRank(p) == CatState.RANK_ELDER) {
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.ElderMemoryPacket());
        }
    }
}
