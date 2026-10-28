package com.moonclaws.mod;

import com.moonclaws.mod.client.ClientEvents;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(MoonclawsMod.MODID)
public class MoonclawsMod {
    public static final String MODID = "moonclaws";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<Item> MOON_STONE = ITEMS.register("moon_stone",
            () -> new MoonStoneItem(new Item.Properties().tab(ItemGroup.TAB_MISC).stacksTo(1)));

    public MoonclawsMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        modBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(new CommonEvents());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.register(new ClientEvents()));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
        event.enqueueWork(() -> {
            // cats should spawn on grass like other animals (if the game already has a rule, keep it)
            try {
                EntitySpawnPlacementRegistry.register(EntityType.CAT,
                        EntitySpawnPlacementRegistry.PlacementType.ON_GROUND,
                        Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                        AnimalEntity::checkAnimalSpawnRules);
            } catch (IllegalStateException ignored) {
            }
        });
    }
}
