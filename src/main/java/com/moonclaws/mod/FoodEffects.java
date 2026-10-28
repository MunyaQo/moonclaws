package com.moonclaws.mod;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;

/** Weak short effects for food, the same for every food item no matter how it was obtained. */
public final class FoodEffects {

    private static final class Spec {
        final Effect effect;
        final int ticks;

        Spec(Effect effect, int ticks) {
            this.effect = effect;
            this.ticks = ticks;
        }
    }

    private static Map<Item, Spec> specs;

    private FoodEffects() {
    }

    private static void add(Effect effect, int seconds, Item... items) {
        for (Item item : items) {
            specs.put(item, new Spec(effect, seconds * 20));
        }
    }

    private static void init() {
        specs = new HashMap<>();
        // fish: faster swimming
        add(Effects.DOLPHINS_GRACE, 30,
                Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.COOKED_COD, Items.COOKED_SALMON);
        // small game: speed
        add(Effects.MOVEMENT_SPEED, 20,
                Items.RABBIT, Items.COOKED_RABBIT, Items.CHICKEN, Items.COOKED_CHICKEN);
        // red meat: strength (shortest, because it is the strongest)
        add(Effects.DAMAGE_BOOST, 15,
                Items.BEEF, Items.COOKED_BEEF, Items.PORKCHOP, Items.COOKED_PORKCHOP,
                Items.MUTTON, Items.COOKED_MUTTON);
        // plant food: a little regeneration
        add(Effects.REGENERATION, 6,
                Items.BREAD, Items.CARROT, Items.POTATO, Items.BAKED_POTATO,
                Items.SWEET_BERRIES, Items.APPLE);
    }

    public static void apply(ServerPlayerEntity player, ItemStack eaten) {
        if (specs == null) {
            init();
        }
        Spec spec = specs.get(eaten.getItem());
        if (spec != null) {
            player.addEffect(new EffectInstance(spec.effect, spec.ticks, 0));
        }
    }
}
