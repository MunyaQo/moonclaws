package com.moonclaws.mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Life around settlements: groups appear in the world, the camp name shows when a player walks in,
 * and strangers who walk into private dens are warned twice and then pushed out (no damage).
 */
public class ClanEvents {

    private static final int WARN_DELAY = 100; // 5 seconds between steps

    private static final class Visit {
        int settlement = -1;
        int stage;
        long lastStepTick;
        boolean insideDen;
    }

    private final Map<UUID, Visit> visits = new HashMap<>();

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof ServerWorld)
                || event.world.dimension() != World.OVERWORLD) {
            return;
        }
        ServerWorld world = (ServerWorld) event.world;
        long time = world.getGameTime();
        if (time % 40 == 0) {
            ClanWorldgen.tick(world);
        }
        if (time % 10 != 0) {
            return;
        }
        SettlementData data = SettlementData.get(world);
        for (ServerPlayerEntity p : world.players()) {
            Visit v = visits.computeIfAbsent(p.getUUID(), id -> new Visit());
            SettlementData.Settlement s = data.settlementNear(p.getX(), p.getZ(), 16.0D);
            if (s == null) {
                v.settlement = -1;
                v.stage = 0;
                v.insideDen = false;
                continue;
            }
            if (v.settlement != s.id) {
                v.settlement = s.id;
                v.stage = 0;
                v.insideDen = false;
                p.displayClientMessage(new TranslationTextComponent("message.moonclaws.enter_camp",
                        new TranslationTextComponent("clan.moonclaws." + s.clan), s.leaderName)
                        .withStyle(TextFormatting.GOLD), true);
            }
            if (!CatState.isCat(p) || p.isSpectator() || p.isCreative()) {
                continue;
            }
            SettlementData.Den den = s.denAt(p.getX(), p.getY(), p.getZ());
            if (den == null || !den.isPrivate()) {
                v.insideDen = false;
                continue;
            }
            if (!v.insideDen) {
                v.insideDen = true;
                if (v.stage == 0) {
                    step(world, p, s, v, time);
                }
                continue;
            }
            if (time - v.lastStepTick >= WARN_DELAY) {
                step(world, p, s, v, time);
            }
        }
    }

    /** Next reaction of the camp: warning, harder warning, then a push out of the den. */
    private void step(ServerWorld world, ServerPlayerEntity p, SettlementData.Settlement s, Visit v, long time) {
        List<ClanCatEntity> watchers = world.getEntitiesOfClass(ClanCatEntity.class, p.getBoundingBox().inflate(16.0D),
                c -> c.getSettlementId() == s.id && c.isAlive() && !c.isLying());
        if (watchers.isEmpty()) {
            return; // nobody sees the stranger
        }
        ClanCatEntity cat = watchers.get(0);
        v.lastStepTick = time;
        if (v.stage == 0) {
            p.sendMessage(new TranslationTextComponent("message.moonclaws.den_warn_1", cat.getCatName())
                    .withStyle(TextFormatting.YELLOW), p.getUUID());
            v.stage = 1;
        } else if (v.stage == 1) {
            p.sendMessage(new TranslationTextComponent("message.moonclaws.den_warn_2", cat.getCatName())
                    .withStyle(TextFormatting.RED), p.getUUID());
            for (ClanCatEntity c : watchers) {
                c.playSound(SoundEvents.CAT_HISS, 1.0F, 1.0F);
            }
            v.stage = 2;
        } else {
            // push out towards the camp center, no damage
            Vector3d toCenter = new Vector3d(s.center.getX() + 0.5D - p.getX(), 0.0D, s.center.getZ() + 0.5D - p.getZ());
            if (toCenter.lengthSqr() > 0.01D) {
                toCenter = toCenter.normalize().scale(1.1D);
            }
            p.setDeltaMovement(toCenter.x, 0.35D, toCenter.z);
            p.hurtMarked = true;
            cat.playSound(SoundEvents.CAT_HISS, 1.0F, 0.8F);
            p.displayClientMessage(new TranslationTextComponent("message.moonclaws.den_push", cat.getCatName())
                    .withStyle(TextFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        visits.remove(event.getPlayer().getUUID());
    }
}
