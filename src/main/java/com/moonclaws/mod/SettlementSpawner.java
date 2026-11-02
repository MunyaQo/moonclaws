package com.moonclaws.mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.block.HorizontalBlock;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

/** Fills a freshly built settlement with nests and clan cats, and registers it in the world data. */
public final class SettlementSpawner {

    /** "Young clan": up to 8 cats = 8 nests. */
    public static final int YOUNG_CLAN_NESTS = 8;
    public static final int CLAN_NAMES = 24;

    private SettlementSpawner() {
    }

    private static final class Resident {
        final int rank;
        final int gender;

        Resident(int rank, int gender) {
            this.rank = rank;
            this.gender = gender;
        }
    }

    public static SettlementData.Settlement populate(ServerWorld world, SettlementBuilder.Layout layout, int clan, Random rnd) {
        SettlementData data = SettlementData.get(world);
        SettlementData.Settlement s = data.newSettlement(layout.center, clan);
        s.dens.addAll(layout.dens);

        // who lives here: 6-7 cats
        List<Resident> people = new ArrayList<>();
        people.add(new Resident(CatState.RANK_LEADER, gender(rnd)));
        people.add(new Resident(CatState.RANK_DEPUTY, gender(rnd)));
        people.add(new Resident(CatState.RANK_HEALER, gender(rnd)));
        int warriors = 2 + rnd.nextInt(2);
        int apprentices = 1 + rnd.nextInt(2);
        int elders = rnd.nextInt(2);
        while (3 + warriors + apprentices + elders > 7) {
            if (apprentices > 1) {
                apprentices--;
            } else {
                warriors--;
            }
        }
        for (int i = 0; i < warriors; i++) {
            people.add(new Resident(CatState.RANK_WARRIOR, gender(rnd)));
        }
        for (int i = 0; i < apprentices; i++) {
            people.add(new Resident(CatState.RANK_APPRENTICE, gender(rnd)));
        }
        for (int i = 0; i < elders; i++) {
            people.add(new Resident(CatState.RANK_ELDER, gender(rnd)));
        }

        Set<String> usedStems = new HashSet<>();
        for (Resident r : people) {
            int den = denFor(r);
            List<BlockPos> free = layout.slotsOf(den);
            if (free.isEmpty()) {
                free = layout.slotsOf(SettlementData.DEN_NURSERY);
            }
            if (free.isEmpty()) {
                continue;
            }
            BlockPos nest = free.remove(0);
            placeNest(world, nest, nestKind(den), rnd);
            s.nests.add(nest);

            String[] name;
            int tries = 0;
            do {
                name = CatNames.random(CatNames.LANG_RU, rnd);
                tries++;
            } while (usedStems.contains(name[1]) && tries < 20);
            usedStems.add(name[1]);

            ClanCatEntity cat = ModEntities.CLAN_CAT.get().create(world);
            if (cat == null) {
                continue;
            }
            cat.moveTo(nest.getX() + 0.5D, nest.getY() + 0.3D, nest.getZ() + 0.5D, rnd.nextFloat() * 360.0F, 0.0F);
            cat.setup(s.id, r.rank, r.gender, name, nest, layout.center.above(-3), rnd.nextInt(CatState.SKINS));
            world.addFreshEntity(cat);
            if (r.rank == CatState.RANK_LEADER) {
                s.leaderName = cat.getCatName();
            }
        }

        // free nests up to the limit go to the nursery (room for kits later)
        List<BlockPos> nursery = layout.slotsOf(SettlementData.DEN_NURSERY);
        while (s.nests.size() < YOUNG_CLAN_NESTS && !nursery.isEmpty()) {
            BlockPos nest = nursery.remove(0);
            placeNest(world, nest, NestBlock.FEATHER, rnd);
            s.nests.add(nest);
        }
        data.setDirty();
        return s;
    }

    private static int gender(Random rnd) {
        return rnd.nextBoolean() ? CatState.GENDER_TOM : CatState.GENDER_SHE;
    }

    private static int denFor(Resident r) {
        switch (r.rank) {
            case CatState.RANK_LEADER:
            case CatState.RANK_DEPUTY:
                return SettlementData.DEN_LEADERS;
            case CatState.RANK_HEALER:
                return SettlementData.DEN_HEALERS;
            case CatState.RANK_APPRENTICE:
                return SettlementData.DEN_APPRENTICES;
            case CatState.RANK_ELDER:
                return SettlementData.DEN_NURSERY;
            default:
                return r.gender == CatState.GENDER_SHE ? SettlementData.DEN_SHE_WARRIORS : SettlementData.DEN_WARRIORS;
        }
    }

    private static int nestKind(int den) {
        switch (den) {
            case SettlementData.DEN_HEALERS: return NestBlock.GRASS;
            case SettlementData.DEN_LEADERS: return NestBlock.FERN;
            case SettlementData.DEN_NURSERY: return NestBlock.FEATHER;
            default: return NestBlock.HAY;
        }
    }

    private static void placeNest(ServerWorld world, BlockPos pos, int kind, Random rnd) {
        Direction facing = Direction.from2DDataValue(rnd.nextInt(4));
        world.setBlock(pos, MoonclawsMod.nestOf(kind).defaultBlockState().setValue(HorizontalBlock.FACING, facing), 2);
    }
}
