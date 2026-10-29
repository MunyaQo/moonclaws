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
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.Pose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.CampfireTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.MobSpawnInfo;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
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
            AxisAlignedBB standingBox = new AxisAlignedBB(
                    player.getX() - 0.3D, player.getY(), player.getZ() - 0.3D,
                    player.getX() + 0.3D, player.getY() + 0.7D, player.getZ() + 0.3D);
            if (player.level.noCollision(player, standingBox)) {
                player.setPose(Pose.STANDING);
            }
        }

        if (event.side == LogicalSide.SERVER && player instanceof ServerPlayerEntity
                && player.tickCount % 10 == 0) {
            CatState.applyAbilities((ServerPlayerEntity) player);
        }
    }

    /** Wolves become rare, cats take their place in the same biomes (a bit less common than wolves were). */
    @SubscribeEvent
    public void onBiomeLoad(BiomeLoadingEvent event) {
        List<MobSpawnInfo.Spawners> creatures = event.getSpawns().getSpawner(EntityClassification.CREATURE);
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

    /** /moonclaws version (everyone) and /moonclaws settlement (cheats): the demo cat settlement. */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("moonclaws")
                .then(Commands.literal("version").executes(ctx -> {
                    String version = ModList.get().getModContainerById(MoonclawsMod.MODID)
                            .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
                    ctx.getSource().sendSuccess(new StringTextComponent("Moonclaws " + version), false);
                    return 1;
                }))
                .then(Commands.literal("settlement").requires(source -> source.hasPermission(2)).executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                    net.minecraft.util.math.BlockPos ground = player.blockPosition().below();
                    SettlementBuilder.build(player.getLevel(), ground);
                    SettlementData.get(player.getLevel()).add(ground);
                    // move the player to the entrance path (south side)
                    player.teleportTo(ground.getX() + 0.5D, ground.getY() + 1.0D, ground.getZ() + 12.5D);
                    ctx.getSource().sendSuccess(new StringTextComponent("Moonclaws: settlement built. You are at the entrance (south side)."), true);
                    return 1;
                })));
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

    /** Right click on a stray cat with an empty hand opens the trade window. */
    @SubscribeEvent
    public void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getWorld().isClientSide || event.getHand() != Hand.MAIN_HAND || !event.getItemStack().isEmpty()) {
            return;
        }
        if (event.getTarget() instanceof StrayCatEntity && event.getPlayer() instanceof ServerPlayerEntity) {
            ServerPlayerEntity sp = (ServerPlayerEntity) event.getPlayer();
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new ModNetwork.OpenTradePacket());
            event.setCanceled(true);
            event.setCancellationResult(ActionResultType.SUCCESS);
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
        if (pose == Pose.STANDING) {
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
