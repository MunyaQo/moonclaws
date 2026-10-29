package com.moonclaws.mod;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Spawn egg of the stray cat (a simple own item, spawns the cat on the clicked block). */
public class StrayCatEggItem extends Item {

    public StrayCatEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public ActionResultType useOn(ItemUseContext context) {
        World world = context.getLevel();
        if (world.isClientSide) {
            return ActionResultType.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        StrayCatEntity cat = ModEntities.STRAY_CAT.get().create(world);
        if (cat == null) {
            return ActionResultType.FAIL;
        }
        cat.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, world.random.nextFloat() * 360.0F, 0.0F);
        cat.setCatType(StrayCatEntity.RARE_SKINS[world.random.nextInt(StrayCatEntity.RARE_SKINS.length)]);
        cat.setPersistenceRequired();
        world.addFreshEntity(cat);
        ItemStack stack = context.getItemInHand();
        if (context.getPlayer() == null || !context.getPlayer().abilities.instabuild) {
            stack.shrink(1);
        }
        return ActionResultType.SUCCESS;
    }
}
