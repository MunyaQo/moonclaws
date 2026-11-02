package com.moonclaws.mod;

import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ILivingEntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.IServerWorld;
import net.minecraft.world.World;

/**
 * A stray cat: looks like a vanilla cat, does not run away from players,
 * and trades with cat-players (right click with an empty paw) in the birch-bark window.
 */
public class StrayCatEntity extends CatEntity {

    /** Rare skins: british shorthair, calico, persian, ragdoll, jellie. */
    public static final int[] RARE_SKINS = {4, 5, 6, 7, 9};
    /** Common skins: tabby, tuxedo, red, siamese, white, all black. */
    public static final int[] COMMON_SKINS = {0, 1, 2, 3, 8, 10};

    private final CatMerchant merchant = new CatMerchant(this);

    public StrayCatEntity(EntityType<? extends CatEntity> type, World world) {
        super(type, world);
        merchant.setOffers(CatTrades.stray());
        this.goalSelector.addGoal(0, new TradeStillGoal(this, merchant));
    }

    /** 90% rare skin, 10% common skin. */
    public static int pickSkin(Random random) {
        if (random.nextInt(10) == 0) {
            return COMMON_SKINS[random.nextInt(COMMON_SKINS.length)];
        }
        return RARE_SKINS[random.nextInt(RARE_SKINS.length)];
    }

    @Override
    public ILivingEntityData finalizeSpawn(IServerWorld world, DifficultyInstance difficulty, SpawnReason reason,
                                           @Nullable ILivingEntityData data, @Nullable CompoundNBT nbt) {
        ILivingEntityData result = super.finalizeSpawn(world, difficulty, reason, data, nbt);
        this.setCatType(pickSkin(this.random));
        return result;
    }

    /**
     * Vanilla wild cats get an "avoid players" goal here. Stray cats are neutral and let you come close,
     * so this does nothing.
     */
    @Override
    protected void reassessTameGoals() {
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level.isClientSide) {
            merchant.tick();
        }
    }

    @Override
    public ActionResultType mobInteract(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND || !player.getItemInHand(hand).isEmpty()) {
            return super.mobInteract(player, hand);
        }
        if (level.isClientSide) {
            return ActionResultType.SUCCESS;
        }
        if (!(player instanceof ServerPlayerEntity)) {
            return ActionResultType.PASS;
        }
        ServerPlayerEntity sp = (ServerPlayerEntity) player;
        if (!CatState.isCat(sp)) {
            // stray cats don't trade with humans
            playSound(SoundEvents.CAT_HISS, 1.0F, 1.0F);
            sp.displayClientMessage(new TranslationTextComponent("message.moonclaws.stray_hiss"), true);
            return ActionResultType.CONSUME;
        }
        if (!merchant.isTrading()) {
            getNavigation().stop();
            merchant.open(sp, new TranslationTextComponent("entity.moonclaws.stray_cat"));
        }
        return ActionResultType.CONSUME;
    }

    @Override
    public void addAdditionalSaveData(CompoundNBT tag) {
        super.addAdditionalSaveData(tag);
        tag.put("MoonclawsTrade", merchant.save());
    }

    @Override
    public void readAdditionalSaveData(CompoundNBT tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("MoonclawsTrade")) {
            merchant.load(tag.getCompound("MoonclawsTrade"));
        }
        if (!merchant.hasOffers()) {
            merchant.setOffers(CatTrades.stray());
        }
    }
}
