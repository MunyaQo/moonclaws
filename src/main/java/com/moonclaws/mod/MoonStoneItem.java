package com.moonclaws.mod;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.PacketDistributor;

/**
 * Moon Stone: right click toggles between human and cat form.
 * Shift + right click opens the cat card (sex, clan, class, coat, name).
 * The very first time (or for cats from older versions) the card opens by itself.
 */
public class MoonStoneItem extends Item {

    public MoonStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!world.isClientSide && player instanceof ServerPlayerEntity) {
            ServerPlayerEntity sp = (ServerPlayerEntity) player;
            if (sp.isShiftKeyDown() || !CatState.isCardDone(sp)) {
                openCard(sp);
            } else {
                CatState.setCat(sp, !CatState.isCat(sp));
            }
            player.getCooldowns().addCooldown(this, 20);
        }
        return ActionResult.sidedSuccess(stack, world.isClientSide());
    }

    public static void openCard(ServerPlayerEntity sp) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                new ModNetwork.OpenCardPacket(CatState.readCard(sp)));
    }
}
