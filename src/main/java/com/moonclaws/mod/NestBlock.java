package com.moonclaws.mod;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.StateContainer;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

/**
 * Nest ("ворох"): a low pile of hay (or grass, feathers, fern) with a hollow in the middle.
 * Clan cats sleep on their own nest; a cat-player can sleep on a nest at night like on a bed.
 */
public class NestBlock extends Block {

    public static final int HAY = 0;
    public static final int GRASS = 1;
    public static final int FEATHER = 2;
    public static final int FERN = 3;

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D);
    /** Cats walk/lie on the soft bottom of the hollow, so the solid part is only 2 pixels high. */
    private static final VoxelShape COLLISION = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
    /** Height of the hollow's bottom (in blocks). */
    public static final double HOLLOW_Y = 2.0D / 16.0D;

    private final int kind;

    public NestBlock(int kind, Properties properties) {
        super(properties);
        this.kind = kind;
        this.registerDefaultState(this.stateDefinition.any().setValue(HorizontalBlock.FACING, Direction.NORTH));
    }

    public int getKind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        // a facing is needed because the game uses it to find where a sleeper stands up
        builder.add(HorizontalBlock.FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return this.defaultBlockState().setValue(HorizontalBlock.FACING, context.getHorizontalDirection());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        return COLLISION;
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
            sp.displayClientMessage(new TranslationTextComponent("message.moonclaws.nest_human"), true);
            return ActionResultType.CONSUME;
        }
        if (sp.isSleeping()) {
            return ActionResultType.CONSUME;
        }
        if (!sp.position().closerThan(Vector3d.atCenterOf(pos), 3.0D)) {
            return ActionResultType.CONSUME;
        }
        sp.setRespawnPosition(world.dimension(), pos, sp.yRot, false, true);
        if (world.isDay() && !world.isThundering()) {
            // in daytime the nest only remembers the cat (respawn point)
            return ActionResultType.CONSUME;
        }
        sp.startSleeping(pos);
        if (world instanceof ServerWorld) {
            ((ServerWorld) world).updateSleepingPlayerList();
        }
        return ActionResultType.CONSUME;
    }

    // ---------------------------------------------------------------- bed behaviour (Forge)

    @Override
    public boolean isBed(BlockState state, IBlockReader world, BlockPos pos, @Nullable Entity sleeper) {
        return sleeper == null || (sleeper instanceof PlayerEntity && CatState.isCat((PlayerEntity) sleeper));
    }

    @Override
    public void setBedOccupied(BlockState state, World world, BlockPos pos, LivingEntity sleeper, boolean occupied) {
        // nests have no "occupied" state
    }

    @Override
    public Direction getBedDirection(BlockState state, IWorldReader world, BlockPos pos) {
        return state.getValue(HorizontalBlock.FACING);
    }

    @Override
    public Optional<Vector3d> getRespawnPosition(BlockState state, EntityType<?> type, IWorldReader world, BlockPos pos,
                                                 float orientation, @Nullable LivingEntity entity) {
        // stand up right on the nest (it is low), or next to it if something is above
        if (world.getBlockState(pos.above()).getCollisionShape(world, pos.above()).isEmpty()
                && world.getBlockState(pos.above(2)).getCollisionShape(world, pos.above(2)).isEmpty()) {
            return Optional.of(new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.3D, pos.getZ() + 0.5D));
        }
        return net.minecraft.block.BedBlock.findStandUpPosition(type, world, pos, orientation);
    }
}
