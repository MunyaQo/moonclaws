package com.moonclaws.mod;

import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITIES, MoonclawsMod.MODID);

    public static final RegistryObject<EntityType<StrayCatEntity>> STRAY_CAT = ENTITIES.register("stray_cat",
            () -> EntityType.Builder.<StrayCatEntity>of(StrayCatEntity::new, EntityClassification.CREATURE)
                    .sized(0.6F, 0.7F)
                    .clientTrackingRange(8)
                    .build(MoonclawsMod.MODID + ":stray_cat"));

    private ModEntities() {
    }
}
