package com.moonclaws.mod;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.IWorldPosCallable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

/** Healer's Stone: a flat mossy stone (half a block) where cats grind herbs into poultices. */
public class HealerStoneBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);

    public HealerStoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
                                BlockRayTraceResult hit) {
        if (world.isClientSide) {
            return ActionResultType.SUCCESS;
        }
        if (!(player instanceof ServerPlayerEntity)) {
            return ActionResultType.PASS;
        }
        ServerPlayerEntity sp = (ServerPlayerEntity) player;
        if (!CatState.isCat(sp)) {
            sp.displayClientMessage(new TranslationTextComponent("message.moonclaws.healer_human"), true);
            return ActionResultType.CONSUME;
        }
        if (!Medicine.canUseStone(CatState.getRank(sp))) {
            sp.displayClientMessage(new TranslationTextComponent("message.moonclaws.healer_young"), true);
            return ActionResultType.CONSUME;
        }
        NetworkHooks.openGui(sp, new SimpleNamedContainerProvider(
                (id, inventory, p) -> new HealerContainer(id, inventory, IWorldPosCallable.create(world, pos)),
                new TranslationTextComponent("block.moonclaws.healer_stone")));
        return ActionResultType.CONSUME;
    }
}
