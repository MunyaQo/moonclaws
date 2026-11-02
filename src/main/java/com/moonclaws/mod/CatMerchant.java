package com.moonclaws.mod;

import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.merchant.IMerchant;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MerchantOffer;
import net.minecraft.item.MerchantOffers;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;

/**
 * Trading part of a cat (clan cat or stray cat). Works with the normal villager-style trade logic,
 * but the payment is prey and the window is our birch-bark one.
 * Offers have limits and are restocked at dawn. A deputy/elder player gets a few more items.
 */
public class CatMerchant implements IMerchant {

    private final MobEntity owner;
    private MerchantOffers base = new MerchantOffers();
    @Nullable
    private MerchantOffers view;
    @Nullable
    private PlayerEntity trading;
    private long lastDay = -1;

    public CatMerchant(MobEntity owner) {
        this.owner = owner;
    }

    public void setOffers(MerchantOffers offers) {
        this.base = offers;
    }

    public boolean hasOffers() {
        return !base.isEmpty();
    }

    /** Restock all offers once per game day (at the first look after dawn). */
    public void restockIfNewDay() {
        long day = owner.level.getDayTime() / 24000L;
        if (day != lastDay) {
            for (MerchantOffer o : base) {
                o.resetUses();
            }
            lastDay = day;
        }
    }

    /** Server: open the trade window for a player. */
    public void open(ServerPlayerEntity player, ITextComponent title) {
        restockIfNewDay();
        int bonus = CatState.tradeBonus(CatState.getRank(player));
        view = new MerchantOffers();
        for (MerchantOffer o : base) {
            ItemStack result = o.getResult().copy();
            result.setCount(Math.min(result.getMaxStackSize(), result.getCount() + bonus));
            view.add(new MerchantOffer(o.getBaseCostA().copy(), o.getCostB().copy(), result,
                    o.getUses(), o.getMaxUses(), 0, 0.0F));
        }
        setTradingPlayer(player);
        OptionalInt id = player.openMenu(new SimpleNamedContainerProvider(
                (windowId, inventory, p) -> new CatTradeContainer(windowId, inventory, this), title));
        if (id.isPresent()) {
            player.sendMerchantOffers(id.getAsInt(), view, 0, 0, false, true);
        } else {
            setTradingPlayer(null);
        }
    }

    /** Close the window if the player walked away. */
    public void tick() {
        if (trading != null && (!trading.isAlive() || trading.distanceToSqr(owner) > 64.0D)) {
            if (trading instanceof ServerPlayerEntity) {
                ((ServerPlayerEntity) trading).closeContainer();
            }
            trading = null;
        }
    }

    public boolean isTrading() {
        return trading != null;
    }

    @Override
    public void setTradingPlayer(@Nullable PlayerEntity player) {
        this.trading = player;
        if (player == null) {
            view = null;
        }
    }

    @Nullable
    @Override
    public PlayerEntity getTradingPlayer() {
        return trading;
    }

    @Override
    public MerchantOffers getOffers() {
        return view != null ? view : base;
    }

    @Override
    public void overrideOffers(@Nullable MerchantOffers offers) {
        // client side copy, not used
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        if (view != null) {
            int index = view.indexOf(offer);
            if (index >= 0 && index < base.size()) {
                base.get(index).increaseUses();
            }
        }
        owner.playSound(SoundEvents.CAT_PURREOW, 0.8F, 1.0F);
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public World getLevel() {
        return owner.level;
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.CAT_PURREOW;
    }

    @Override
    public boolean canRestock() {
        return true;
    }

    public CompoundNBT save() {
        CompoundNBT tag = new CompoundNBT();
        tag.put("Offers", base.createTag());
        tag.putLong("LastDay", lastDay);
        return tag;
    }

    public void load(CompoundNBT tag) {
        if (tag.contains("Offers")) {
            base = new MerchantOffers(tag.getCompound("Offers"));
        }
        lastDay = tag.getLong("LastDay");
    }
}
