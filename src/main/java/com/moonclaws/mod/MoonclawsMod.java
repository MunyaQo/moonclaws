package com.moonclaws.mod;

import com.moonclaws.mod.client.ClientEvents;
import com.moonclaws.mod.client.ClientSetup;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolType;
import net.minecraftforge.common.extensions.IForgeContainerType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
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

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<ContainerType<?>> CONTAINERS =
            DeferredRegister.create(ForgeRegistries.CONTAINERS, MODID);

    public static final RegistryObject<Item> MOON_STONE = ITEMS.register("moon_stone",
            () -> new MoonStoneItem(new Item.Properties().tab(ItemGroup.TAB_MISC).stacksTo(1)));

    public static final RegistryObject<Item> STRAY_CAT_EGG = ITEMS.register("stray_cat_egg",
            () -> new StrayCatEggItem(new Item.Properties().tab(ItemGroup.TAB_MISC)));

    public static final RegistryObject<Block> HEALER_STONE = BLOCKS.register("healer_stone",
            () -> new HealerStoneBlock(AbstractBlock.Properties.of(Material.STONE)
                    .strength(1.5F, 6.0F).sound(SoundType.STONE)
                    .harvestTool(ToolType.PICKAXE).noOcclusion()));

    public static final RegistryObject<Item> HEALER_STONE_ITEM = ITEMS.register("healer_stone",
            () -> new BlockItem(HEALER_STONE.get(), new Item.Properties().tab(ItemGroup.TAB_DECORATIONS)));

    public static final RegistryObject<Item> HERB_POULTICE = ITEMS.register("herb_poultice",
            () -> new HerbPoulticeItem(new Item.Properties().tab(ItemGroup.TAB_BREWING).stacksTo(16)));

    public static final RegistryObject<Block> HAY_NEST = nest("hay_nest", NestBlock.HAY, SoundType.GRASS);
    public static final RegistryObject<Block> GRASS_NEST = nest("grass_nest", NestBlock.GRASS, SoundType.GRASS);
    public static final RegistryObject<Block> FEATHER_NEST = nest("feather_nest", NestBlock.FEATHER, SoundType.WOOL);
    public static final RegistryObject<Block> FERN_NEST = nest("fern_nest", NestBlock.FERN, SoundType.GRASS);

    public static final RegistryObject<ContainerType<CatTradeContainer>> CAT_TRADE = CONTAINERS.register(
            "cat_trade", () -> IForgeContainerType.create((id, inventory, data) -> new CatTradeContainer(id, inventory)));

    public static final RegistryObject<ContainerType<HealerContainer>> HEALER_CONTAINER = CONTAINERS.register(
            "healer_stone", () -> IForgeContainerType.create((id, inventory, data) -> new HealerContainer(id, inventory)));

    private static RegistryObject<Block> nest(String name, int kind, SoundType sound) {
        RegistryObject<Block> block = BLOCKS.register(name, () -> new NestBlock(kind,
                AbstractBlock.Properties.of(Material.GRASS).strength(0.4F).sound(sound).noOcclusion()));
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().tab(ItemGroup.TAB_DECORATIONS)));
        return block;
    }

    /** Nest block for a kind (NestBlock.HAY ... NestBlock.FERN). */
    public static Block nestOf(int kind) {
        switch (kind) {
            case NestBlock.GRASS: return GRASS_NEST.get();
            case NestBlock.FEATHER: return FEATHER_NEST.get();
            case NestBlock.FERN: return FERN_NEST.get();
            default: return HAY_NEST.get();
        }
    }

    public MoonclawsMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        CONTAINERS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::entityAttributes);

        MinecraftForge.EVENT_BUS.register(new CommonEvents());
        MinecraftForge.EVENT_BUS.register(new RankEvents());
        MinecraftForge.EVENT_BUS.register(new ClanEvents());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            MinecraftForge.EVENT_BUS.register(new ClientEvents());
            ClientSetup.register(modBus);
        });
    }

    private void entityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.STRAY_CAT.get(), CatEntity.createAttributes().build());
        event.put(ModEntities.CLAN_CAT.get(), CatEntity.createAttributes().build());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
        event.enqueueWork(() ->
                // stray cats: any solid ground (sand too), in daylight-level light
                EntitySpawnPlacementRegistry.register(ModEntities.STRAY_CAT.get(),
                        EntitySpawnPlacementRegistry.PlacementType.ON_GROUND,
                        Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                        (type, world, reason, pos, random) ->
                                MobEntity.checkMobSpawnRules(type, world, reason, pos, random)
                                        && world.getRawBrightness(pos, 0) > 8));
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
