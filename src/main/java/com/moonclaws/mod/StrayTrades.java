package com.moonclaws.mod;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Demo barter with stray cats: the player brings prey, the cat gives things. */
public final class StrayTrades {

    public static final class Trade {
        public final Item wants;
        public final int wantsCount;
        public final Item gives;
        public final int givesCount;

        Trade(Item wants, int wantsCount, Item gives, int givesCount) {
            this.wants = wants;
            this.wantsCount = wantsCount;
            this.gives = gives;
            this.givesCount = givesCount;
        }
    }

    private static Trade[] trades;

    private StrayTrades() {
    }

    public static Trade[] get() {
        if (trades == null) {
            trades = new Trade[] {
                    new Trade(Items.CHICKEN, 1, Items.FEATHER, 3),
                    new Trade(Items.RABBIT, 1, Items.RABBIT_HIDE, 2),
                    new Trade(Items.SALMON, 1, Items.INK_SAC, 1),
                    new Trade(Items.BEEF, 1, Items.LEATHER, 2)
            };
        }
        return trades;
    }

    private static int count(PlayerEntity player, Item item) {
        int total = 0;
        for (int i = 0; i < player.inventory.getContainerSize(); i++) {
            ItemStack s = player.inventory.getItem(i);
            if (s.getItem() == item) {
                total += s.getCount();
            }
        }
        return total;
    }

    private static void take(PlayerEntity player, Item item, int amount) {
        int left = amount;
        for (int i = 0; i < player.inventory.getContainerSize() && left > 0; i++) {
            ItemStack s = player.inventory.getItem(i);
            if (s.getItem() == item) {
                int n = Math.min(left, s.getCount());
                s.shrink(n);
                left -= n;
            }
        }
    }

    /** Server side: do the trade if the player has what the cat wants. */
    public static boolean execute(PlayerEntity player, int index) {
        Trade[] all = get();
        if (index < 0 || index >= all.length) {
            return false;
        }
        Trade t = all[index];
        if (count(player, t.wants) < t.wantsCount) {
            return false;
        }
        take(player, t.wants, t.wantsCount);
        ItemStack reward = new ItemStack(t.gives, t.givesCount);
        if (!player.inventory.add(reward)) {
            player.drop(reward, false);
        }
        return true;
    }
}
