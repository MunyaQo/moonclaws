package com.moonclaws.mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.RotatedPillarBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.state.properties.Half;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerWorld;

/**
 * Council "Four Trees": a wide glade in the forest with a shallow hollow in the middle, four separate
 * "ancient" trees (one trunk each, much taller and fuller than the forest around, wood by biome) on the diagonals,
 * a low rock in the center, paths from four entrances, flowers and mushrooms, soft light from one shroomlight
 * deep in each crown and one in the roots, and a stone platform for every settlement of the group,
 * turned towards that settlement.
 */
public final class CouncilBuilder {

    public static final int WOOD_OAK = 0;
    public static final int WOOD_BIRCH = 1;
    public static final int WOOD_SPRUCE = 2;
    public static final int WOOD_DARK_OAK = 3;

    private static final int FLOOR_R = 9;
    private static final int TERRACE_R = 11;
    private static final int LEVEL_R = 13;   // ground is shaped up to here
    private static final int GLADE_R = 19;   // trees are cleared up to ~17-21 (uneven edge)
    private static final int TREE_OFFSET = 7;
    public static final double PLATFORM_R = 5.5D;

    private CouncilBuilder() {
    }

    private static void set(ServerWorld w, int x, int y, int z, BlockState s) {
        SettlementBuilder.set(w, x, y, z, s);
    }

    private static Block logOf(int wood) {
        if (wood == WOOD_DARK_OAK) {
            return Blocks.DARK_OAK_LOG;
        }
        return wood == WOOD_BIRCH ? Blocks.BIRCH_LOG : (wood == WOOD_SPRUCE ? Blocks.SPRUCE_LOG : Blocks.OAK_LOG);
    }

    private static Block stairsOf(int wood) {
        if (wood == WOOD_DARK_OAK) {
            return Blocks.DARK_OAK_STAIRS;
        }
        return wood == WOOD_BIRCH ? Blocks.BIRCH_STAIRS : (wood == WOOD_SPRUCE ? Blocks.SPRUCE_STAIRS : Blocks.OAK_STAIRS);
    }

