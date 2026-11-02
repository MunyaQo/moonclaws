package com.moonclaws.mod;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Herb poultice: eaten in a second, works only in cat form. Can be thrown to a friend like any item. */
public class HerbPoulticeItem extends Item {

    private static final String[] HERB_KEYS = {
            "item.minecraft.dandelion", "block.minecraft.poppy", "block.minecraft.cornflower",
            "block.minecraft.oxeye_daisy", "item.minecraft.sweet_berries", "item.minecraft.kelp"
    };

    public HerbPoulticeItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 20;
    }

    @Override
    public UseAction getUseAnimation(ItemStack stack) {
        return UseAction.EAT;
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!CatState.isCat(player)) {
            if (!world.isClientSide) {
                player.displayClientMessage(new TranslationTextComponent("message.moonclaws.poultice_human"), true);
            }
            return ActionResult.fail(stack);
        }
        player.startUsingItem(hand);
        return ActionResult.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, World world, LivingEntity entity) {
        if (!(entity instanceof PlayerEntity)) {
            return stack;
        }
        PlayerEntity player = (PlayerEntity) entity;
        if (!world.isClientSide && CatState.isCat(player)) {
            Medicine.apply(player, stack);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GENERIC_EAT, SoundCategory.PLAYERS, 0.6F, 1.3F);
        }
        if (!player.abilities.instabuild) {
            stack.shrink(1);
        }
        return stack;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        List<Integer> herbs = Medicine.herbsOf(stack);
        if (herbs.isEmpty()) {
            return;
        }
        // composition: "Poppy, Cornflower + cobweb"
        ITextComponent line = null;
        for (int herb : herbs) {
            ITextComponent name = new TranslationTextComponent(HERB_KEYS[herb]);
            if (line == null) {
                line = name.copy();
            } else {
                line = line.copy().append(", ").append(name);
            }
        }
        if (Medicine.hasWeb(stack)) {
            line = line.copy().append(" + ").append(new TranslationTextComponent("block.minecraft.cobweb"));
        }
        tooltip.add(line.copy().withStyle(TextFormatting.GRAY));

        double factor = Medicine.byHealer(stack) ? Medicine.HEALER_FACTOR : 1.0D;
        int[] r = Medicine.regen(herbs.size(), Medicine.hasWeb(stack));
        long seconds = Math.round(r[1] * factor);
        tooltip.add(new TranslationTextComponent("tooltip.moonclaws.regen",
                new TranslationTextComponent("enchantment.level." + (r[0] + 1)), seconds).withStyle(TextFormatting.GREEN));
        for (int herb : Medicine.uniqueHerbs(stack)) {
            tooltip.add(new TranslationTextComponent("tooltip.moonclaws.herb." + herb).withStyle(TextFormatting.DARK_GREEN));
        }
        if (Medicine.byHealer(stack)) {
            tooltip.add(new TranslationTextComponent("tooltip.moonclaws.by_healer").withStyle(TextFormatting.AQUA));
        }
        tooltip.add(new TranslationTextComponent("tooltip.moonclaws.cats_only").withStyle(TextFormatting.DARK_GRAY));
    }
}
