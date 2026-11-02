package com.moonclaws.mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Groups "Council + 3-5 young settlements around it" appear in the world by themselves.
 *
 * The world is split into regions of 1024 x 1024 blocks; each region gets at most one group.
 * The place is chosen from the world seed, and the terrain is checked BEFORE anything is built
 * (green biomes only, nearly flat, no water). A piece is built when a player comes close and
 * all chunks under it are loaded.
 */
public final class ClanWorldgen {

    private static final Logger LOG = LogManager.getLogger("Moonclaws");

    public static final int REGION = 1024;
    public static final int RING = 96;
    /** Raise when the place-finding rules change (empty regions are checked again). */
    private static final int PLAN_VERSION = 4;          // settlements are ~6 chunks from the Council
    /** Build as soon as the chunks are there, far away, so the player does not see it appear. */
    private static final int BUILD_DISTANCE = 240;
    /** The builders dig the camp into the ground, so some unevenness is fine. */
    private static final int MAX_SLOPE = 7;

    private ClanWorldgen() {
    }

    public static long key(int rx, int rz) {
        return ((long) rx << 32) ^ (rz & 0xFFFFFFFFL);
    }

    // ------------------------------------------------------------------ terrain checks

    /** Wood kind for a green biome, or -1 if no group may stand here. */
    public static int woodFor(ServerWorld world, Biome biome) {
        if (biome.getBaseTemperature() < 0.15F) {
            return -1; // snowy: own templates later
        }
        Biome.Category c = biome.getBiomeCategory();
        ResourceLocation id = world.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY).getKey(biome);
        String name = id == null ? "" : id.getPath();
        if (c == Biome.Category.SWAMP || name.contains("dark_forest")) {
            return CouncilBuilder.WOOD_DARK_OAK;
        }
        if (c == Biome.Category.FOREST) {
            return name.contains("birch") ? CouncilBuilder.WOOD_BIRCH : CouncilBuilder.WOOD_OAK;
        }
        if (c == Biome.Category.TAIGA) {
            return CouncilBuilder.WOOD_SPRUCE;
        }
        if (c == Biome.Category.PLAINS) {
            return CouncilBuilder.WOOD_OAK;
        }
        return -1;
    }

    private static Biome biomeAt(ChunkGenerator gen, int x, int z) {
        return gen.getBiomeSource().getNoiseBiome(x >> 2, 64 >> 2, z >> 2);
    }

    /**
     * Ground level (surface block y) of a site if it is fine for building: green biome, nearly flat, dry.
     * Uses the world generator only, so nothing has to be loaded. Returns Integer.MIN_VALUE if not fine.
     */
    static int checkSite(ServerWorld world, int x, int z, int radius) {
        ChunkGenerator gen = world.getChunkSource().getGenerator();
        int sea = gen.getSeaLevel();
        int[] offsets = {-radius, -radius / 2, 0, radius / 2, radius};
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        List<Integer> heights = new ArrayList<>();
        for (int ox : offsets) {
            for (int oz : offsets) {
                int h = gen.getBaseHeight(x + ox, z + oz, Heightmap.Type.WORLD_SURFACE_WG);
                min = Math.min(min, h);
                max = Math.max(max, h);
                heights.add(h);
            }
        }
        // the center must be green, and at least 3 of the 4 corners
        int centerWood = woodFor(world, biomeAt(gen, x, z));
        if (centerWood < 0) {
            return Integer.MIN_VALUE;
        }
        // surface block = height - 1; under water if it is below the sea level (swamps may be right at it)
        int lowest = centerWood == CouncilBuilder.WOOD_DARK_OAK ? sea - 1 : sea;
        if (max - min > MAX_SLOPE || min - 1 < lowest) {
            return Integer.MIN_VALUE;
        }
        int[][] corners = {{radius, radius}, {-radius, radius}, {radius, -radius}, {-radius, -radius}};
        int green = 0;
        for (int[] c : corners) {
            if (woodFor(world, biomeAt(gen, x + c[0], z + c[1])) >= 0) {
                green++;
            }
        }
        if (green < 3) {
            return Integer.MIN_VALUE;
        }
        Collections.sort(heights);
        return heights.get(heights.size() / 2) - 1;
    }

    // ------------------------------------------------------------------ planning

    /** Make sure the region has been looked at (planned or marked empty). Returns true if work was done. */
    public static boolean plan(ServerWorld world, int rx, int rz) {
        SettlementData data = SettlementData.get(world);
        data.upgradePlans(PLAN_VERSION);
        long k = key(rx, rz);
        if (data.isRegionKnown(k)) {
            return false;
        }
        Random r = new Random(world.getSeed() ^ (rx * 341873128712L + rz * 132897987541L) ^ 0x6D6F6F6EL);
        for (int attempt = 0; attempt < 10; attempt++) {
            int cx = rx * REGION + 160 + r.nextInt(REGION - 320);
            int cz = rz * REGION + 160 + r.nextInt(REGION - 320);
            int cy = checkSite(world, cx, cz, 13);
            if (cy == Integer.MIN_VALUE) {
                continue;
            }
            int count = 3 + r.nextInt(3);
            double a0 = r.nextDouble() * Math.PI * 2.0D;
            SettlementData.Group g = new SettlementData.Group();
            g.council = new BlockPos(cx, cy, cz);
            // each settlement may move a little (closer/farther, a bit to the side) to find good ground
            double[] radii = {RING, RING - 16, RING + 16};
            double[] turns = {0.0D, 0.18D, -0.18D};
            for (int i = 0; i < count; i++) {
                double a = a0 + i * Math.PI * 2.0D / count;
                found:
                for (double rr : radii) {
                    for (double turn : turns) {
                        int sx = cx + (int) Math.round(Math.cos(a + turn) * rr);
                        int sz = cz + (int) Math.round(Math.sin(a + turn) * rr);
                        int sy = checkSite(world, sx, sz, 14);
                        if (sy != Integer.MIN_VALUE) {
                            g.sites.add(new BlockPos(sx, sy, sz));
                            break found;
                        }
                    }
                }
            }
            if (g.sites.size() < 3) {
                continue;
            }
            List<Integer> names = new ArrayList<>();
            for (int i = 0; i < SettlementSpawner.CLAN_NAMES; i++) {
                names.add(i);
            }
            Collections.shuffle(names, r);
            for (int i = 0; i < g.sites.size(); i++) {
                g.clans.add(names.get(i));
                g.built.add(false);
            }
            data.putGroup(k, g);
            LOG.info("[Moonclaws] planned a clan group at {} {} with {} settlements", cx, cz, g.sites.size());
            return true;
        }
        data.markRegionEmpty(k);
        LOG.info("[Moonclaws] region {} {}: no good place for a clan group", rx, rz);
        return true;
    }

    // ------------------------------------------------------------------ building

    private static boolean chunksLoaded(ServerWorld world, int x, int z, int radius) {
        ServerChunkProvider chunks = world.getChunkSource();
        for (int cx = (x - radius) >> 4; cx <= (x + radius) >> 4; cx++) {
            for (int cz = (z - radius) >> 4; cz <= (z + radius) >> 4; cz++) {
                if (!chunks.hasChunk(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Ground right now (median of real heights, so tree trunks don't matter), or MIN_VALUE if there is a real lake.
     * Small ponds are fine: they are filled with earth when building.
     */
    private static int realGround(ServerWorld world, int x, int z, int radius) {
        return realGround(world, x, z, radius, 5);
    }

    private static int realGround(ServerWorld world, int x, int z, int radius, int maxWater) {
        List<Integer> heights = new ArrayList<>();
        int[] offsets = {-radius, -radius / 2, 0, radius / 2, radius};
        int water = 0;
        for (int ox : offsets) {
            for (int oz : offsets) {
                int h = world.getHeight(Heightmap.Type.WORLD_SURFACE, x + ox, z + oz);
                BlockPos top = new BlockPos(x + ox, h - 1, z + oz);
                if (!world.getFluidState(top).isEmpty()) {
                    water++;
                    continue;
                }
                heights.add(world.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x + ox, z + oz));
            }
        }
        if (water > maxWater || heights.isEmpty()) {
            return Integer.MIN_VALUE;
        }
        Collections.sort(heights);
        return heights.get(heights.size() / 2) - 1;
    }

    /** Small ponds and streams around a building place become earth (so no water runs into the camp). */
    private static void fillPonds(ServerWorld world, int x, int z, int g, int radius) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                for (int y = g + 3; y >= g - 8; y--) {
                    p.set(x + dx, y, z + dz);
                    if (world.getFluidState(p).isEmpty()) {
                        continue;
                    }
                    net.minecraft.block.BlockState fill;
                    if (y > g) {
                        fill = net.minecraft.block.Blocks.AIR.defaultBlockState();
                    } else if (y == g) {
                        fill = net.minecraft.block.Blocks.GRASS_BLOCK.defaultBlockState();
                    } else {
                        fill = net.minecraft.block.Blocks.DIRT.defaultBlockState();
                    }
                    world.setBlock(p, fill, 2);
                }
            }
        }
    }

    /** Something built by players or villagers here (planks, beds, doors, chests, torches, glass...)? Then don't build. */
    private static boolean somethingBuilt(ServerWorld world, int x, int z, int g, int radius) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = g - 3; y <= g + 8; y++) {
                    p.set(x + dx, y, z + dz);
                    net.minecraft.block.BlockState st = world.getBlockState(p);
                    if (st.is(net.minecraft.tags.BlockTags.PLANKS) || st.is(net.minecraft.tags.BlockTags.BEDS)
                            || st.is(net.minecraft.tags.BlockTags.DOORS) || st.is(net.minecraft.tags.BlockTags.WOOL)
                            || st.getBlock() instanceof net.minecraft.block.ChestBlock
                            || st.getBlock() instanceof net.minecraft.block.TorchBlock
                            || st.getBlock() instanceof net.minecraft.block.AbstractGlassBlock
                            || st.getBlock() instanceof net.minecraft.block.CraftingTableBlock
                            || st.getBlock() instanceof net.minecraft.block.AbstractFurnaceBlock
                            || st.getBlock() == net.minecraft.block.Blocks.GRASS_PATH) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Called every 2 seconds: plan regions around players and build pieces that are close. At most one per call. */
    public static void tick(ServerWorld world) {
        if (world.dimension() != World.OVERWORLD) {
            return;
        }
        // plan at most one new region per call, so there is no lag spike
        outer:
        for (ServerPlayerEntity p : world.players()) {
            int rx = Math.floorDiv((int) p.getX(), REGION);
            int rz = Math.floorDiv((int) p.getZ(), REGION);
            if (plan(world, rx, rz)) {
                break;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (plan(world, rx + dx, rz + dz)) {
                        break outer;
                    }
                }
            }
        }
        SettlementData data = SettlementData.get(world);
        // camps built before 0.6.1 get their patrol trail once, when a player comes near
        for (SettlementData.Settlement s : data.getSettlements()) {
            if (!s.trail && near(world, s.center) && chunksLoaded(world, s.center.getX(), s.center.getZ(), 18)) {
                boolean swamp = woodFor(world, world.getBiome(s.center)) == CouncilBuilder.WOOD_DARK_OAK;
                PatrolTrail.build(world, s.center, swamp);
                s.trail = true;
                data.setDirty();
                LOG.info("[Moonclaws] patrol trail laid around the settlement at {}", s.center);
                return;
            }
        }
        for (Map.Entry<Long, SettlementData.Group> e : data.getGroups().entrySet()) {
            SettlementData.Group g = e.getValue();
            if (!g.councilBuilt && near(world, g.council) && chunksLoaded(world, g.council.getX(), g.council.getZ(), 20)) {
                buildCouncil(world, g);
                data.setDirty();
                return;
            }
            for (int i = 0; i < g.sites.size(); i++) {
                BlockPos site = g.sites.get(i);
                if (!g.built.get(i) && near(world, site) && chunksLoaded(world, site.getX(), site.getZ(), 18)) {
                    g.built.set(i, true);
                    SettlementData.Settlement s = buildSettlement(world, site, g.clans.get(i));
                    // a Council that already stands gets a platform for the new settlement
                    BlockPos council = councilGround(data, g);
                    if (s != null && council != null && chunksLoaded(world, council.getX(), council.getZ(), 8)) {
                        CouncilBuilder.addPlatform(world, council, s.center.getX(), s.center.getZ());
                    }
                    data.setDirty();
                    return;
                }
            }
        }
    }

    /** Where the Council of this group really stands (its ground), or null if it is not built. */
    private static BlockPos councilGround(SettlementData data, SettlementData.Group g) {
        if (!g.councilBuilt) {
            return null;
        }
        for (BlockPos c : data.getCouncils()) {
            double dx = c.getX() - g.council.getX();
            double dz = c.getZ() - g.council.getZ();
            if (dx * dx + dz * dz < 30 * 30) {
                return c;
            }
        }
        return null;
    }

    private static boolean near(ServerWorld world, BlockPos pos) {
        for (ServerPlayerEntity p : world.players()) {
            double dx = p.getX() - pos.getX();
            double dz = p.getZ() - pos.getZ();
            if (dx * dx + dz * dz < BUILD_DISTANCE * BUILD_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    private static void buildCouncil(ServerWorld world, SettlementData.Group g) {
        g.councilBuilt = true;
        boolean swampCouncil = woodFor(world, world.getBiome(g.council)) == CouncilBuilder.WOOD_DARK_OAK;
        int y = realGround(world, g.council.getX(), g.council.getZ(), 10, swampCouncil ? 14 : 5);
        if (y == Integer.MIN_VALUE) {
            LOG.info("[Moonclaws] Council at {} skipped: water", g.council);
            return;
        }
        BlockPos ground = new BlockPos(g.council.getX(), y, g.council.getZ());
        if (somethingBuilt(world, ground.getX(), ground.getZ(), y, 14)) {
            LOG.info("[Moonclaws] Council at {} skipped: something is built there", ground);
            return;
        }
        fillPonds(world, ground.getX(), ground.getZ(), y, 16);
        int wood = woodFor(world, world.getBiome(ground));
        if (wood < 0) {
            wood = CouncilBuilder.WOOD_OAK;
        }
        CouncilBuilder.build(world, ground, wood);
        SettlementData data = SettlementData.get(world);
        data.addCouncil(ground);
        // a platform for every settlement of the group that already stands
        for (BlockPos site : g.sites) {
            SettlementData.Settlement s = data.settlementNear(site.getX() + 0.5D, site.getZ() + 0.5D, 30.0D);
            if (s != null) {
                CouncilBuilder.addPlatform(world, ground, s.center.getX(), s.center.getZ());
            }
        }
        LOG.info("[Moonclaws] built a Council at {}", ground);
    }

    public static SettlementData.Settlement buildSettlement(ServerWorld world, BlockPos site, int clan) {
        boolean swamp = woodFor(world, world.getBiome(site)) == CouncilBuilder.WOOD_DARK_OAK;
        int y = realGround(world, site.getX(), site.getZ(), 12, swamp ? 14 : 5);
        if (y == Integer.MIN_VALUE) {
            LOG.info("[Moonclaws] settlement at {} skipped: water", site);
            return null;
        }
        BlockPos ground = new BlockPos(site.getX(), y, site.getZ());
        if (somethingBuilt(world, ground.getX(), ground.getZ(), y, 16)) {
            LOG.info("[Moonclaws] settlement at {} skipped: something is built there", ground);
            return null;
        }
        fillPonds(world, ground.getX(), ground.getZ(), y, 17);
        SettlementBuilder.clearTrees(world, ground.getX(), ground.getZ(), y, 17);
        SettlementBuilder.Layout layout = SettlementBuilder.build(world, ground);
        if (swamp) {
            SwampStyle.apply(world, layout);
        }
        SettlementData.Settlement s = SettlementSpawner.populate(world, layout, clan, new Random(ground.asLong()));
        PatrolTrail.build(world, ground, swamp);
        s.trail = true;
        LOG.info("[Moonclaws] built a settlement at {} (clan {})", ground, clan);
        return s;
    }

    /** For the /moonclaws nearest command: groups around a position, nearest first. */
    public static List<SettlementData.Group> nearestGroups(ServerWorld world, BlockPos pos) {
        int rx = Math.floorDiv(pos.getX(), REGION);
        int rz = Math.floorDiv(pos.getZ(), REGION);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                plan(world, rx + dx, rz + dz);
            }
        }
        List<SettlementData.Group> groups = new ArrayList<>(SettlementData.get(world).getGroups().values());
        groups.sort((a, b) -> Double.compare(a.council.distSqr(pos), b.council.distSqr(pos)));
        return groups;
    }
}
