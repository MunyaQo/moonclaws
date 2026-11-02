package com.moonclaws.mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.RotatedPillarBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.state.properties.SlabType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

/**
 * Swamp / dark forest look for a freshly built "young clan" camp: puddles and channels on the floor
 * with lily pads and reeds, boardwalks of spruce slabs to the dens, dark oak roots instead of the thorn hedge
 * (with entrances in the south and north), dark oak den frames with hanging vines, mossy den walls,
 * podzol and many mushrooms, the meeting rock half in water.
 */
public final class SwampStyle {

    private SwampStyle() {
    }

    private static BlockState darkLog(Direction.Axis axis) {
        return Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    private static BlockState darkLeaves() {
        return Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, Boolean.TRUE);
    }

    /** Places on the floor that must stay dry: entrance path, center, campfire area, den mouths. */
    private static boolean reserved(int dx, int dz) {
        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
            return true;                                   // center
        }
        if (Math.abs(dx) <= 1 && dz >= 2) {
            return true;                                   // entrance path (south)
        }
        if (dx >= 1 && dx <= 4 && dz >= -2 && dz <= 2) {
            return true;                                   // campfire, benches, hay
        }
        int[][] mouths = {{4, -3}, {4, 3}, {-4, -3}, {-4, 3}, {-3, -4}, {3, -4}};
        for (int[] m : mouths) {
            if (Math.abs(dx - m[0]) <= 1 && Math.abs(dz - m[1]) <= 1) {
                return true;                               // in front of den tunnels
            }
        }
        return false;
    }

    public static void apply(ServerWorld world, SettlementBuilder.Layout layout) {
        BlockPos c = layout.center;
        int cx = c.getX();
        int cz = c.getZ();
        int g = c.getY();
        int floorTop = g - 4;
        Random rnd = new Random(c.asLong() ^ 0x5357414DL);
        BlockPos.Mutable p = new BlockPos.Mutable();

        // 1. dark oak instead of oak everywhere in the camp; terraces partly podzol
        for (int dx = -14; dx <= 14; dx++) {
            for (int dz = -14; dz <= 14; dz++) {
                for (int y = g - 8; y <= g + 3; y++) {
                    p.set(cx + dx, y, cz + dz);
                    BlockState s = world.getBlockState(p);
                    Block b = s.getBlock();
                    if (b == Blocks.OAK_LOG) {
                        world.setBlock(p, darkLog(s.getValue(RotatedPillarBlock.AXIS)), 2);
                    } else if (b == Blocks.OAK_LEAVES) {
                        world.setBlock(p, darkLeaves(), 2);
                    } else if (b == Blocks.GRASS_BLOCK && rnd.nextInt(3) == 0) {
                        world.setBlock(p, Blocks.PODZOL.defaultBlockState(), 2);
                    }
                }
            }
        }

        // 2. the hedge ring becomes roots: dark oak logs lying around the camp, leaves on top; north entrance too
        for (int dx = -11; dx <= 11; dx++) {
            for (int dz = -11; dz <= 11; dz++) {
                int r2 = dx * dx + dz * dz;
                if (r2 <= 81 || r2 > 110) {
                    continue;
                }
                if (Math.abs(dx) <= 1) {
                    // gaps for the south and the north entrance
                    p.set(cx + dx, g + 1, cz + dz);
                    world.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    world.setBlock(p.above(), Blocks.AIR.defaultBlockState(), 2);
                    continue;
                }
                if (rnd.nextInt(5) < 3) {
                    Direction.Axis tangent = Math.abs(dx) > Math.abs(dz) ? Direction.Axis.Z : Direction.Axis.X;
                    world.setBlock(new BlockPos(cx + dx, g + 1, cz + dz), darkLog(tangent), 2);
                }
            }
        }
        // a path down from the north entrance (coarse dirt on the terraces)
        for (int dz = -12; dz <= -5; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int top = world.getHeight(net.minecraft.world.gen.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx + dx, cz + dz) - 1;
                if (dx == 0 && top <= g) {
                    world.setBlock(new BlockPos(cx, top, cz + dz), Blocks.COARSE_DIRT.defaultBlockState(), 2);
                }
            }
        }

        // 3. puddles and channels on the floor
        List<int[]> water = new ArrayList<>();
        double[][] blobs = new double[5][3];
        for (double[] b : blobs) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = 1.5D + rnd.nextDouble() * 3.0D;
            b[0] = Math.cos(a) * r;
            b[1] = Math.sin(a) * r;
            b[2] = 1.3D + rnd.nextDouble() * 1.2D;
        }
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (dx * dx + dz * dz > 24 || reserved(dx, dz)) {
                    continue;
                }
                boolean wet = false;
                for (double[] b : blobs) {
                    double ddx = dx - b[0];
                    double ddz = dz - b[1];
                    if (ddx * ddx + ddz * ddz <= b[2] * b[2]) {
                        wet = true;
                    }
                }
                p.set(cx + dx, floorTop + 1, cz + dz);
                if (wet && world.isEmptyBlock(p)) {
                    world.setBlock(new BlockPos(cx + dx, floorTop, cz + dz), Blocks.WATER.defaultBlockState(), 2);
                    water.add(new int[] {dx, dz});
                }
            }
        }
        // the meeting rock stands half in water
        for (int dx = -5; dx <= -1; dx++) {
            for (int dz = -3; dz <= 2; dz++) {
                p.set(cx + dx, floorTop + 1, cz + dz);
                if (!reserved(dx, dz) && dx * dx + dz * dz <= 24 && world.isEmptyBlock(p) && rnd.nextInt(3) != 0) {
                    world.setBlock(new BlockPos(cx + dx, floorTop, cz + dz), Blocks.WATER.defaultBlockState(), 2);
                    water.add(new int[] {dx, dz});
                }
            }
        }
        // lily pads, reeds at the edges, boardwalks over water near the den mouths
        for (int[] w : water) {
            BlockPos at = new BlockPos(cx + w[0], floorTop, cz + w[1]);
            if (world.getBlockState(at).getBlock() != Blocks.WATER) {
                continue;
            }
            boolean nearMouth = Math.abs(w[0]) >= 3 || Math.abs(w[1]) >= 3;
            if (nearMouth && rnd.nextInt(2) == 0) {
                world.setBlock(at, Blocks.SPRUCE_SLAB.defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.BOTTOM).setValue(SlabBlock.WATERLOGGED, Boolean.TRUE), 2);
            } else if (rnd.nextInt(3) == 0) {
                world.setBlock(at.above(), Blocks.LILY_PAD.defaultBlockState(), 2);
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos side = at.relative(d);
                BlockState sb = world.getBlockState(side);
                if ((sb.getBlock() == Blocks.DIRT || sb.getBlock() == Blocks.PODZOL || sb.getBlock() == Blocks.COARSE_DIRT
                        || sb.getBlock() == Blocks.GRASS_BLOCK)
                        && world.isEmptyBlock(side.above()) && rnd.nextInt(7) == 0) {
                    world.setBlock(side.above(), Blocks.SUGAR_CANE.defaultBlockState(), 2);
                    if (rnd.nextBoolean() && world.isEmptyBlock(side.above(2))) {
                        world.setBlock(side.above(2), Blocks.SUGAR_CANE.defaultBlockState(), 2);
                    }
                }
            }
        }

        // 4. dens: vines over the entrances, mossy walls, podzol floor
        for (SettlementData.Den den : layout.dens) {
            for (int x = den.min.getX() - 1; x <= den.max.getX() + 1; x++) {
                for (int z = den.min.getZ() - 1; z <= den.max.getZ() + 1; z++) {
                    for (int y = den.min.getY() - 1; y <= den.max.getY() + 1; y++) {
                        p.set(x, y, z);
                        BlockState s = world.getBlockState(p);
                        if (y == den.min.getY() - 1 && s.getBlock() == Blocks.DIRT) {
                            world.setBlock(p, Blocks.PODZOL.defaultBlockState(), 2);
                        } else if ((s.getBlock() == Blocks.DIRT || s.getBlock() == Blocks.COARSE_DIRT) && rnd.nextInt(3) == 0) {
                            world.setBlock(p, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
        int[][] dens = {{1, 0, -3}, {1, 0, 3}, {-1, 0, -3}, {-1, 0, 3}, {0, -1, -3}, {0, -1, 3}};
        for (int[] d : dens) {
            int perpX = Math.abs(d[1]);
            int perpZ = Math.abs(d[0]);
            Direction toDen = Direction.fromNormal(d[0], 0, d[1]);
            for (int s = -1; s <= 1; s++) {
                BlockPos front = new BlockPos(cx + d[0] * 4 + perpX * (d[2] + s), g - 2, cz + d[1] * 4 + perpZ * (d[2] + s));
                if (world.isEmptyBlock(front) && s != 0) {
                    world.setBlock(front, Blocks.VINE.defaultBlockState()
                            .setValue(VineBlock.getPropertyForFace(toDen), Boolean.TRUE), 2);
                }
            }
        }

        // 5. more mushrooms on podzol
        for (int i = 0; i < 14; i++) {
            int dx = rnd.nextInt(25) - 12;
            int dz = rnd.nextInt(25) - 12;
            int top = world.getHeight(net.minecraft.world.gen.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx + dx, cz + dz) - 1;
            BlockPos ground = new BlockPos(cx + dx, top, cz + dz);
            Block b = world.getBlockState(ground).getBlock();
            if ((b == Blocks.PODZOL || b == Blocks.GRASS_BLOCK || b == Blocks.DIRT) && world.isEmptyBlock(ground.above())) {
                world.setBlock(ground, Blocks.PODZOL.defaultBlockState(), 2);
                world.setBlock(ground.above(), (rnd.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState(), 2);
            }
        }
    }
}
