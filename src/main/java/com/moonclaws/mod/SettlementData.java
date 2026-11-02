package com.moonclaws.mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.LongNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;

/**
 * Everything the world remembers about cats: built settlements (with dens and nests), Council clearings,
 * planned groups (Council + settlements around it) and which regions were already looked at.
 */
public class SettlementData extends WorldSavedData {

    private static final String NAME = "moonclaws_settlements";

    // den kinds (same order as in SettlementBuilder)
    public static final int DEN_HEALERS = 0;
    public static final int DEN_WARRIORS = 1;
    public static final int DEN_APPRENTICES = 2;
    public static final int DEN_NURSERY = 3;
    public static final int DEN_SHE_WARRIORS = 4;
    public static final int DEN_LEADERS = 5;

    /** A den: its kind and the box a player is "inside" of. */
    public static final class Den {
        public final int kind;
        public final BlockPos min;
        public final BlockPos max;

        public Den(int kind, BlockPos min, BlockPos max) {
            this.kind = kind;
            this.min = min.immutable();
            this.max = max.immutable();
        }

        public boolean contains(double x, double y, double z) {
            return x >= min.getX() && x < max.getX() + 1 && y >= min.getY() - 0.5D && y < max.getY() + 1
                    && z >= min.getZ() && z < max.getZ() + 1;
        }

        /** Strangers are warned in all dens except the healers' and the leaders'. */
        public boolean isPrivate() {
            return kind != DEN_HEALERS && kind != DEN_LEADERS;
        }
    }

    public static final class Settlement {
        public int id;
        public BlockPos center;
        public int clan;
        public String leaderName = "";
        public final List<Den> dens = new ArrayList<>();
        public final List<BlockPos> nests = new ArrayList<>();

        public Den denAt(double x, double y, double z) {
            for (Den d : dens) {
                if (d.contains(x, y, z)) {
                    return d;
                }
            }
            return null;
        }
    }

    /** A planned group: a Council in the middle and settlement sites around it. */
    public static final class Group {
        public BlockPos council;
        public boolean councilBuilt;
        public final List<BlockPos> sites = new ArrayList<>();
        public final List<Integer> clans = new ArrayList<>();
        public final List<Boolean> built = new ArrayList<>();
    }

    private final List<BlockPos> centers = new ArrayList<>();
    private final Map<Integer, Settlement> settlements = new HashMap<>();
    private final List<BlockPos> councils = new ArrayList<>();
    private final Map<Long, Group> groups = new HashMap<>();
    private final Set<Long> emptyRegions = new HashSet<>();
    private int nextId = 1;
    private int planVersion;

    public SettlementData() {
        super(NAME);
    }

    public static SettlementData get(ServerWorld world) {
        return world.getDataStorage().computeIfAbsent(SettlementData::new, NAME);
    }

    // ------------------------------------------------------------------ simple zones (old API)

    public void add(BlockPos pos) {
        centers.add(pos.immutable());
        setDirty();
    }

    /** Horizontal distance only: caves under the settlement are protected too. Councils count as well. */
    public boolean isNear(double x, double z, double radius) {
        double r2 = radius * radius;
        for (BlockPos c : centers) {
            if (dist2(c, x, z) <= r2) {
                return true;
            }
        }
        for (BlockPos c : councils) {
            if (dist2(c, x, z) <= r2) {
                return true;
            }
        }
        return false;
    }

    private static double dist2(BlockPos c, double x, double z) {
        double dx = c.getX() + 0.5D - x;
        double dz = c.getZ() + 0.5D - z;
        return dx * dx + dz * dz;
    }

