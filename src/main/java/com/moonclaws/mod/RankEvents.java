package com.moonclaws.mod;

import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Class (rank) features: cat names, apprentice experience, leader's nine lives, elder's memory. */
public class RankEvents {

    private static final String K_LIFE_USED = "moonclaws_life_used";
    private static final String K_LIFE_MSG = "moonclaws_life_msg";

    /** In cat form the player is called by the cat name (chat, name above the head). */
    @SubscribeEvent
    public void onNameFormat(PlayerEvent.NameFormat event) {
        PlayerEntity p = event.getPlayer();
        if (!CatState.isCat(p)) {
            return;
        }
        String name = CatState.getCatName(p);
        if (!name.isEmpty()) {
            event.setDisplayname(new StringTextComponent(name));
        }
    }

    /** Apprentices learn faster: +25% experience. */
    @SubscribeEvent
    public void onXp(PlayerXpEvent.XpChange event) {
        PlayerEntity p = event.getPlayer();
        if (p.level.isClientSide || event.getAmount() <= 0) {
            return;
        }
        if (CatState.isCat(p) && CatState.getRank(p) == CatState.RANK_APPRENTICE) {
            double more = event.getAmount() * 1.25D;
            int whole = (int) more;
            if (p.getRandom().nextDouble() < more - whole) {
                whole++;
            }
            event.setAmount(whole);
        }
    }

    // ------------------------------------------------------------------ leader: nine lives

    private static boolean usesLife(PlayerEntity p) {
        return CatState.isCat(p) && CatState.getRank(p) == CatState.RANK_LEADER && CatState.getLives(p) > 0;
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity sp = (ServerPlayerEntity) event.getEntityLiving();
        if (!usesLife(sp)) {
            return;
        }
        CompoundNBT d = CatState.data(sp);
        d.putInt(CatState.K_LIVES, d.getInt(CatState.K_LIVES) - 1);
        d.putBoolean(K_LIFE_USED, true);
        CatState.save(sp, d);
    }

    /** StarClan keeps the leader's things: they are not dropped, they come back after respawn. */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        if (!(event.getEntityLiving() instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity sp = (ServerPlayerEntity) event.getEntityLiving();
        CompoundNBT d = CatState.data(sp);
        if (!d.getBoolean(K_LIFE_USED)) {
            return;
        }
        ListNBT list = new ListNBT();
        for (ItemEntity item : event.getDrops()) {
            ItemStack stack = item.getItem();
            if (!stack.isEmpty()) {
                list.add(stack.save(new CompoundNBT()));
            }
        }
        d.put(CatState.K_SAVED_ITEMS, list);
        CatState.save(sp, d);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onXpDrop(LivingExperienceDropEvent event) {
        if (event.getEntityLiving() instanceof ServerPlayerEntity
                && CatState.data((ServerPlayerEntity) event.getEntityLiving()).getBoolean(K_LIFE_USED)) {
            event.setCanceled(true);
        }
    }

    /** Runs after CommonEvents copied the cat data to the new player. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        PlayerEntity now = event.getPlayer();
        PlayerEntity old = event.getOriginal();
        CompoundNBT d = CatState.data(now);
        if (!d.getBoolean(K_LIFE_USED)) {
            return;
        }
        ListNBT list = d.getList(CatState.K_SAVED_ITEMS, 10);
        for (INBT tag : list) {
            ItemStack stack = ItemStack.of((CompoundNBT) tag);
            if (!stack.isEmpty() && !now.inventory.add(stack)) {
                now.drop(stack, false);
            }
        }
        now.experienceLevel = old.experienceLevel;
        now.totalExperience = old.totalExperience;
        now.experienceProgress = old.experienceProgress;
        d.remove(CatState.K_SAVED_ITEMS);
        d.remove(K_LIFE_USED);
        d.putBoolean(K_LIFE_MSG, true);
        CatState.save(now, d);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        PlayerEntity p = event.getPlayer();
        if (p.level.isClientSide) {
            return;
        }
        CompoundNBT d = CatState.data(p);
        if (!d.getBoolean(K_LIFE_MSG)) {
            return;
        }
        d.remove(K_LIFE_MSG);
        CatState.save(p, d);
        int lives = d.getInt(CatState.K_LIVES);
        if (lives > 0) {
            p.sendMessage(new TranslationTextComponent("message.moonclaws.life_used", lives)
                    .withStyle(TextFormatting.AQUA), p.getUUID());
        } else {
            p.sendMessage(new TranslationTextComponent("message.moonclaws.life_last")
                    .withStyle(TextFormatting.GOLD), p.getUUID());
        }
    }

    // ------------------------------------------------------------------ elder: memory

    private static final String[] DIRECTIONS = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};

    /** Once per game day: tells where the closest settlement is. */
    public static void elderMemory(ServerPlayerEntity sp) {
        if (!CatState.isCat(sp) || CatState.getRank(sp) != CatState.RANK_ELDER) {
            return;
        }
        CompoundNBT d = CatState.data(sp);
        long day = sp.level.getDayTime() / 24000L;
        if (d.contains(CatState.K_ELDER_DAY) && d.getLong(CatState.K_ELDER_DAY) == day) {
            sp.displayClientMessage(new TranslationTextComponent("message.moonclaws.elder_tired"), true);
            return;
        }
        BlockPos target = SettlementData.get(sp.getLevel()).nearest(sp.getX(), sp.getZ());
        if (target == null) {
            sp.sendMessage(new TranslationTextComponent("message.moonclaws.elder_none")
                    .withStyle(TextFormatting.GRAY), sp.getUUID());
        } else {
            double dx = target.getX() + 0.5D - sp.getX();
            double dz = target.getZ() + 0.5D - sp.getZ();
            int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            // Minecraft: +Z is south, +X is east
            double angle = Math.toDegrees(Math.atan2(-dx, dz)); // 0 = south, 90 = west, 180 = north
            int index = (int) Math.floor(((angle + 360.0D + 22.5D) % 360.0D) / 45.0D);
            TranslationTextComponent dir = new TranslationTextComponent("direction.moonclaws." + DIRECTIONS[index]);
            sp.sendMessage(new TranslationTextComponent("message.moonclaws.elder_memory", dir, distance)
                    .withStyle(TextFormatting.GRAY), sp.getUUID());
        }
        d.putLong(CatState.K_ELDER_DAY, day);
        CatState.save(sp, d);
    }
}
