package com.moonclaws.mod;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;

/**
 * Herb medicine ("herb poultice") made on the Healer's Stone.
 * Strength depends on the number of herbs and the cobweb ("bandage"); every kind of herb adds its own bonus once.
 */
public final class Medicine {

    public static final int DANDELION = 0;
    public static final int POPPY = 1;
    public static final int CORNFLOWER = 2;
    public static final int DAISY = 3;
    public static final int BERRIES = 4;
    public static final int KELP = 5;

    /** Seconds of each herb bonus. */
    public static final int BONUS_SECONDS = 20;
    /** Healer-made medicine lasts this much longer. */
    public static final double HEALER_FACTOR = 1.5D;

    private static final String T_HERBS = "Herbs";
    private static final String T_WEB = "Cobweb";
    private static final String T_HEALER = "Healer";

    private Medicine() {
    }

    public static int herbIndex(Item item) {
        if (item == Items.DANDELION) return DANDELION;
        if (item == Items.POPPY) return POPPY;
        if (item == Items.CORNFLOWER) return CORNFLOWER;
        if (item == Items.OXEYE_DAISY) return DAISY;
        if (item == Items.SWEET_BERRIES) return BERRIES;
        if (item == Items.KELP) return KELP;
        return -1;
    }

    public static boolean isHerb(ItemStack stack) {
        return !stack.isEmpty() && herbIndex(stack.getItem()) >= 0;
    }

    public static boolean isWeb(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.COBWEB;
    }

    public static boolean canUseStone(int rank) {
        return rank != CatState.RANK_NONE && rank != CatState.RANK_KIT && rank != CatState.RANK_APPRENTICE;
    }

    /** Regeneration for a recipe: {amplifier (0 = level I), seconds}. */
    public static int[] regen(int herbs, boolean web) {
        if (herbs <= 1) {
            return web ? new int[] {1, 6} : new int[] {0, 8};
        }
        if (herbs == 2) {
            return web ? new int[] {1, 12} : new int[] {0, 15};
        }
        return web ? new int[] {2, 8} : new int[] {1, 10};
    }

    /** Why nothing comes out (a lang key), or null if the recipe is fine / the stone is just empty. */
    public static String problem(int rank, int herbs, boolean web) {
        if (!canUseStone(rank)) {
            return "gui.moonclaws.healer.too_young";
        }
        if (herbs == 0) {
            return web ? "gui.moonclaws.healer.need_herb" : null;
        }
        if (rank != CatState.RANK_HEALER && (herbs > 1 || web)) {
            return "gui.moonclaws.healer.only_healer";
        }
        return null;
    }

    /** The poultice that the given ingredients make, or EMPTY. */
    public static ItemStack result(int rank, List<ItemStack> herbSlots, ItemStack webSlot) {
        List<Integer> herbs = new ArrayList<>();
        for (ItemStack s : herbSlots) {
            if (isHerb(s)) {
                herbs.add(herbIndex(s.getItem()));
            } else if (!s.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        boolean web = isWeb(webSlot);
        if (herbs.isEmpty() || problem(rank, herbs.size(), web) != null) {
            return ItemStack.EMPTY;
        }
        return make(herbs, web, rank == CatState.RANK_HEALER);
    }

    public static ItemStack make(List<Integer> herbs, boolean web, boolean healer) {
        ItemStack out = new ItemStack(MoonclawsMod.HERB_POULTICE.get());
        CompoundNBT tag = out.getOrCreateTag();
        byte[] ids = new byte[herbs.size()];
        // sorted, so the same herbs in any order stack together
        List<Integer> sorted = new ArrayList<>(herbs);
        sorted.sort(Integer::compareTo);
        for (int i = 0; i < ids.length; i++) {
            ids[i] = (byte) (int) sorted.get(i);
        }
        tag.putByteArray(T_HERBS, ids);
        tag.putBoolean(T_WEB, web);
        tag.putBoolean(T_HEALER, healer);
        return out;
    }

    public static List<Integer> herbsOf(ItemStack poultice) {
        List<Integer> out = new ArrayList<>();
        CompoundNBT tag = poultice.getTag();
        if (tag != null) {
            for (byte b : tag.getByteArray(T_HERBS)) {
                if (b >= 0 && b <= KELP) {
                    out.add((int) b);
                }
            }
        }
        return out;
    }

    public static boolean hasWeb(ItemStack poultice) {
        return poultice.getTag() != null && poultice.getTag().getBoolean(T_WEB);
    }

    public static boolean byHealer(ItemStack poultice) {
        return poultice.getTag() != null && poultice.getTag().getBoolean(T_HEALER);
    }

    public static Set<Integer> uniqueHerbs(ItemStack poultice) {
        return new LinkedHashSet<>(herbsOf(poultice));
    }

    /** Server side: the cat ate the poultice. */
    public static void apply(PlayerEntity player, ItemStack poultice) {
        List<Integer> herbs = herbsOf(poultice);
        if (herbs.isEmpty()) {
            return;
        }
        double factor = byHealer(poultice) ? HEALER_FACTOR : 1.0D;
        int[] r = regen(herbs.size(), hasWeb(poultice));
        player.addEffect(new EffectInstance(Effects.REGENERATION, (int) Math.round(r[1] * 20 * factor), r[0]));
        int bonusTicks = (int) Math.round(BONUS_SECONDS * 20 * factor);
        for (int herb : uniqueHerbs(poultice)) {
            switch (herb) {
                case DANDELION:
                    player.getFoodData().eat(2, 0.3F);
                    break;
                case POPPY:
                    player.removeEffect(Effects.POISON);
                    break;
                case CORNFLOWER:
                    player.addEffect(new EffectInstance(Effects.DAMAGE_RESISTANCE, bonusTicks, 0));
                    break;
                case DAISY:
                    player.removeEffect(Effects.WEAKNESS);
                    break;
                case BERRIES:
                    player.addEffect(new EffectInstance(Effects.MOVEMENT_SPEED, bonusTicks, 0));
                    break;
                case KELP:
                    player.addEffect(new EffectInstance(Effects.WATER_BREATHING, bonusTicks, 0));
                    break;
                default:
                    break;
            }
        }
    }
}