    /** Closest settlement (built, or planned in a group) — the elder's memory. */
    public BlockPos nearest(double x, double z) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        List<BlockPos> all = new ArrayList<>(centers);
        for (Group g : groups.values()) {
            all.addAll(g.sites);
        }
        for (BlockPos c : all) {
            double d = dist2(c, x, z);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    public int count() {
        return centers.size();
    }

    // ------------------------------------------------------------------ settlements

    public Settlement newSettlement(BlockPos center, int clan) {
        Settlement s = new Settlement();
        s.id = nextId++;
        s.center = center.immutable();
        s.clan = clan;
        settlements.put(s.id, s);
        centers.add(s.center);
        setDirty();
        return s;
    }

    public Settlement getSettlement(int id) {
        return settlements.get(id);
    }

    public Settlement settlementNear(double x, double z, double radius) {
        double r2 = radius * radius;
        for (Settlement s : settlements.values()) {
            if (dist2(s.center, x, z) <= r2) {
                return s;
            }
        }
        return null;
    }

    public void addCouncil(BlockPos pos) {
        councils.add(pos.immutable());
        setDirty();
    }

    public List<BlockPos> getCouncils() {
        return councils;
    }

    // ------------------------------------------------------------------ groups (world generation)

    /** When the planning rules change, regions that were found empty are looked at again. */
    public void upgradePlans(int version) {
        if (planVersion < version) {
            emptyRegions.clear();
            // places that were skipped (no settlement stands there) get another chance
            for (Group g : groups.values()) {
                for (int i = 0; i < g.sites.size(); i++) {
                    BlockPos site = g.sites.get(i);
                    if (g.built.get(i) && settlementNear(site.getX() + 0.5D, site.getZ() + 0.5D, 30.0D) == null) {
                        g.built.set(i, false);
                    }
                }
                if (g.councilBuilt && !councils.stream().anyMatch(c -> dist2(c, g.council.getX(), g.council.getZ()) < 30 * 30)) {
                    g.councilBuilt = false;
                }
            }
            planVersion = version;
            setDirty();
        }
    }

    public boolean isRegionKnown(long key) {
        return groups.containsKey(key) || emptyRegions.contains(key);
    }

    public void markRegionEmpty(long key) {
        emptyRegions.add(key);
        setDirty();
    }

    public void putGroup(long key, Group g) {
        groups.put(key, g);
        setDirty();
    }

    public Map<Long, Group> getGroups() {
        return groups;
    }

    // ------------------------------------------------------------------ saving

    private static long[] toLongs(List<BlockPos> list) {
        long[] values = new long[list.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = list.get(i).asLong();
        }
        return values;
    }

    private static void fromLongs(long[] values, List<BlockPos> into) {
        for (long v : values) {
            into.add(BlockPos.of(v));
        }
    }

    @Override
    public void load(CompoundNBT nbt) {
        centers.clear();
        settlements.clear();
        councils.clear();
        groups.clear();
        emptyRegions.clear();
        fromLongs(nbt.getLongArray("centers"), centers);
        fromLongs(nbt.getLongArray("councils"), councils);
        nextId = Math.max(1, nbt.getInt("nextId"));
        planVersion = nbt.getInt("planVersion");
        for (INBT t : nbt.getList("settlements", 10)) {
            CompoundNBT c = (CompoundNBT) t;
            Settlement s = new Settlement();
            s.id = c.getInt("id");
            s.center = BlockPos.of(c.getLong("center"));
            s.clan = c.getInt("clan");
            s.leaderName = c.getString("leader");
            fromLongs(c.getLongArray("nests"), s.nests);
            for (INBT dt : c.getList("dens", 10)) {
                CompoundNBT d = (CompoundNBT) dt;
                s.dens.add(new Den(d.getInt("kind"), BlockPos.of(d.getLong("min")), BlockPos.of(d.getLong("max"))));
            }
            settlements.put(s.id, s);
            nextId = Math.max(nextId, s.id + 1);
        }
        for (INBT t : nbt.getList("groups", 10)) {
            CompoundNBT c = (CompoundNBT) t;
            Group g = new Group();
            g.council = BlockPos.of(c.getLong("council"));
            g.councilBuilt = c.getBoolean("councilBuilt");
            fromLongs(c.getLongArray("sites"), g.sites);
            for (int clan : c.getIntArray("clans")) {
                g.clans.add(clan);
            }
            for (byte b : c.getByteArray("built")) {
                g.built.add(b != 0);
            }
            while (g.clans.size() < g.sites.size()) {
                g.clans.add(0);
            }
            while (g.built.size() < g.sites.size()) {
                g.built.add(false);
            }
            groups.put(c.getLong("key"), g);
        }
        for (INBT t : nbt.getList("emptyRegions", 4)) {
            emptyRegions.add(((LongNBT) t).getAsLong());
        }
    }

    @Override
    public CompoundNBT save(CompoundNBT nbt) {
        nbt.putLongArray("centers", toLongs(centers));
        nbt.putLongArray("councils", toLongs(councils));
        nbt.putInt("nextId", nextId);
        nbt.putInt("planVersion", planVersion);
        ListNBT list = new ListNBT();
        for (Settlement s : settlements.values()) {
            CompoundNBT c = new CompoundNBT();
            c.putInt("id", s.id);
            c.putLong("center", s.center.asLong());
            c.putInt("clan", s.clan);
            c.putString("leader", s.leaderName);
            c.putLongArray("nests", toLongs(s.nests));
            ListNBT dens = new ListNBT();
            for (Den d : s.dens) {
                CompoundNBT dc = new CompoundNBT();
                dc.putInt("kind", d.kind);
                dc.putLong("min", d.min.asLong());
                dc.putLong("max", d.max.asLong());
                dens.add(dc);
            }
            c.put("dens", dens);
            list.add(c);
        }
        nbt.put("settlements", list);
        ListNBT groupList = new ListNBT();
        for (Map.Entry<Long, Group> e : groups.entrySet()) {
            Group g = e.getValue();
            CompoundNBT c = new CompoundNBT();
            c.putLong("key", e.getKey());
            c.putLong("council", g.council.asLong());
            c.putBoolean("councilBuilt", g.councilBuilt);
            c.putLongArray("sites", toLongs(g.sites));
            int[] clans = new int[g.clans.size()];
            for (int i = 0; i < clans.length; i++) {
                clans[i] = g.clans.get(i);
            }
            c.putIntArray("clans", clans);
            byte[] built = new byte[g.built.size()];
            for (int i = 0; i < built.length; i++) {
                built[i] = (byte) (g.built.get(i) ? 1 : 0);
            }
            c.putByteArray("built", built);
            groupList.add(c);
        }
        nbt.put("groups", groupList);
        ListNBT empty = new ListNBT();
        for (long k : emptyRegions) {
            empty.add(LongNBT.valueOf(k));
        }
        nbt.put("emptyRegions", empty);
        return nbt;
    }
}
