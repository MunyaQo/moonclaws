package com.moonclaws.mod;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

/**
 * Demo cat settlement: a round pit with six cave dens in its walls, a meeting stone in the middle,
 * a dining area and a leaf tunnel entrance. Everything is scaled for cats (1-block entrances).
 *
 * Layout (top view, N is up, entrance in the south):
 *   north wall: queens' den (red), herald's den (blue)
 *   west wall:  apprentices' den (light gray), nursery (pink)
 *   east wall:  healers' den (green), warriors' den (brown)
 */
public final class SettlementBuilder {

    private static final double OUTER_R2 = 14.5D * 14.5D; // solid platform radius
    private static final int OUTER = 14;
    private static final int PIT_R2 = 8 * 8;              // pit radius 8 (about 17 blocks across)

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

    private static BlockState solid(int depth, Random rnd) {
        if (depth == 0) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
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

        // 1. solid platform and cleared sky
        for (int dx = -OUTER; dx <= OUTER; dx++) {
            for (int dz = -OUTER; dz <= OUTER; dz++) {
                if (dx * dx + dz * dz > OUTER_R2) {
                    continue;
                }
                for (int y = g - 8; y <= g; y++) {
                    set(world, cx + dx, y, cz + dz, solid(y - g, rnd));
                }
                for (int y = g + 1; y <= g + 6; y++) {
                    set(world, cx + dx, y, cz + dz, air());
                }
            }
        }

        // 2. the pit
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                if (dx * dx + dz * dz > PIT_R2) {
                    continue;
                }
                for (int y = g - 3; y <= g; y++) {
                    set(world, cx + dx, y, cz + dz, air());
                }
                set(world, cx + dx, g - 4, cz + dz, floor(rnd));
            }
        }

        // 3. thorn ring of leaves around the rim (gap for the entrance in the south)
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 <= PIT_R2 || d2 > 81) {
                    continue;
                }
                if (Math.abs(dx) <= 1 && dz > 0) {
                    continue;
                }
                set(world, cx + dx, g + 1, cz + dz, leaves());
                set(world, cx + dx, g + 2, cz + dz, leaves());
            }
        }

        // 4. meeting stone in the middle
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(world, cx + dx, g - 3, cz + dz, Blocks.ANDESITE.defaultBlockState());
            }
        }
        set(world, cx, g - 2, cz, Blocks.ANDESITE.defaultBlockState());

        // 5. dining area (east of the center): bare earth and a pile of fresh-kill (hay)
        for (int dx = 3; dx <= 5; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(world, cx + dx, g - 4, cz + dz, Blocks.COARSE_DIRT.defaultBlockState());
            }
        }
        set(world, cx + 4, g - 3, cz, Blocks.HAY_BLOCK.defaultBlockState());

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

        // 7. entrance in the south: open trench with steps, thorn walls and a leaf arch
        for (int k = 0; k <= 3; k++) {
            int z = cz + 9 + k;
            int standing = g - 4 + k;
            for (int y = standing + 1; y <= g; y++) {
                set(world, cx, y, z, air());
            }
        }
        for (int z = cz + 9; z <= cz + 13; z++) {
            for (int side = -1; side <= 1; side += 2) {
                set(world, cx + side, g + 1, z, leaves());
                set(world, cx + side, g + 2, z, leaves());
            }
        }
        for (int z = cz + 12; z <= cz + 13; z++) {
            for (int dx = -1; dx <= 1; dx++) {
                set(world, cx + dx, g + 3, z, leaves());
            }
        }
    }

    private static void buildDen(ServerWorld world, int cx, int cz, int g, int dirX, int dirZ, int offset,
                                 Block carpet, boolean healers) {
        int perpX = Math.abs(dirZ);
        int perpZ = Math.abs(dirX);

        // entrance tunnel: exactly one block high
        int tx = cx + dirX * 8 + perpX * offset;
        int tz = cz + dirZ * 8 + perpZ * offset;
        set(world, tx, g - 3, tz, air());

        // chamber 3x3, two blocks high
        for (int t = 9; t <= 11; t++) {
            for (int s = -1; s <= 1; s++) {
                int x = cx + dirX * t + perpX * (offset + s);
                int z = cz + dirZ * t + perpZ * (offset + s);
                set(world, x, g - 3, z, air());
                set(world, x, g - 2, z, air());
            }
        }

        // beds: carpets on both sides of the back part
        for (int t = 10; t <= 11; t++) {
            for (int s = -1; s <= 1; s += 2) {
                int x = cx + dirX * t + perpX * (offset + s);
                int z = cz + dirZ * t + perpZ * (offset + s);
                set(world, x, g - 3, z, carpet.defaultBlockState());
            }
        }

        if (healers) {
            // herbs near the entrance
            set(world, cx + dirX * 9 + perpX * (offset - 1), g - 3, cz + dirZ * 9 + perpZ * (offset - 1),
                    Blocks.FERN.defaultBlockState());
            set(world, cx + dirX * 9 + perpX * offset, g - 3, cz + dirZ * 9 + perpZ * offset,
                    Blocks.POPPY.defaultBlockState());
            set(world, cx + dirX * 9 + perpX * (offset + 1), g - 3, cz + dirZ * 9 + perpZ * (offset + 1),
                    Blocks.DANDELION.defaultBlockState());
            // half-block niche in the back wall (for storing herbs later)
            set(world, cx + dirX * 12 + perpX * offset, g - 3, cz + dirZ * 12 + perpZ * offset,
                    Blocks.COBBLESTONE_SLAB.defaultBlockState());
        }
    }
}
