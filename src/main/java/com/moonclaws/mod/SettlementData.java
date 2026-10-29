package com.moonclaws.mod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;

/** Remembers where cat settlements were built (per world), used for the "no hostile mobs" zone. */
public class SettlementData extends WorldSavedData {

    private static final String NAME = "moonclaws_settlements";

    private final List<BlockPos> centers = new ArrayList<>();

    public SettlementData() {
        super(NAME);
    }

    public static SettlementData get(ServerWorld world) {
        return world.getDataStorage().computeIfAbsent(SettlementData::new, NAME);
    }

    public void add(BlockPos pos) {
        centers.add(pos.immutable());
        setDirty();
    }

    /** Horizontal distance only: caves under the settlement are protected too. */
    public boolean isNear(double x, double z, double radius) {
        double r2 = radius * radius;
        for (BlockPos c : centers) {
            double dx = c.getX() + 0.5D - x;
            double dz = c.getZ() + 0.5D - z;
            if (dx * dx + dz * dz <= r2) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void load(CompoundNBT nbt) {
        centers.clear();
        for (long value : nbt.getLongArray("centers")) {
            centers.add(BlockPos.of(value));
        }
    }

    @Override
    public CompoundNBT save(CompoundNBT nbt) {
        long[] values = new long[centers.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = centers.get(i).asLong();
        }
        nbt.putLongArray("centers", values);
        return nbt;
    }
}
