package com.moonclaws.mod;

import net.minecraft.entity.merchant.IMerchant;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.inventory.container.MerchantContainer;

/** The normal trade logic of Minecraft, but with our own window type (birch-bark screen). */
public class CatTradeContainer extends MerchantContainer {

    /** Client side. */
    public CatTradeContainer(int id, PlayerInventory inventory) {
        super(id, inventory);
    }

    public CatTradeContainer(int id, PlayerInventory inventory, IMerchant merchant) {
        super(id, inventory, merchant);
    }

    @Override
    public ContainerType<?> getType() {
        return MoonclawsMod.CAT_TRADE.get();
    }
}
