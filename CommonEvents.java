package com.moonclaws.mod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.Pose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

/** Events that run on both sides / on the server. */
public class CommonEvents {

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            refresh((ServerPlayerEntity) event.getPlayer());
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            refresh((ServerPlayerEntity) event.getPlayer());
        }
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            refresh((ServerPlayerEntity) event.getPlayer());
        }
    }

    private static void refresh(ServerPlayerEntity sp) {
        CatState.applyAbilities(sp);
        sp.refreshDimensions();
        CatState.sync(sp);
    }

    /** Keep the cat data after death. */
    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        CompoundNBT old = event.getOriginal().getPersistentData();
        if (old.contains(PlayerEntity.PERSISTED_NBT_TAG)) {
            event.getPlayer().getPersistentData().put(PlayerEntity.PERSISTED_NBT_TAG,
                    old.getCompound(PlayerEntity.PERSISTED_NBT_TAG).copy());
        }
    }

    /** A player started to see another player: tell him about the other player's cat state. */
    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayerEntity && event.getPlayer() instanceof ServerPlayerEntity) {
            CatState.syncTo((ServerPlayerEntity) event.getTarget(), (ServerPlayerEntity) event.getPlayer());
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        PlayerEntity player = event.player;

        // Vanilla decides "stand or crawl" using the height of a human (1.8).
        // A cat is smaller: if it fits standing, keep the normal standing pose (no slowdown).
        // In half-block gaps it does not fit, so it keeps crawling (low pose).
        if (player.getPose() == Pose.SWIMMING && !player.isInWater() && !player.isSwimming()
                && CatState.isCat(player)) {
            AxisAlignedBB standingBox = new AxisAlignedBB(
                    player.getX() - 0.3D, player.getY(), player.getZ() - 0.3D,
                    player.getX() + 0.3D, player.getY() + 0.7D, player.getZ() + 0.3D);
            if (player.level.noCollision(player, standingBox)) {
                player.setPose(Pose.STANDING);
            }
        }

        if (event.side == LogicalSide.SERVER && player instanceof ServerPlayerEntity
                && player.tickCount % 10 == 0) {
            CatState.applyAbilities((ServerPlayerEntity) player);
        }
    }

    /** Cats land on their paws. */
    @SubscribeEvent
    public void onFall(LivingFallEvent event) {
        if (event.getEntityLiving() instanceof PlayerEntity && CatState.isCat((PlayerEntity) event.getEntityLiving())) {
            event.setDamageMultiplier(0.0F);
        }
    }

    /** Small cat body: fits into 1-block gaps. */
    @SubscribeEvent
    public void onSize(EntityEvent.Size event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof PlayerEntity)) {
            return;
        }
        if (!CatState.isCat((PlayerEntity) entity)) {
            return;
        }
        Pose pose = event.getPose();
        if (pose == Pose.STANDING) {
            event.setNewSize(EntitySize.scalable(0.6F, 0.7F));
            event.setNewEyeHeight(0.55F);
        } else if (pose == Pose.CROUCHING) {
            event.setNewSize(EntitySize.scalable(0.6F, 0.6F));
            event.setNewEyeHeight(0.5F);
        } else if (pose == Pose.SWIMMING) {
            // low crawling pose: fits under half-block gaps
            event.setNewSize(EntitySize.scalable(0.6F, 0.5F));
            event.setNewEyeHeight(0.4F);
        }
    }
}
