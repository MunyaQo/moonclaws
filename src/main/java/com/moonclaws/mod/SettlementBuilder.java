package com.moonclaws.mod;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.RotatedPillarBlock;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

/**
 * Demo cat settlement (version 0.2.0): a round forest hollow with terraces, a mossy rock pile for
 * meetings, a dining area with an unlit campfire, log benches and hay, and six den caves with wooden
 * frames dug into the slopes. Scaled for cats: den entrances are one block high.
 *
 * Layout (north is up, the entrance path is in the south):
 *   north side: queens' den (red), herald's den (blue)
 *   west side:  apprentices' den (light gray), nursery (pink)
 *   east side:  healers' den (green, herbs, half-block niche), warriors' den (brown)
 */
public final class SettlementBuilder {

    private static final int PLATFORM = 13;
    private static final double PLATFORM_R2 = 13.5D * 13.5D;

    // terrace borders (squared radius): floor, 1st, 2nd, 3rd terrace, then the normal surface
    private static final int FLOOR_R2 = 25;
    private static final int T1_R2 = 40;
    private static final int T2_R2 = 58;
    private static final int T3_R2 = 81;

    private SettlementBuilder() {
    }

    private static void set(ServerWorld world, int x, int y, int z, BlockState state) {
        if (y < 1 || y > 254) {
            return;
        }
        world.setBlock(new BlockPos(x, y, z), state, 2);
    }

