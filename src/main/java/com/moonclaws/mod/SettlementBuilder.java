package com.moonclaws.mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.RotatedPillarBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

/**
 * "Young clan" settlement: a round forest hollow with terraces, a mossy rock pile for meetings,
 * a dining area with an unlit campfire, log benches and hay, and six den caves with wooden frames
 * dug into the slopes. Scaled for cats: den entrances are one block high. Soft light comes from
 * shroomlights ("glowing mushrooms") on the terrace edges; no torches or lanterns.
 *
 * Layout (north is up, the entrance path is in the south):
 *   north side: she-cat warriors' den, leaders' den (leader + deputy)
 *   west side:  apprentices' den, nursery (elders sleep there for now)
 *   east side:  healers' den (herbs, half-block niche), warriors' den
 */
public final class SettlementBuilder {

    private static final int PLATFORM = 13;
    private static final double PLATFORM_R2 = 13.5D * 13.5D;

    private static final int FLOOR_R2 = 25;
    private static final int T1_R2 = 40;
    private static final int T2_R2 = 58;
    private static final int T3_R2 = 81;

    /** dens: {dirX, dirZ, offset, den kind} */
    private static final int[][] DENS = {
            {1, 0, -3, SettlementData.DEN_HEALERS},
            {1, 0, 3, SettlementData.DEN_WARRIORS},
            {-1, 0, -3, SettlementData.DEN_APPRENTICES},
            {-1, 0, 3, SettlementData.DEN_NURSERY},
            {0, -1, -3, SettlementData.DEN_SHE_WARRIORS},
            {0, -1, 3, SettlementData.DEN_LEADERS}
    };

    /** Places for nests inside a den: {t, s}, back row first. */
    private static final int[][] SLOTS = {{10, -1}, {10, 1}, {9, -1}, {9, 1}, {10, 0}, {9, 0}};

    /** What was built: the dens and the free nest places in each of them. */
    public static final class Layout {
        public final BlockPos center;
        public final int floorY;
        public final List<SettlementData.Den> dens = new ArrayList<>();
        public final List<List<BlockPos>> slots = new ArrayList<>();

        Layout(BlockPos center, int floorY) {
            this.center = center;
            this.floorY = floorY;
        }

        public List<BlockPos> slotsOf(int kind) {
            for (int i = 0; i < dens.size(); i++) {
                if (dens.get(i).kind == kind) {
                    return slots.get(i);
                }
            }
            return new ArrayList<>();
        }
    }

    private SettlementBuilder() {
    }

    static void set(ServerWorld world, int x, int y, int z, BlockState state) {
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

    /** Removes logs, leaves, vines, snow layers and bee nests in one column. */
    public static void clearColumn(ServerWorld world, int x, int z, int fromY, int toY) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int y = fromY; y <= toY; y++) {
            p.set(x, y, z);
            BlockState s = world.getBlockState(p);
            if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.getBlock() == Blocks.VINE
                    || s.getBlock() == Blocks.SNOW || s.getBlock() == Blocks.BEE_NEST) {
                world.setBlock(p, air(), 2);
            }
        }
    }

    /** Removes whole trees (logs, leaves, vines) and snow layers around a place before building there. */
    public static void clearTrees(ServerWorld world, int cx, int cz, int g, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    clearColumn(world, cx + dx, cz + dz, g - 3, g + 30);
                }
            }
        }
    }

    /** @param ground the ground block (its y is the surface level). */
    public static Layout build(ServerWorld world, BlockPos ground) {
        int cx = ground.getX();
        int cz = ground.getZ();
        int g = ground.getY();
        Random rnd = new Random(ground.asLong());
        Layout layout = new Layout(ground.immutable(), g - 3);

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
                    topBlock = Blocks.COARSE_DIRT.defaultBlockState();
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

        // 6. soft light: shroomlights ("glowing mushrooms") on the inner edge of the first terrace
        int[][] lights = {{4, 4}, {-4, 4}, {4, -4}, {-4, -4}, {5, 1}, {-1, -5}};
        for (int[] l : lights) {
            set(world, cx + l[0], g - 3, cz + l[1], Blocks.SHROOMLIGHT.defaultBlockState());
        }
        // a few mushrooms on podzol near the walls
        int[][] mush = {{3, 3}, {-3, 4}, {4, -2}, {-2, -4}};
        for (int i = 0; i < mush.length; i++) {
            set(world, cx + mush[i][0], g - 4, cz + mush[i][1], Blocks.PODZOL.defaultBlockState());
            set(world, cx + mush[i][0], g - 3, cz + mush[i][1],
                    (i % 2 == 0 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState());
        }

        // 7. dens
        for (int[] den : DENS) {
            buildDen(world, layout, cx, cz, g, den[0], den[1], den[2], den[3]);
        }
        return layout;
    }

    private static void buildDen(ServerWorld world, Layout layout, int cx, int cz, int g, int dirX, int dirZ,
                                 int offset, int kind) {
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

        if (kind == SettlementData.DEN_HEALERS) {
            set(world, cx + dirX * 8 + perpX * (offset - 1), g - 3, cz + dirZ * 8 + perpZ * (offset - 1),
                    Blocks.FERN.defaultBlockState());
            set(world, cx + dirX * 8 + perpX * offset, g - 3, cz + dirZ * 8 + perpZ * offset,
                    Blocks.POPPY.defaultBlockState());
            set(world, cx + dirX * 8 + perpX * (offset + 1), g - 3, cz + dirZ * 8 + perpZ * (offset + 1),
                    Blocks.DANDELION.defaultBlockState());
            set(world, cx + dirX * 11 + perpX * offset, g - 3, cz + dirZ * 11 + perpZ * offset,
                    Blocks.COBBLESTONE_SLAB.defaultBlockState());
        }

        // the den box (tunnel + chamber) and the nest places
        int x1 = cx + dirX * 6 + perpX * (offset - 1);
        int z1 = cz + dirZ * 6 + perpZ * (offset - 1);
        int x2 = cx + dirX * 10 + perpX * (offset + 1);
        int z2 = cz + dirZ * 10 + perpZ * (offset + 1);
        BlockPos min = new BlockPos(Math.min(x1, x2), g - 3, Math.min(z1, z2));
        BlockPos max = new BlockPos(Math.max(x1, x2), g - 2, Math.max(z1, z2));
        layout.dens.add(new SettlementData.Den(kind, min, max));
        List<BlockPos> slots = new ArrayList<>();
        for (int[] slot : SLOTS) {
            slots.add(new BlockPos(cx + dirX * slot[0] + perpX * (offset + slot[1]), g - 3,
                    cz + dirZ * slot[0] + perpZ * (offset + slot[1])));
        }
        if (kind == SettlementData.DEN_HEALERS) {
            // the healers keep a Healer's Stone in their den
            BlockPos stone = slots.remove(slots.size() - 1);
            world.setBlock(stone, MoonclawsMod.HEALER_STONE.get().defaultBlockState(), 2);
        }
        layout.slots.add(slots);
    }
}
