package com.moonclaws.mod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.loot.LootTable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.MutableRegistry;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biomes;
import net.minecraft.world.biome.MobSpawnInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * /moonclaws test — checks everything that can be checked without playing by hand.
 * Every line also goes to the game log (logs/latest.log) with the prefix [Moonclaws test].
 */
public final class SelfTest {

    private static final Logger LOG = LogManager.getLogger("Moonclaws");

    private final List<String> failed = new ArrayList<>();
    private int passed;

    private SelfTest() {
    }

    private void check(String name, Supplier<Boolean> test) {
        boolean ok;
        String extra = "";
        try {
            ok = Boolean.TRUE.equals(test.get());
        } catch (RuntimeException e) {
            ok = false;
            extra = " (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
        }
        if (ok) {
            passed++;
            LOG.info("[Moonclaws test] OK   {}", name);
        } else {
            failed.add(name + extra);
            LOG.warn("[Moonclaws test] FAIL {}{}", name, extra);
        }
    }

    public static int run(CommandSource source) {
        SelfTest t = new SelfTest();
        MinecraftServer server = source.getServer();
        LOG.info("[Moonclaws test] ---- start ----");

        // registry
        t.check("item moon_stone", () -> MoonclawsMod.MOON_STONE.get() != null);
        t.check("item stray_cat_egg", () -> MoonclawsMod.STRAY_CAT_EGG.get() != null);
        t.check("block healer_stone", () -> MoonclawsMod.HEALER_STONE.get() != null);
        t.check("item healer_stone", () -> MoonclawsMod.HEALER_STONE_ITEM.get() != null);
        t.check("item herb_poultice (stack 16)", () -> new ItemStack(MoonclawsMod.HERB_POULTICE.get()).getMaxStackSize() == 16);
        t.check("container healer_stone", () -> MoonclawsMod.HEALER_CONTAINER.get() != null);
        t.check("stray cat has attributes", () -> {
            StrayCatEntity cat = ModEntities.STRAY_CAT.get().create(source.getLevel());
            return cat != null && cat.getAttribute(Attributes.MAX_HEALTH) != null;
        });

        // 0.6.0 kittypets
        t.check("kittypet has attributes and a collar", () -> {
            KittypetEntity pet = ModEntities.KITTYPET.get().create(source.getLevel());
            if (pet == null || pet.getAttribute(Attributes.MAX_HEALTH) == null) {
                return false;
            }
            pet.setupNew(new java.util.Random(1), false, 2);
            return pet.isTame() && pet.getOwnerUUID() == null && !pet.getPetName().isEmpty() && pet.getPull() <= 40;
        });
        t.check("witch cat is black", () -> {
            KittypetEntity pet = ModEntities.KITTYPET.get().create(source.getLevel());
            pet.setupNew(new java.util.Random(2), true, 3);
            return pet.isWitchCat() && pet.getCatType() == KittypetEntity.ALL_BLACK;
        });
        t.check("kittypet trades: 4 offers", () -> CatTrades.kittypet(new java.util.Random(3)).size() == 4
                && CatTrades.witchCat(new java.util.Random(4)).size() == 4);
        t.check("red kittypet stem", () -> {
            String st = KittypetEntity.stemFor(2, new java.util.Random(5));
            return !st.isEmpty() && CatNames.compose(CatState.RANK_APPRENTICE, CatNames.LANG_RU, "Барсик", st, "").endsWith("лап");
        });
        t.check("neutered about 70%", () -> {
            int n = 0;
            java.util.Random r = new java.util.Random(6);
            for (int i = 0; i < 200; i++) {
                KittypetEntity pet = ModEntities.KITTYPET.get().create(source.getLevel());
                pet.setupNew(r, false, 0);
                if (pet.isNeutered()) {
                    n++;
                }
            }
            return n > 120 && n < 160;
        });

        // data files
        t.check("recipe moon_stone", () -> recipe(server, "moon_stone").isPresent());
        t.check("recipe healer_stone gives the stone", () -> recipe(server, "healer_stone")
                .map(r -> r.getResultItem().getItem() == MoonclawsMod.HEALER_STONE_ITEM.get()).orElse(false));
        t.check("loot table healer_stone", () -> server.getLootTables()
                .get(new ResourceLocation(MoonclawsMod.MODID, "blocks/healer_stone")) != LootTable.EMPTY);

        // stray cat spawning
        MutableRegistry<Biome> biomes = server.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        t.check("stray cats spawn in plains", () -> hasStray(biomes, Biomes.PLAINS));
        t.check("stray cats spawn in desert", () -> hasStray(biomes, Biomes.DESERT));
        t.check("stray cats spawn in snowy tundra", () -> hasStray(biomes, Biomes.SNOWY_TUNDRA));
        t.check("no stray cats in ocean", () -> !hasStray(biomes, Biomes.OCEAN));
        t.check("no stray cats on mushroom fields", () -> !hasStray(biomes, Biomes.MUSHROOM_FIELDS));

        // medicine
        ItemStack poppy = new ItemStack(Items.POPPY);
        ItemStack corn = new ItemStack(Items.CORNFLOWER);
        ItemStack kelp = new ItemStack(Items.KELP);
        ItemStack web = new ItemStack(Items.COBWEB);
        ItemStack none = ItemStack.EMPTY;
        t.check("healer: 3 herbs + cobweb works", () -> !Medicine.result(CatState.RANK_HEALER,
                Arrays.asList(poppy, corn, kelp), web).isEmpty());
        t.check("3 herbs + cobweb = Regeneration III 8 s", () -> Arrays.equals(Medicine.regen(3, true), new int[] {2, 8}));
        t.check("2 herbs + cobweb = Regeneration II 12 s", () -> Arrays.equals(Medicine.regen(2, true), new int[] {1, 12}));
        t.check("1 herb = Regeneration I 8 s", () -> Arrays.equals(Medicine.regen(1, false), new int[] {0, 8}));
        t.check("warrior: 1 herb works", () -> !Medicine.result(CatState.RANK_WARRIOR,
                Arrays.asList(poppy, none, none), none).isEmpty());
        t.check("warrior: 2 herbs do not work", () -> Medicine.result(CatState.RANK_WARRIOR,
                Arrays.asList(poppy, corn, none), none).isEmpty());
        t.check("warrior: cobweb does not work", () -> Medicine.result(CatState.RANK_WARRIOR,
                Arrays.asList(poppy, none, none), web).isEmpty());
        t.check("kit cannot use the stone", () -> Medicine.result(CatState.RANK_KIT,
                Arrays.asList(poppy, none, none), none).isEmpty());
        t.check("apprentice cannot use the stone", () -> Medicine.result(CatState.RANK_APPRENTICE,
                Arrays.asList(poppy, none, none), none).isEmpty());
        t.check("two poppies: bonus counted once", () -> Medicine.uniqueHerbs(Medicine.result(CatState.RANK_HEALER,
                Arrays.asList(poppy, poppy, none), none)).size() == 1);
        t.check("same herbs in any order stack together", () -> ItemStack.tagMatches(
                Medicine.result(CatState.RANK_HEALER, Arrays.asList(poppy, corn, none), none),
                Medicine.result(CatState.RANK_HEALER, Arrays.asList(corn, poppy, none), none)));
        t.check("dirt is not a herb", () -> !Medicine.isHerb(new ItemStack(Items.DIRT)));

        // names
        t.check("name: kit Lastochka", () -> "Ласточка".equals(
                CatNames.compose(CatState.RANK_KIT, CatNames.LANG_RU, "Ласточка", "Ласточко", "хвост")));
        t.check("name: apprentice Lastochkolap", () -> "Ласточколап".equals(
                CatNames.compose(CatState.RANK_APPRENTICE, CatNames.LANG_RU, "Ласточка", "Ласточко", "хвост")));
        t.check("name: warrior Lastochkokhvost", () -> "Ласточкохвост".equals(
                CatNames.compose(CatState.RANK_WARRIOR, CatNames.LANG_RU, "Ласточка", "Ласточко", "хвост")));
        t.check("name: leader Lastochkozvyozd", () -> "Ласточкозвёзд".equals(
                CatNames.compose(CatState.RANK_LEADER, CatNames.LANG_RU, "Ласточка", "Ласточко", "хвост")));
        t.check("name: stem guessed from Lastochka", () -> "Ласточко".equals(CatNames.guessStem("Ласточка", CatNames.LANG_RU)));
        t.check("name: English Firepaw", () -> "Firepaw".equals(
                CatNames.compose(CatState.RANK_APPRENTICE, CatNames.LANG_EN, "Firekit", "Fire", "heart")));
        t.check("name: formatting codes removed", () -> "Мурка".equals(CatNames.clean("§cМурка")));

        // classes
        t.check("hearts: kit 6", () -> 20 + CatState.healthDelta(CatState.RANK_KIT) == 12);
        t.check("hearts: warrior 10", () -> 20 + CatState.healthDelta(CatState.RANK_WARRIOR) == 20);
        t.check("hearts: healer 8", () -> 20 + CatState.healthDelta(CatState.RANK_HEALER) == 16);
        t.check("hearts: elder 7", () -> 20 + CatState.healthDelta(CatState.RANK_ELDER) == 14);
        t.check("trade bonus: deputy +1, elder +2", () -> CatState.tradeBonus(CatState.RANK_DEPUTY) == 1
                && CatState.tradeBonus(CatState.RANK_ELDER) == 2 && CatState.tradeBonus(CatState.RANK_WARRIOR) == 0);

        // 0.5.0: nests, clan cats, trades, groups
        t.check("nests registered (4)", () -> MoonclawsMod.nestOf(NestBlock.HAY) != MoonclawsMod.nestOf(NestBlock.FERN)
                && MoonclawsMod.nestOf(NestBlock.GRASS) != MoonclawsMod.nestOf(NestBlock.FEATHER));
        t.check("recipe hay_nest", () -> recipe(server, "hay_nest").isPresent());
        t.check("recipe grass_nest", () -> recipe(server, "grass_nest").isPresent());
        t.check("recipe feather_nest", () -> recipe(server, "feather_nest").isPresent());
        t.check("recipe fern_nest", () -> recipe(server, "fern_nest").isPresent());
        t.check("loot table hay_nest", () -> server.getLootTables()
                .get(new ResourceLocation(MoonclawsMod.MODID, "blocks/hay_nest")) != LootTable.EMPTY);
        t.check("clan cat has attributes", () -> {
            ClanCatEntity cat = ModEntities.CLAN_CAT.get().create(source.getLevel());
            return cat != null && cat.getAttribute(Attributes.MAX_HEALTH) != null;
        });
        t.check("trades: healer, warrior, apprentice, elder, deputy have offers", () ->
                !CatTrades.forRank(CatState.RANK_HEALER).isEmpty() && !CatTrades.forRank(CatState.RANK_WARRIOR).isEmpty()
                        && !CatTrades.forRank(CatState.RANK_APPRENTICE).isEmpty() && !CatTrades.forRank(CatState.RANK_ELDER).isEmpty()
                        && !CatTrades.forRank(CatState.RANK_DEPUTY).isEmpty());
        t.check("trades: leader does not trade yet", () -> CatTrades.forRank(CatState.RANK_LEADER).isEmpty());
        t.check("trades: stray cats 4 offers", () -> CatTrades.stray().size() == 4);
        t.check("trades: healer sells a poultice", () -> CatTrades.forRank(CatState.RANK_HEALER).stream()
                .anyMatch(o -> o.getResult().getItem() == MoonclawsMod.HERB_POULTICE.get()));
        t.check("container cat_trade", () -> MoonclawsMod.CAT_TRADE.get() != null);
        t.check("24 clan names", () -> SettlementSpawner.CLAN_NAMES == 24);
        t.check("young clan: 8 nests", () -> SettlementSpawner.YOUNG_CLAN_NESTS == 8);
        t.check("groups: region key is unique", () -> ClanWorldgen.key(1, 2) != ClanWorldgen.key(2, 1)
                && ClanWorldgen.key(-1, 0) != ClanWorldgen.key(0, -1));

        LOG.info("[Moonclaws test] ---- done: {} ok, {} failed ----", t.passed, t.failed.size());
        if (t.failed.isEmpty()) {
            source.sendSuccess(new StringTextComponent("Moonclaws: " + t.passed + " / " + t.passed + " OK")
                    .withStyle(TextFormatting.GREEN), false);
        } else {
            source.sendFailure(new StringTextComponent("Moonclaws: " + t.failed.size() + " FAIL, " + t.passed + " OK"));
            for (String f : t.failed) {
                source.sendFailure(new StringTextComponent(" - " + f));
            }
        }
        return t.failed.isEmpty() ? 1 : 0;
    }

    private static Optional<? extends IRecipe<?>> recipe(MinecraftServer server, String path) {
        return server.getRecipeManager().byKey(new ResourceLocation(MoonclawsMod.MODID, path));
    }

    private static boolean hasStray(MutableRegistry<Biome> biomes, RegistryKey<Biome> key) {
        Biome biome = biomes.get(key);
        if (biome == null) {
            return false;
        }
        for (MobSpawnInfo.Spawners s : biome.getMobSettings().getMobs(EntityClassification.CREATURE)) {
            if (s.type == ModEntities.STRAY_CAT.get()) {
                return true;
            }
        }
        return false;
    }
}