    private static BlockState leavesOf(int wood) {
        if (wood == WOOD_DARK_OAK) {
            return Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, Boolean.TRUE);
        }
        Block b = wood == WOOD_BIRCH ? Blocks.BIRCH_LEAVES : (wood == WOOD_SPRUCE ? Blocks.SPRUCE_LEAVES : Blocks.OAK_LEAVES);
        return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, Boolean.TRUE);
    }

    private static BlockState log(int wood, Direction.Axis axis) {
        return logOf(wood).defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    private static BlockState rock(Random rnd) {
        int n = rnd.nextInt(10);
        if (n < 4) {
            return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        }
        if (n < 6) {
            return Blocks.STONE_BRICKS.defaultBlockState();
        }
        if (n < 8) {
            return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        return (n == 8 ? Blocks.ANDESITE : Blocks.COBBLESTONE).defaultBlockState();
    }

    /** Height of the top block of the ground for a distance from the center. */
    private static int topAt(double r, int floorY, int g) {
        if (r <= FLOOR_R + 0.5D) {
            return floorY;
        }
        if (r <= TERRACE_R + 0.5D) {
            return floorY + 1;
        }
        return g;
    }

    /** @param ground surface block at the center; the clearing floor is two blocks lower. */
    public static void build(ServerWorld world, BlockPos ground, int wood) {
        int cx = ground.getX();
        int cz = ground.getZ();
        int g = ground.getY();
        int floorY = g - 2;
        Random rnd = new Random(ground.asLong() ^ 0x4C41574CL);

        // 0. a glade: cut the forest with an uneven edge, leave a few stumps
        double[] edge = new double[16];
        for (int i = 0; i < edge.length; i++) {
            edge[i] = GLADE_R - 2 + rnd.nextDouble() * 4.0D;
        }
        for (int dx = -GLADE_R - 3; dx <= GLADE_R + 3; dx++) {
            for (int dz = -GLADE_R - 3; dz <= GLADE_R + 3; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                double a = (Math.atan2(dz, dx) + Math.PI) / (2 * Math.PI) * edge.length;
                int i0 = (int) Math.floor(a) % edge.length;
                int i1 = (i0 + 1) % edge.length;
                double t = a - Math.floor(a);
                double limit = edge[i0] * (1 - t) + edge[i1] * t;
                if (r > limit) {
                    continue;
                }
                SettlementBuilder.clearColumn(world, cx + dx, cz + dz, g - 3, g + 34);
            }
        }

        // 1. ground: floor, one terrace, then the normal surface; clear the air above
        for (int dx = -LEVEL_R; dx <= LEVEL_R; dx++) {
            for (int dz = -LEVEL_R; dz <= LEVEL_R; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r > LEVEL_R + 0.5D) {
                    continue;
                }
                int top = topAt(r, floorY, g);
                for (int y = g - 6; y < top; y++) {
                    set(world, cx + dx, y, cz + dz, (y < top - 2 ? Blocks.STONE : Blocks.DIRT).defaultBlockState());
                }
                set(world, cx + dx, top, cz + dz, Blocks.GRASS_BLOCK.defaultBlockState());
                for (int y = top + 1; y <= g + 8; y++) {
                    set(world, cx + dx, y, cz + dz, Blocks.AIR.defaultBlockState());
                }
            }
        }

        // 2. stumps and lone bushes on the glade outside the hollow
        for (int i = 0; i < 10; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = LEVEL_R + 1.5D + rnd.nextDouble() * (GLADE_R - LEVEL_R - 3);
            int x = cx + (int) Math.round(Math.cos(a) * r);
            int z = cz + (int) Math.round(Math.sin(a) * r);
            int y = world.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (i % 2 == 0) {
                world.setBlock(new BlockPos(x, y, z), log(wood, Direction.Axis.Y), 2);
                if (rnd.nextBoolean()) {
                    world.setBlock(new BlockPos(x, y + 1, z), log(wood, Direction.Axis.Y), 2);
                }
            } else {
                world.setBlock(new BlockPos(x, y, z), leavesOf(wood), 2);
            }
        }

        // 3. paths: a cross from the four entrances and a ring around the platforms
        for (int dx = -LEVEL_R; dx <= LEVEL_R; dx++) {
            for (int dz = -LEVEL_R; dz <= LEVEL_R; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                boolean cross = (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) && r <= LEVEL_R;
                boolean ring = r >= 3.0D && r <= 3.9D;
                if ((!cross && !ring) || rnd.nextInt(5) == 0) {
                    continue;
                }
                int n = rnd.nextInt(3);
                set(world, cx + dx, topAt(r, floorY, g), cz + dz,
                        (n == 0 ? Blocks.GRAVEL : Blocks.COARSE_DIRT).defaultBlockState());
            }
        }

        // swamp: the rock stands on an island, water around it (the paths of the cross stay dry)
        if (wood == WOOD_DARK_OAK) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    if (r >= 2.6D && r < 4.0D && Math.abs(dx) > 1 && Math.abs(dz) > 1) {
                        set(world, cx + dx, floorY, cz + dz, Blocks.WATER.defaultBlockState());
                        if (rnd.nextInt(3) == 0) {
                            set(world, cx + dx, floorY + 1, cz + dz, Blocks.LILY_PAD.defaultBlockState());
                        }
                    }
                }
            }
        }

        // 4. the rock: low and wide, about 4 blocks above the floor
        int[][][] layers = {ring(2), square(1), {{0, 0}, {1, 0}, {0, -1}, {-1, 0}, {0, 1}}, {{0, 0}, {1, 0}}};
        for (int i = 0; i < layers.length; i++) {
            for (int[] c : layers[i]) {
                set(world, cx + c[0], floorY + 1 + i, cz + c[1], rock(rnd));
            }
        }
        set(world, cx + 3, floorY + 1, cz - 1, Blocks.SHROOMLIGHT.defaultBlockState());
        set(world, cx - 3, floorY + 1, cz + 1, Blocks.SHROOMLIGHT.defaultBlockState());

        // 5. four separate ancient trees on the diagonals
        int[][] spots = {{TREE_OFFSET, TREE_OFFSET}, {-TREE_OFFSET, TREE_OFFSET}, {TREE_OFFSET, -TREE_OFFSET}, {-TREE_OFFSET, -TREE_OFFSET}};
        for (int[] sp : spots) {
            int tx = cx + sp[0];
            int tz = cz + sp[1];
            double r = Math.sqrt(sp[0] * sp[0] + sp[1] * sp[1]);
            int base = topAt(r, floorY, g) + 1;
            set(world, tx, base - 1, tz, Blocks.DIRT.defaultBlockState());
            BlockPos trunk = new BlockPos(tx, base, tz);
            if (wood == WOOD_SPRUCE) {
                ancientSpruce(world, trunk, rnd);
            } else {
                ancientBroadleaf(world, trunk, wood, rnd);
                vines(world, trunk, rnd);
            }
            roots(world, trunk, wood, rnd);
        }

        // 6. flowers, grass and mushrooms on the floor and terrace (not on paths and platforms)
        for (int i = 0; i < 80; i++) {
            int dx = rnd.nextInt(2 * TERRACE_R + 1) - TERRACE_R;
            int dz = rnd.nextInt(2 * TERRACE_R + 1) - TERRACE_R;
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r < 7.2D || r > TERRACE_R || Math.abs(dx) <= 1 || Math.abs(dz) <= 1) {
                continue;
            }
            int top = topAt(r, floorY, g);
            BlockPos above = new BlockPos(cx + dx, top + 1, cz + dz);
            if (!world.isEmptyBlock(above)) {
                continue;
            }
            int n = rnd.nextInt(10);
            if (n < 4) {
                world.setBlock(above, Blocks.GRASS.defaultBlockState(), 2);
            } else if (n < 6) {
                world.setBlock(above, Blocks.POPPY.defaultBlockState(), 2);
            } else if (n < 8) {
                world.setBlock(above, Blocks.DANDELION.defaultBlockState(), 2);
            } else {
                world.setBlock(above.below(), Blocks.PODZOL.defaultBlockState(), 2);
                world.setBlock(above, (n == 8 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState(), 2);
            }
        }
    }

    /**
     * A stone platform 3x3 for one settlement, on the ring around the rock, in the direction of that settlement.
     * Can be added at any time (also later, when a new settlement joins the Council).
     */
    public static void addPlatform(ServerWorld world, BlockPos ground, int towardsX, int towardsZ) {
        int cx = ground.getX();
        int cz = ground.getZ();
        int floorY = ground.getY() - 2;
        double a = Math.atan2(towardsZ - cz, towardsX - cx);
        int px = cx + (int) Math.round(Math.cos(a) * PLATFORM_R);
        int pz = cz + (int) Math.round(Math.sin(a) * PLATFORM_R);
        Random rnd = new Random(BlockPos.asLong(px, floorY, pz));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = new BlockPos(px + dx, floorY + 1, pz + dz);
                world.setBlock(p, (rnd.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.MOSSY_STONE_BRICKS).defaultBlockState(), 2);
                world.setBlock(p.above(), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // moss and a flower at the edges
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos edge = new BlockPos(px, floorY + 1, pz).relative(d, 2);
            if (world.isEmptyBlock(edge) && rnd.nextBoolean()) {
                world.setBlock(edge, (rnd.nextBoolean() ? Blocks.FERN : Blocks.POPPY).defaultBlockState(), 2);
            }
        }
    }

    private static int[][] square(int r) {
        List<int[]> out = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                out.add(new int[] {dx, dz});
            }
        }
        return out.toArray(new int[0][]);
    }

    /** A filled square without its corners. */
    private static int[][] ring(int r) {
        List<int[]> out = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) == r && Math.abs(dz) == r) {
                    continue;
                }
                out.add(new int[] {dx, dz});
            }
        }
        return out.toArray(new int[0][]);
    }

    private static void leafBlob(ServerWorld world, BlockPos c, double rx, double ry, BlockState leaves) {
        int ix = (int) Math.ceil(rx);
        int iy = (int) Math.ceil(ry);
        for (int dx = -ix; dx <= ix; dx++) {
            for (int dy = -iy; dy <= iy; dy++) {
                for (int dz = -ix; dz <= ix; dz++) {
                    double v = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rx * rx);
                    if (v > 1.0D) {
                        continue;
                    }
                    BlockPos p = c.offset(dx, dy, dz);
                    if (world.isEmptyBlock(p)) {
                        world.setBlock(p, leaves, 2);
                    }
                }
            }
        }
    }

    /** Oak or birch: tall trunk, thick branches to four sides, a big crown about 9 blocks wide. */
    private static void ancientBroadleaf(ServerWorld world, BlockPos trunk, int wood, Random rnd) {
        int h = 14 + rnd.nextInt(3);
        for (int y = 0; y < h; y++) {
            world.setBlock(trunk.above(y), log(wood, Direction.Axis.Y), 2);
        }
        BlockState leaves = leavesOf(wood);
        // branches
        Direction[] dirs = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int i = 0; i < dirs.length; i++) {
            Direction d = dirs[i];
            int startY = h - 7 + (i % 2) * 2;
            BlockPos p = trunk.above(startY);
            for (int k = 1; k <= 3; k++) {
                p = p.relative(d);
                if (k == 3) {
                    p = p.above();
                }
                world.setBlock(p, log(wood, d.getAxis()), 2);
            }
            leafBlob(world, p.above(), 2.3D, 1.6D, leaves);
        }
        // main crown
        BlockPos top = trunk.above(h - 2);
        leafBlob(world, top, 4.5D, 3.2D, leaves);
        leafBlob(world, trunk.above(h + 1), 2.5D, 1.5D, leaves);
        // one soft light deep in the crown, right next to the trunk
        world.setBlock(top.relative(dirs[rnd.nextInt(4)]), Blocks.SHROOMLIGHT.defaultBlockState(), 2);
    }

    /** Spruce: a tall "candle" with tiers of branches getting smaller to the top. */
    private static void ancientSpruce(ServerWorld world, BlockPos trunk, Random rnd) {
        int h = 15 + rnd.nextInt(3);
        for (int y = 0; y < h; y++) {
            world.setBlock(trunk.above(y), log(WOOD_SPRUCE, Direction.Axis.Y), 2);
        }
        BlockState leaves = leavesOf(WOOD_SPRUCE);
        for (int y = 4; y <= h + 1; y++) {
            double left = (h + 1 - y) / (double) (h - 3);
            double r = (y % 2 == 0) ? 1.0D + left * 3.6D : 0.6D + left * 2.2D;
            int ir = (int) Math.ceil(r);
            for (int dx = -ir; dx <= ir; dx++) {
                for (int dz = -ir; dz <= ir; dz++) {
                    if (dx * dx + dz * dz > r * r + 0.3D) {
                        continue;
                    }
                    BlockPos p = trunk.offset(dx, y, dz);
                    if (world.isEmptyBlock(p)) {
                        world.setBlock(p, leaves, 2);
                    }
                }
            }
        }
        world.setBlock(trunk.above(h + 2), leaves, 2);
        world.setBlock(trunk.above(h - 6).relative(Direction.from2DDataValue(rnd.nextInt(4))),
                Blocks.SHROOMLIGHT.defaultBlockState(), 2);
    }

    /** Long roots: logs lying on the ground around the trunk, then stairs going outwards into the ground. */
    private static void roots(ServerWorld world, BlockPos trunk, int wood, Random rnd) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int len = 3 + rnd.nextInt(2);
            for (int k = 1; k <= len; k++) {
                BlockPos p = trunk.relative(d, k);
                if (k == len) {
                    // the end goes into the ground: a stair one step lower
                    p = p.below();
                    world.setBlock(p, stairsOf(wood).defaultBlockState()
                            .setValue(StairsBlock.FACING, d.getOpposite()).setValue(StairsBlock.HALF, Half.BOTTOM), 2);
                } else if (k == len - 1) {
                    world.setBlock(p, stairsOf(wood).defaultBlockState()
                            .setValue(StairsBlock.FACING, d.getOpposite()).setValue(StairsBlock.HALF, Half.BOTTOM), 2);
                } else {
                    world.setBlock(p, log(wood, d.getAxis()), 2);
                }
            }
        }
        // the soft light hidden in the roots
        world.setBlock(trunk.offset(1, 0, 1), Blocks.SHROOMLIGHT.defaultBlockState(), 2);
    }

    private static void vines(ServerWorld world, BlockPos trunk, Random rnd) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (rnd.nextInt(2) == 0) {
                continue;
            }
            int len = 3 + rnd.nextInt(5);
            for (int y = 2; y < 2 + len; y++) {
                BlockPos at = trunk.above(y).relative(d);
                if (world.isEmptyBlock(at)) {
                    world.setBlock(at, Blocks.VINE.defaultBlockState()
                            .setValue(VineBlock.getPropertyForFace(d.getOpposite()), Boolean.TRUE), 2);
                }
            }
        }
    }
}
