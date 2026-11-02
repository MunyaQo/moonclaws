package com.moonclaws.mod;

import java.util.Random;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

/**
 * A trodden path around a camp for the outer night guard: a ring 3 blocks wide, about 5 blocks beyond the hedge
 * (13.5-16.5 blocks from the center), at the level of the camp ground. Bumps and trees on it are cut,
 * holes and water are filled with dirt. Paths connect it with the camp entrances.
 */
public final class PatrolTrail {

    public static final double INNER = 13.5D;
    public static final double OUTER = 16.5D;

    private PatrolTrail() {
    }

    public static void build(ServerWorld world, BlockPos center, boolean swamp) {
        int cx = center.getX();
        int cz = center.getZ();
        int g = center.getY();
        Random rnd = new Random(center.asLong() ^ 0x50415452L);
        int r = (int) Math.ceil(OUTER);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                boolean ring = d >= INNER && d <= OUTER;
                // connections to the south entrance (and to the north one in a swamp camp)
                boolean south = Math.abs(dx) <= 1 && dz >= 11 && d < INNER;
                boolean north = swamp && Math.abs(dx) <= 1 && dz <= -11 && d < INNER;
                if (ring || south || north) {
                    column(world, cx + dx, cz + dz, g, rnd);
                }
            }
        }
    }

    private static boolean soft(BlockState s) {
        Material m = s.getMaterial();
        return s.isAir() || m.isLiquid() || m.isReplaceable() || m == Material.PLANT || m == Material.LEAVES
                || m == Material.REPLACEABLE_PLANT || m == Material.WATER_PLANT || m == Material.REPLACEABLE_WATER_PLANT
                || m == Material.SNOW;
    }

    private static void column(ServerWorld world, int x, int z, int g, Random rnd) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        boolean wasWater = false;
        // cut everything above the path (bumps, trees, grass, snow)
        for (int y = g + 1; y <= g + 14; y++) {
            p.set(x, y, z);
            if (!world.getBlockState(p).isAir()) {
                world.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // fill holes and water below
        for (int y = g - 6; y < g; y++) {
            p.set(x, y, z);
            BlockState s = world.getBlockState(p);
            if (soft(s)) {
                if (s.getMaterial().isLiquid()) {
                    wasWater = true;
                }
                world.setBlock(p, Blocks.DIRT.defaultBlockState(), 2);
            }
        }
        p.set(x, g, z);
        BlockState top = world.getBlockState(p);
        if (top.getMaterial().isLiquid()) {
            wasWater = true;
        }
        BlockState path;
        int roll = rnd.nextInt(20);
        if (wasWater) {
            path = roll < 12 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.PODZOL.defaultBlockState();
        } else if (roll < 11) {
            path = Blocks.GRASS_PATH.defaultBlockState();
        } else if (roll < 16) {
            path = Blocks.COARSE_DIRT.defaultBlockState();
        } else {
            path = Blocks.GRASS_BLOCK.defaultBlockState();
        }
        world.setBlock(p, path, 2);
    }
}
