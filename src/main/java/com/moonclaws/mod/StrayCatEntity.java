package com.moonclaws.mod;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.world.World;

/** A stray cat: looks and behaves like a vanilla cat, but can be traded with (right click with an empty hand). */
public class StrayCatEntity extends CatEntity {

    /** Rare skins only: british shorthair, calico, persian, ragdoll, jellie. */
    public static final int[] RARE_SKINS = {4, 5, 6, 7, 9};

    public StrayCatEntity(EntityType<? extends CatEntity> type, World world) {
        super(type, world);
    }
}
