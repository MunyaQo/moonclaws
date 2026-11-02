package com.moonclaws.mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Normal cats that appear in villages and witch huts become kittypets.
 * Cats in the wild (they replace wolves in this mod) stay as they are, and tame cats are never touched.
 * The swap happens one tick later, so the world is not changed in the middle of loading a chunk.
 */
public class KittypetEvents {

    private static final String CHECKED = "MoonclawsKittypetChecked";
    private final List<CatEntity> queue = new ArrayList<>();

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent event) {
        Entity e = event.getEntity();
        if (e.level.isClientSide || e.getClass() != CatEntity.class) {
            return;
        }
        CatEntity cat = (CatEntity) e;
        if (cat.isTame() || cat.getPersistentData().getBoolean(CHECKED)) {
            return;
        }
        queue.add(cat);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof ServerWorld) || queue.isEmpty()) {
            return;
        }
        ServerWorld world = (ServerWorld) event.world;
        List<CatEntity> now = new ArrayList<>();
        queue.removeIf(c -> {
            if (c.level == world) {
                now.add(c);
                return true;
            }
            return !c.isAlive();
        });
        for (CatEntity cat : now) {
            if (!cat.isAlive() || cat.isTame()) {
                continue;
            }
            cat.getPersistentData().putBoolean(CHECKED, true);
            BlockPos pos = cat.blockPosition();
            boolean hut = world.structureFeatureManager().getStructureAt(pos, true, Structure.SWAMP_HUT).isValid();
            boolean village = !hut && (world.isCloseToVillage(pos, 2)
                    || world.structureFeatureManager().getStructureAt(pos, true, Structure.VILLAGE).isValid());
            if (hut || village) {
                replace(world, cat, hut);
            }
        }
    }

    static KittypetEntity replace(ServerWorld world, CatEntity cat, boolean witch) {
        KittypetEntity pet = ModEntities.KITTYPET.get().create(world);
        if (pet == null) {
            return null;
        }
        pet.moveTo(cat.getX(), cat.getY(), cat.getZ(), cat.yRot, cat.xRot);
        pet.setupNew(new Random(), witch, cat.getCatType());
        if (cat.isBaby()) {
            pet.setAge(cat.getAge());
        }
        cat.remove();
        world.addFreshEntity(pet);
        return pet;
    }
}
