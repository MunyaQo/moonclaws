package com.moonclaws.mod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.command.Commands;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.Pose;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.monster.ElderGuardianEntity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.CampfireTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.MobSpawnInfo;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.PacketDistributor;

/** Events that run on both sides / on the server. */
public class CommonEvents {

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            refresh((ServerPlayerEntity) event.getPlayer());
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            refresh((ServerPlayerEntity) event.getPlayer());
        }
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            refresh((ServerPlayerEntity) event.getPlayer());
        }
    }

    private static void refresh(ServerPlayerEntity sp) {
        CatState.applyAbilities(sp);
        sp.refreshDimensions();
        CatState.sync(sp);
    }

    /** Keep the cat data after death. */
    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        CompoundNBT old = event.getOriginal().getPersistentData();
        if (old.contains(PlayerEntity.PERSISTED_NBT_TAG)) {
            event.getPlayer().getPersistentData().put(PlayerEntity.PERSISTED_NBT_TAG,
                    old.getCompound(PlayerEntity.PERSISTED_NBT_TAG).copy());
        }
    }

    /** A player started to see another player: tell him about the other player's cat state. */
    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayerEntity && event.getPlayer() instanceof ServerPlayerEntity) {
            CatState.syncTo((ServerPlayerEntity) event.getTarget(), (ServerPlayerEntity) event.getPlayer());
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        PlayerEntity player = event.player;

        // Vanilla decides "stand or crawl" using the height of a human (1.8).
        // A cat is smaller: if it fits standing, keep the normal standing pose (no slowdown).
        // In half-block gaps it does not fit, so it keeps crawling (low pose).
        if (player.getPose() == Pose.SWIMMING && !player.isInWater() && !player.isSwimming()
                && CatState.isCat(player)) {
            double standHeight = CatState.getRank(player) == CatState.RANK_KIT ? 0.5D : 0.7D;
            AxisAlignedBB standingBox = new AxisAlignedBB(
                    player.getX() - 0.3D, player.getY(), player.getZ() - 0.3D,
                    player.getX() + 0.3D, player.getY() + standHeight, player.getZ() + 0.3D);
            if (player.level.noCollision(player, standingBox)) {
                player.setPose(Pose.STANDING);
            }
        }

        if (event.side == LogicalSide.SERVER && player instanceof ServerPlayerEntity
                && player.tickCount % 10 == 0) {
            CatState.applyAbilities((ServerPlayerEntity) player);
            if (CatState.isCat(player)) {
                calmNearbyMobs((ServerPlayerEntity) player);
            }
        }
    }

    /** Wolves become rare, cats take their place in the same biomes (a bit less common than wolves were). */
    @SubscribeEvent
    public void onBiomeLoad(BiomeLoadingEvent event) {
        List<MobSpawnInfo.Spawners> creatures = event.getSpawns().getSpawner(EntityClassification.CREATURE);

        // stray cats: single, a bit rarer than wolves were, on any land biome (deserts included)
        Biome.Category category = event.getCategory();
        if (category != Biome.Category.OCEAN && category != Biome.Category.RIVER
                && category != Biome.Category.MUSHROOM && category != Biome.Category.NETHER
                && category != Biome.Category.THEEND && category != Biome.Category.NONE) {
            creatures.add(new MobSpawnInfo.Spawners(ModEntities.STRAY_CAT.get(), 4, 1, 1));
        }
        List<MobSpawnInfo.Spawners> wolves = new ArrayList<>();
        for (MobSpawnInfo.Spawners entry : creatures) {
            if (entry.type == EntityType.WOLF) {
                wolves.add(entry);
            }
        }
        if (wolves.isEmpty()) {
            return;
        }
        creatures.removeAll(wolves);
        // wolves: rare small packs (before: weight 5-8, packs of 4)
        creatures.add(new MobSpawnInfo.Spawners(EntityType.WOLF, 1, 2, 4));
        // cats: weight a bit lower than the old wolves had, groups of 1-3
        int catWeight = event.getCategory() == Biome.Category.TAIGA ? 6 : 4;
        creatures.add(new MobSpawnInfo.Spawners(EntityType.CAT, catWeight, 1, 3));
    }

    /** Food gives weak short effects, but only in cat form. */
    @SubscribeEvent
    public void onFoodEaten(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntityLiving() instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) event.getEntityLiving();
        if (CatState.isCat(player)) {
            FoodEffects.apply(player, event.getItem());
        }
    }

    /** /moonclaws version (everyone), /moonclaws test and /moonclaws settlement (cheats). */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("moonclaws")
                .then(Commands.literal("version").executes(ctx -> {
                    String version = ModList.get().getModContainerById(MoonclawsMod.MODID)
                            .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
                    ctx.getSource().sendSuccess(new StringTextComponent("Moonclaws " + version), false);
                    return 1;
                }))
                .then(Commands.literal("test").requires(source -> source.hasPermission(2))
                        .executes(ctx -> SelfTest.run(ctx.getSource())))
                .then(Commands.literal("settlement").requires(source -> source.hasPermission(2)).executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                    net.minecraft.util.math.BlockPos ground = player.blockPosition().below();
                    java.util.Random rnd = new java.util.Random();
                    SettlementBuilder.clearTrees(player.getLevel(), ground.getX(), ground.getZ(), ground.getY(), 17);
                    SettlementBuilder.Layout layout = SettlementBuilder.build(player.getLevel(), ground);
                    boolean swamp = ClanWorldgen.woodFor(player.getLevel(), player.getLevel().getBiome(ground)) == CouncilBuilder.WOOD_DARK_OAK;
                    if (swamp) {
                        SwampStyle.apply(player.getLevel(), layout);
                    }
                    SettlementData.Settlement s = SettlementSpawner.populate(player.getLevel(), layout,
                            rnd.nextInt(SettlementSpawner.CLAN_NAMES), rnd);
                    PatrolTrail.build(player.getLevel(), ground, swamp);
                    s.trail = true;
                    // move the player to the entrance path (south side)
                    player.teleportTo(ground.getX() + 0.5D, ground.getY() + 1.0D, ground.getZ() + 12.5D);
                    ctx.getSource().sendSuccess(new TranslationTextComponent("message.moonclaws.settlement_built",
                            new TranslationTextComponent("clan.moonclaws." + s.clan), s.leaderName), true);
                    return 1;
                }))
                .then(Commands.literal("council").requires(source -> source.hasPermission(2)).executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                    net.minecraft.util.math.BlockPos ground = player.blockPosition().below();
                    int wood = ClanWorldgen.woodFor(player.getLevel(), player.getLevel().getBiome(ground));
                    CouncilBuilder.build(player.getLevel(), ground, wood < 0 ? CouncilBuilder.WOOD_OAK : wood);
                    SettlementData.get(player.getLevel()).addCouncil(ground);
                    // demo: four platforms, as if four settlements were around
                    for (int i = 0; i < 4; i++) {
                        double a = Math.PI / 4 + i * Math.PI / 2 + 0.3D;
                        CouncilBuilder.addPlatform(player.getLevel(), ground,
                                ground.getX() + (int) (Math.cos(a) * 96), ground.getZ() + (int) (Math.sin(a) * 96));
                    }
                    player.teleportTo(ground.getX() + 0.5D, ground.getY() + 1.0D, ground.getZ() + 16.5D);
                    ctx.getSource().sendSuccess(new TranslationTextComponent("message.moonclaws.council_built"), true);
                    return 1;
                }))
                .then(Commands.literal("kittypet").requires(source -> source.hasPermission(2))
                        .executes(ctx -> spawnKittypet(ctx.getSource(), 0))
                        .then(Commands.literal("witch").executes(ctx -> spawnKittypet(ctx.getSource(), 1)))
                        .then(Commands.literal("wild").executes(ctx -> spawnKittypet(ctx.getSource(), 2))))
                .then(Commands.literal("nearest").requires(source -> source.hasPermission(2)).executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                    java.util.List<SettlementData.Group> groups = ClanWorldgen.nearestGroups(player.getLevel(), player.blockPosition());
                    if (groups.isEmpty()) {
                        ctx.getSource().sendFailure(new TranslationTextComponent("message.moonclaws.nearest_none"));
                        return 0;
                    }
                    SettlementData.Group g = groups.get(0);
                    ctx.getSource().sendSuccess(new TranslationTextComponent("message.moonclaws.nearest_council",
                            g.council.getX(), g.council.getZ(),
                            (int) Math.sqrt(g.council.distSqr(player.blockPosition()))), false);
                    for (int i = 0; i < g.sites.size(); i++) {
                        net.minecraft.util.math.BlockPos site = g.sites.get(i);
                        ctx.getSource().sendSuccess(new TranslationTextComponent("message.moonclaws.nearest_site",
                                new TranslationTextComponent("clan.moonclaws." + g.clans.get(i)), site.getX(), site.getZ()), false);
                    }
                    return 1;
                })));
    }

    /** Test: a kittypet next to the player (0 normal, 1 witch's cat, 2 ready to leave: not neutered, pull 75). */
    private static int spawnKittypet(net.minecraft.command.CommandSource source, int kind)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrException();
        KittypetEntity pet = ModEntities.KITTYPET.get().create(player.getLevel());
        if (pet == null) {
            return 0;
        }
        java.util.Random rnd = new java.util.Random();
        pet.moveTo(player.getX() + 1.5D, player.getY(), player.getZ(), player.yRot + 180.0F, 0.0F);
        pet.setupNew(rnd, kind == 1, rnd.nextInt(10));
        if (kind == 2) {
            pet.makeWild();
        }
        player.getLevel().addFreshEntity(pet);
        source.sendSuccess(new TranslationTextComponent("message.moonclaws.kittypet_spawned", pet.getPetName(),
                pet.isNeutered() ? "да" : "нет", pet.getPull()), false);
        return 1;
    }

    /** No natural spawns of hostile mobs around settlements (48 blocks). */
    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (!(event.getWorld() instanceof ServerWorld)) {
            return;
        }
        if (event.getEntityLiving().getType().getCategory() != EntityClassification.MONSTER) {
            return;
        }
        SpawnReason reason = event.getSpawnReason();
        if (reason != SpawnReason.NATURAL && reason != SpawnReason.CHUNK_GENERATION) {
            return;
        }
        if (SettlementData.get((ServerWorld) event.getWorld()).isNear(event.getX(), event.getZ(), 48.0D)) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** Unlit campfire = food storage: right click with an empty hand takes the last item back. */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().isClientSide || event.getHand() != Hand.MAIN_HAND || !event.getItemStack().isEmpty()) {
            return;
        }
        BlockState state = event.getWorld().getBlockState(event.getPos());
        if (!(state.getBlock() instanceof CampfireBlock) || state.getValue(CampfireBlock.LIT)) {
            return;
        }
        TileEntity tile = event.getWorld().getBlockEntity(event.getPos());
        if (!(tile instanceof CampfireTileEntity)) {
            return;
        }
        CampfireTileEntity fire = (CampfireTileEntity) tile;
        NonNullList<ItemStack> items = fire.getItems();
        for (int i = items.size() - 1; i >= 0; i--) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                ItemStack out = stack.copy();
                items.set(i, ItemStack.EMPTY);
                fire.setChanged();
                event.getWorld().sendBlockUpdated(event.getPos(), state, state, 3);
                if (!event.getPlayer().inventory.add(out)) {
                    event.getPlayer().drop(out, false);
                }
                event.setCanceled(true);
                event.setCancellationResult(ActionResultType.SUCCESS);
                return;
            }
        }
    }

    // ------------------------------------------------------------ neutrality of hostile mobs to cats

    private static final String K_PROVOKER = "moonclaws_provoker";
    private static final String K_PROVOKED_AT = "moonclaws_provoked_at";
    /** A provoked mob stays angry for 30 seconds after the last hit. */
    private static final long ANGRY_TICKS = 600L;
    private static final long KIT_ANGRY_TICKS = 200L;

    private static boolean isBoss(MobEntity mob) {
        return mob instanceof WitherEntity || mob instanceof EnderDragonEntity || mob instanceof ElderGuardianEntity;
    }

    private static boolean isProvokedBy(MobEntity mob, PlayerEntity player) {
        CompoundNBT d = mob.getPersistentData();
        if (!d.hasUUID(K_PROVOKER) || !d.getUUID(K_PROVOKER).equals(player.getUUID())) {
            return false;
        }
        // kits are forgiven sooner: 10 seconds instead of 30
        long angry = CatState.getRank(player) == CatState.RANK_KIT ? KIT_ANGRY_TICKS : ANGRY_TICKS;
        return mob.level.getGameTime() - d.getLong(K_PROVOKED_AT) <= angry;
    }

    /** Should this mob leave this player alone? */
    private static boolean leavesAlone(MobEntity mob, PlayerEntity player) {
        return mob instanceof IMob && !isBoss(mob) && CatState.isCat(player) && !isProvokedBy(mob, player);
    }

    /** Hostile mobs don't pick a cat-player as a target (unless the cat hit them first). */
    @SubscribeEvent
    public void onSetTarget(LivingSetAttackTargetEvent event) {
        if (event.getEntityLiving().level.isClientSide) {
            return;
        }
        if (!(event.getTarget() instanceof PlayerEntity) || !(event.getEntityLiving() instanceof MobEntity)) {
            return;
        }
        MobEntity mob = (MobEntity) event.getEntityLiving();
        if (leavesAlone(mob, (PlayerEntity) event.getTarget())) {
            mob.setTarget(null);
        }
    }

    /** Remember who hit a mob: that player is fair game for 30 seconds. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (event.getEntityLiving().level.isClientSide || !(event.getEntityLiving() instanceof MobEntity)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof PlayerEntity) {
            CompoundNBT d = event.getEntityLiving().getPersistentData();
            d.putUUID(K_PROVOKER, attacker.getUUID());
            d.putLong(K_PROVOKED_AT, event.getEntityLiving().level.getGameTime());
        }
    }

    /** Mobs that were already chasing the player calm down (on transformation, or 30 s after the last hit). */
    private static void calmNearbyMobs(ServerPlayerEntity player) {
        for (MobEntity mob : player.level.getEntitiesOfClass(MobEntity.class, player.getBoundingBox().inflate(24.0D))) {
            if (mob.getTarget() == player && leavesAlone(mob, player)) {
                mob.setTarget(null);
            }
        }
    }

    /** Cats land on their paws. */
    @SubscribeEvent
    public void onFall(LivingFallEvent event) {
        if (event.getEntityLiving() instanceof PlayerEntity && CatState.isCat((PlayerEntity) event.getEntityLiving())) {
            event.setDamageMultiplier(0.0F);
        }
    }

    /** Small cat body: fits into 1-block gaps. */
    @SubscribeEvent
    public void onSize(EntityEvent.Size event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof PlayerEntity)) {
            return;
        }
        if (!CatState.isCat((PlayerEntity) entity)) {
            return;
        }
        Pose pose = event.getPose();
        if (pose == Pose.STANDING && CatState.getRank((PlayerEntity) entity) == CatState.RANK_KIT) {
            // a kit is tiny: always half a block tall
            event.setNewSize(EntitySize.scalable(0.5F, 0.5F));
            event.setNewEyeHeight(0.4F);
        } else if (pose == Pose.STANDING) {
            event.setNewSize(EntitySize.scalable(0.6F, 0.7F));
            event.setNewEyeHeight(0.55F);
        } else if (pose == Pose.CROUCHING) {
            // sneaking cat: low enough for half-block gaps
            event.setNewSize(EntitySize.scalable(0.6F, 0.5F));
            event.setNewEyeHeight(0.4F);
        } else if (pose == Pose.SWIMMING) {
            // low crawling pose: fits under half-block gaps
            event.setNewSize(EntitySize.scalable(0.6F, 0.5F));
            event.setNewEyeHeight(0.4F);
        }
    }
}