    private static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }

    private static BlockState leaves() {
        return Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, Boolean.TRUE);
    }

    private static BlockState log(Direction.Axis axis) {
        return Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    /** 0 = pit floor, 1-3 = terraces, 4 = normal surface. */
    private static int level(int r2) {
        if (r2 <= FLOOR_R2) {
            return 0;
        }
        if (r2 <= T1_R2) {
            return 1;
        }
        if (r2 <= T2_R2) {
            return 2;
        }
        if (r2 <= T3_R2) {
            return 3;
        }
        return 4;
    }

    private static BlockState solid(int depth, Random rnd) {
        if (depth >= -3) {
            int n = rnd.nextInt(12);
            if (n == 0) {
                return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            }
            return (n < 4 ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState();
        }
        return (rnd.nextInt(6) == 0 ? Blocks.ANDESITE : Blocks.STONE).defaultBlockState();
    }

    private static BlockState floor(Random rnd) {
        int n = rnd.nextInt(10);
        Block b = n < 4 ? Blocks.COARSE_DIRT : (n < 8 ? Blocks.DIRT : Blocks.PODZOL);
        return b.defaultBlockState();
    }

    /** @param ground the ground block under the player (its y is the surface level). */
    public static void build(ServerWorld world, BlockPos ground) {
        int cx = ground.getX();
        int cz = ground.getZ();
        int g = ground.getY();
        Random rnd = new Random(ground.asLong());

        // 1. platform with terraces: solid below, grass on top, cleared sky above
        for (int dx = -PLATFORM; dx <= PLATFORM; dx++) {
            for (int dz = -PLATFORM; dz <= PLATFORM; dz++) {
                int r2 = dx * dx + dz * dz;
                if (r2 > PLATFORM_R2) {
                    continue;
                }
                int lvl = level(r2);
                int top = g - 4 + lvl;
                for (int y = g - 8; y < top; y++) {
                    set(world, cx + dx, y, cz + dz, solid(y - g, rnd));
                }
                BlockState topBlock;
                if (lvl == 0) {
                    topBlock = floor(rnd);
                } else if (dx == 0 && dz >= 5) {
                    topBlock = Blocks.COARSE_DIRT.defaultBlockState(); // entrance path in the south
                } else {
                    topBlock = Blocks.GRASS_BLOCK.defaultBlockState();
                }
                set(world, cx + dx, top, cz + dz, topBlock);
                for (int y = top + 1; y <= g + 6; y++) {
                    set(world, cx + dx, y, cz + dz, air());
                }
            }
        }

        // 2. bushes and ferns on the terraces (not on the entrance path)
        for (int dx = -PLATFORM; dx <= PLATFORM; dx++) {
            for (int dz = -PLATFORM; dz <= PLATFORM; dz++) {
                int r2 = dx * dx + dz * dz;
                if (r2 <= FLOOR_R2 || r2 > T3_R2 + 20) {
                    continue;
                }
                if (Math.abs(dx) <= 1 && dz > 0) {
                    continue;
                }
                int top = g - 4 + level(r2);
                int n = rnd.nextInt(10);
                if (n == 0) {
                    set(world, cx + dx, top + 1, cz + dz, leaves());
                    if (rnd.nextInt(3) == 0) {
                        set(world, cx + dx, top + 2, cz + dz, leaves());
                    }
                } else if (n == 1) {
                    set(world, cx + dx, top + 1, cz + dz, Blocks.FERN.defaultBlockState());
                } else if (n == 2) {
                    set(world, cx + dx, top + 1, cz + dz, Blocks.GRASS.defaultBlockState());
                }
            }
        }

        // 3. thorn ring of leaves on the surface around the hollow (gap for the entrance path)
        for (int dx = -11; dx <= 11; dx++) {
            for (int dz = -11; dz <= 11; dz++) {
                int r2 = dx * dx + dz * dz;
                if (r2 <= T3_R2 || r2 > 110) {
                    continue;
                }
                if (Math.abs(dx) <= 1 && dz > 0) {
                    continue;
                }
                set(world, cx + dx, g + 1, cz + dz, leaves());
                set(world, cx + dx, g + 2, cz + dz, leaves());
            }
        }

        // 4. meeting place: a mossy rock pile west of the center
        for (int dx = -4; dx <= -2; dx++) {
            for (int dz = -2; dz <= 1; dz++) {
                double dist = Math.max(Math.abs(dx + 3.0D), Math.abs(dz + 0.5D));
                int h = (int) Math.floor(3.9D - dist * 1.5D);
                for (int i = 0; i < h; i++) {
                    int n = rnd.nextInt(10);
                    Block b = n < 6 ? Blocks.MOSSY_COBBLESTONE : (n < 9 ? Blocks.COBBLESTONE : Blocks.ANDESITE);
                    set(world, cx + dx, g - 3 + i, cz + dz, b.defaultBlockState());
                }
            }
        }

        // 5. dining area east of the center: unlit campfire, log benches, hay for prey
        set(world, cx + 2, g - 4, cz, Blocks.COBBLESTONE.defaultBlockState());
        set(world, cx + 2, g - 3, cz, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, Boolean.FALSE));
        for (int dx = 1; dx <= 3; dx++) {
            set(world, cx + dx, g - 3, cz - 2, log(Direction.Axis.X));
            set(world, cx + dx, g - 3, cz + 2, log(Direction.Axis.X));
        }
        set(world, cx + 4, g - 3, cz, Blocks.HAY_BLOCK.defaultBlockState());
        set(world, cx + 4, g - 3, cz + 1, Blocks.HAY_BLOCK.defaultBlockState());
        set(world, cx + 4, g - 2, cz, Blocks.HAY_BLOCK.defaultBlockState());

        // 6. dens: {dirX, dirZ, offset}
        int[][] dens = {
                {1, 0, -3},   // healers
                {1, 0, 3},    // warriors
                {-1, 0, -3},  // apprentices
                {-1, 0, 3},   // nursery
                {0, -1, -3},  // queens
                {0, -1, 3}    // herald
        };
        Block[] carpets = {
                Blocks.GREEN_CARPET, Blocks.BROWN_CARPET, Blocks.LIGHT_GRAY_CARPET,
                Blocks.PINK_CARPET, Blocks.RED_CARPET, Blocks.BLUE_CARPET
        };
        for (int i = 0; i < dens.length; i++) {
            buildDen(world, cx, cz, g, dens[i][0], dens[i][1], dens[i][2], carpets[i], i == 0);
        }
    }

    private static void buildDen(ServerWorld world, int cx, int cz, int g, int dirX, int dirZ, int offset,
                                 Block carpet, boolean healers) {
        int perpX = Math.abs(dirZ);
        int perpZ = Math.abs(dirX);

        // entrance tunnel: three cells, exactly one block high (cat size)
        for (int t = 5; t <= 7; t++) {
            set(world, cx + dirX * t + perpX * offset, g - 3, cz + dirZ * t + perpZ * offset, air());
        }

        // wooden frame around the entrance: two posts and a beam above
        Direction.Axis beamAxis = perpX != 0 ? Direction.Axis.X : Direction.Axis.Z;
        for (int s = -1; s <= 1; s += 2) {
            set(world, cx + dirX * 5 + perpX * (offset + s), g - 3, cz + dirZ * 5 + perpZ * (offset + s),
                    log(Direction.Axis.Y));
        }
        for (int s = -1; s <= 1; s++) {
            set(world, cx + dirX * 5 + perpX * (offset + s), g - 2, cz + dirZ * 5 + perpZ * (offset + s),
                    log(beamAxis));
        }

        // chamber 3x3, two blocks high
        for (int t = 8; t <= 10; t++) {
            for (int s = -1; s <= 1; s++) {
                int x = cx + dirX * t + perpX * (offset + s);
                int z = cz + dirZ * t + perpZ * (offset + s);
                set(world, x, g - 3, z, air());
                set(world, x, g - 2, z, air());
            }
        }

        // beds: carpets on both sides of the back part
        for (int t = 9; t <= 10; t++) {
            for (int s = -1; s <= 1; s += 2) {
                set(world, cx + dirX * t + perpX * (offset + s), g - 3, cz + dirZ * t + perpZ * (offset + s),
                        carpet.defaultBlockState());
            }
        }

        if (healers) {
            // herbs near the entrance
            set(world, cx + dirX * 8 + perpX * (offset - 1), g - 3, cz + dirZ * 8 + perpZ * (offset - 1),
                    Blocks.FERN.defaultBlockState());
            set(world, cx + dirX * 8 + perpX * offset, g - 3, cz + dirZ * 8 + perpZ * offset,
                    Blocks.POPPY.defaultBlockState());
            set(world, cx + dirX * 8 + perpX * (offset + 1), g - 3, cz + dirZ * 8 + perpZ * (offset + 1),
                    Blocks.DANDELION.defaultBlockState());
            // half-block niche in the back wall (for storing herbs later)
            set(world, cx + dirX * 11 + perpX * offset, g - 3, cz + dirZ * 11 + perpZ * offset,
                    Blocks.COBBLESTONE_SLAB.defaultBlockState());
        }
    }
}
