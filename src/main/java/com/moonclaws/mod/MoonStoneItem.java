package com.moonclaws.mod;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.PacketDistributor;

/** Moon Stone: right click toggles between human and cat form. */
public class MoonStoneItem extends Item {

    public MoonStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!world.isClientSide && player instanceof ServerPlayerEntity) {
            ServerPlayerEntity sp = (ServerPlayerEntity) player;
            if (CatState.isCat(sp)) {
                CatState.setCat(sp, false);
            } else if (CatState.getClan(sp) == 0) {
                // first transformation: ask the player to choose a clan
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new ModNetwork.OpenClanScreenPacket());
            } else {
                CatState.setCat(sp, true);
            }
            player.getCooldowns().addCooldown(this, 20);
        }
        return ActionResult.sidedSuccess(stack, world.isClientSide());
    }
}
