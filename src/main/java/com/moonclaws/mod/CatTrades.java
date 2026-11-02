package com.moonclaws.mod;

import java.util.Arrays;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MerchantOffer;
import net.minecraft.item.MerchantOffers;

/** What clan cats (by rank) and stray cats trade. Payment is always prey. */
public final class CatTrades {

    private CatTrades() {
    }

    private static MerchantOffer offer(Item wants, int wantsCount, ItemStack gives, int maxUses) {
        return new MerchantOffer(new ItemStack(wants, wantsCount), ItemStack.EMPTY, gives, 0, maxUses, 0, 0.0F);
    }

    private static MerchantOffer offer(Item wants, int wantsCount, Item wants2, int wants2Count, ItemStack gives, int maxUses) {
        return new MerchantOffer(new ItemStack(wants, wantsCount), new ItemStack(wants2, wants2Count), gives, 0, maxUses, 0, 0.0F);
    }

    private static ItemStack of(Item item, int count) {
        return new ItemStack(item, count);
    }

    public static MerchantOffers forRank(int rank) {
        MerchantOffers o = new MerchantOffers();
        switch (rank) {
            case CatState.RANK_HEALER:
                o.add(offer(Items.COD, 1, of(Items.POPPY, 2), 6));
                o.add(offer(Items.RABBIT, 1, of(Items.CORNFLOWER, 2), 6));
                o.add(offer(Items.SALMON, 1, of(Items.OXEYE_DAISY, 2), 6));
                o.add(offer(Items.CHICKEN, 1, of(Items.COBWEB, 1), 4));
                o.add(offer(Items.RABBIT, 1, Items.COD, 1,
                        Medicine.make(Arrays.asList(Medicine.POPPY, Medicine.CORNFLOWER), true, true), 3));
                break;
            case CatState.RANK_WARRIOR:
                o.add(offer(Items.BEEF, 1, of(Items.LEATHER, 2), 6));
                o.add(offer(Items.CHICKEN, 1, of(Items.FEATHER, 4), 8));
                o.add(offer(Items.RABBIT, 1, of(Items.BONE, 2), 6));
                o.add(offer(Items.MUTTON, 1, of(Items.STRING, 3), 6));
                break;
            case CatState.RANK_APPRENTICE:
                o.add(offer(Items.CHICKEN, 1, of(Items.STICK, 6), 8));
                o.add(offer(Items.RABBIT, 1, of(Items.WHEAT_SEEDS, 4), 8));
                o.add(offer(Items.COD, 1, of(Items.FEATHER, 3), 8));
                o.add(offer(Items.CHICKEN, 1, of(Items.DANDELION, 3), 6));
                break;
            case CatState.RANK_ELDER:
                o.add(offer(Items.BEEF, 1, of(Items.NAME_TAG, 1), 1));
                o.add(offer(Items.RABBIT, 1, of(Items.GLASS_BOTTLE, 3), 4));
                o.add(offer(Items.SALMON, 2, of(Items.COMPASS, 1), 1));
                o.add(offer(Items.BEEF, 3, of(Items.MUSIC_DISC_CAT, 1), 1));
                break;
            case CatState.RANK_DEPUTY:
                o.add(offer(Items.BEEF, 1, of(Items.ARROW, 6), 6));
                o.add(offer(Items.PORKCHOP, 1, of(Items.LEATHER, 2), 6));
                o.add(offer(Items.MUTTON, 1, of(Items.STRING, 3), 6));
                break;
            default:
                break; // leader and kits do not trade (yet)
        }
        return o;
    }

    public static MerchantOffers stray() {
        MerchantOffers o = new MerchantOffers();
        o.add(offer(Items.CHICKEN, 1, of(Items.FEATHER, 3), 8));
        o.add(offer(Items.RABBIT, 1, of(Items.RABBIT_HIDE, 2), 8));
        o.add(offer(Items.SALMON, 1, of(Items.INK_SAC, 1), 8));
        o.add(offer(Items.BEEF, 1, of(Items.LEATHER, 2), 8));
        return o;
    }

    /** House things for wild things: 4 random offers from the list. */
    public static MerchantOffers kittypet(java.util.Random rnd) {
        java.util.List<MerchantOffer> all = new java.util.ArrayList<>();
        all.add(offer(Items.FEATHER, 3, of(Items.COOKED_COD, 2), 6));
        all.add(offer(Items.FEATHER, 2, of(Items.COOKED_SALMON, 1), 6));
        all.add(offer(Items.RABBIT_HIDE, 1, of(Items.BREAD, 2), 6));
        all.add(offer(Items.SWEET_BERRIES, 4, of(Items.COOKIE, 4), 6));
        all.add(offer(Items.FEATHER, 4, of(Items.STRING, 3), 6));
        all.add(offer(Items.RABBIT_HIDE, 2, of(Items.WHITE_WOOL, 2), 6));
        all.add(offer(Items.RABBIT_FOOT, 1, of(Items.LEATHER, 3), 4));
        all.add(offer(Items.SWEET_BERRIES, 6, of(Items.PAPER, 4), 6));
        return pick(all, rnd);
    }

    /** The witch's cat: herbs, poultice and "witch things" for herbs that don't grow in the swamp and cobweb. */
    public static MerchantOffers witchCat(java.util.Random rnd) {
        java.util.List<MerchantOffer> all = new java.util.ArrayList<>();
        all.add(offer(Items.CORNFLOWER, 2, of(Items.SUGAR, 3), 6));
        all.add(offer(Items.OXEYE_DAISY, 2, of(Items.GLASS_BOTTLE, 3), 6));
        all.add(offer(Items.COBWEB, 1, of(Items.GLOWSTONE_DUST, 2), 4));
        all.add(offer(Items.SWEET_BERRIES, 4, of(Items.SPIDER_EYE, 1), 4));
        all.add(offer(Items.OXEYE_DAISY, 1, of(Items.POPPY, 3), 6));
        all.add(offer(Items.CORNFLOWER, 1, Items.COBWEB, 1,
                Medicine.make(Arrays.asList(Medicine.POPPY, Medicine.DANDELION), true, true), 3));
        return pick(all, rnd);
    }

    private static MerchantOffers pick(java.util.List<MerchantOffer> all, java.util.Random rnd) {
        java.util.Collections.shuffle(all, rnd);
        MerchantOffers o = new MerchantOffers();
        for (int i = 0; i < 4 && i < all.size(); i++) {
            o.add(all.get(i));
        }
        return o;
    }
}
