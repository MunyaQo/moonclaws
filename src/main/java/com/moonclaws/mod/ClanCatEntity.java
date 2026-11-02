package com.moonclaws.mod;

import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.entity.AgeableEntity;
import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomWalkingGoal;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

/**
 * A cat living in a clan settlement. Has a rank, a sex and a name "as in the books", its own nest,
 * sleeps on it at night, hides from humans, defends the camp, trades with cat-players.
 */
public class ClanCatEntity extends CatEntity {

    /** How far from the camp center cats wander. */
    public static final int HOME_RADIUS = 11;
    /** How far from the camp center cats chase an enemy. */
    public static final int FIGHT_RADIUS = 24;

    private int settlementId;
    private int rank = CatState.RANK_WARRIOR;
    private int gender = CatState.GENDER_TOM;
    private String kitName = "";
    private String stem = "";
    private String ending = "";
    @Nullable
    private BlockPos nest;
    @Nullable
    private BlockPos home;
    private int lastHurtByPlayerTick = -10000;
    private final CatMerchant merchant = new CatMerchant(this);
    /** Came from a Twoleg house; the others sometimes tease it. */
    private boolean formerKittypet;
    /** Still has the Twoleg name: the leader names it at the next sunset. */
    private boolean awaitingName;

    public ClanCatEntity(EntityType<? extends CatEntity> type, World world) {
        super(type, world);
        this.setPersistenceRequired();
        // added here (not in registerGoals) because the merchant exists only after the constructor of CatEntity
        this.goalSelector.addGoal(0, new TradeStillGoal(this, merchant));
        this.goalSelector.addGoal(1, new NightGuardGoal(this));
    }

    // ------------------------------------------------------------------ night guard

    private long guardCheckedDay = -1;
    private long lastGuardDay = -100;

    private long day() {
        return level.getDayTime() / 24000L;
    }

    private long timeOfDay() {
        return level.getDayTime() % 24000L;
    }

    static final int GUARD_NONE = 0;
    static final int GUARD_INNER = 1;
    static final int GUARD_OUTER = 2;

    private int guardRole;

    /**
     * Tonight's guards, in turn by day: a warrior walks outside the hedge, an apprentice inside it.
     * No apprentice: a second warrior walks inside. Only one warrior: the deputy joins him.
     */
    int guardRole() {
        if (rank != CatState.RANK_WARRIOR && rank != CatState.RANK_APPRENTICE && rank != CatState.RANK_DEPUTY) {
            return GUARD_NONE;
        }
        long d = day();
        if (guardCheckedDay != d) {
            guardCheckedDay = d;
            List<ClanCatEntity> all = level.getEntitiesOfClass(ClanCatEntity.class, getBoundingBox().inflate(40.0D),
                    c -> c.settlementId == settlementId && c.isAlive());
            List<ClanCatEntity> warriors = new java.util.ArrayList<>();
            List<ClanCatEntity> apprentices = new java.util.ArrayList<>();
            List<ClanCatEntity> deputies = new java.util.ArrayList<>();
            for (ClanCatEntity c : all) {
                if (c.rank == CatState.RANK_WARRIOR) {
                    warriors.add(c);
                } else if (c.rank == CatState.RANK_APPRENTICE) {
                    apprentices.add(c);
                } else if (c.rank == CatState.RANK_DEPUTY) {
                    deputies.add(c);
                }
            }
            java.util.Comparator<ClanCatEntity> byId = (a, b) -> a.getUUID().compareTo(b.getUUID());
            warriors.sort(byId);
            apprentices.sort(byId);
            deputies.sort(byId);
            ClanCatEntity outer = null;
            ClanCatEntity inner = null;
            int w = warriors.size();
            if (w > 0) {
                outer = warriors.get((int) (d % w));
            } else if (!deputies.isEmpty()) {
                outer = deputies.get(0);
            }
            if (!apprentices.isEmpty()) {
                inner = apprentices.get((int) (d % apprentices.size()));
            } else if (w >= 2) {
                inner = warriors.get((int) ((d + 1) % w));
            } else if (w == 1 && !deputies.isEmpty()) {
                inner = deputies.get(0);
            }
            guardRole = this == outer ? GUARD_OUTER : this == inner ? GUARD_INNER : GUARD_NONE;
        }
        return guardRole;
    }

    boolean isGuardTonight() {
        return guardRole() != GUARD_NONE;
    }

    /** After a night on guard the cat sleeps till noon. */
    boolean sleepsAfterGuard() {
        return lastGuardDay == day() - 1 && timeOfDay() < 6000L;
    }

    @Nullable
    BlockPos getHome() {
        return home;
    }

    /** Wake the fighters of the settlement and send them at a mob. */
    void raiseAlarm(LivingEntity mob) {
        playSound(SoundEvents.CAT_HISS, 1.0F, 1.2F);
        List<ClanCatEntity> fighters = level.getEntitiesOfClass(ClanCatEntity.class, getBoundingBox().inflate(32.0D),
                c -> c.settlementId == settlementId && c.isFighter() && c.isAlive());
        for (ClanCatEntity c : fighters) {
            c.setLying(false);
            c.setTarget(mob);
            c.lastHurtByPlayerTick = c.tickCount; // reuse the "angry for 30 s" timer
        }
        if (isFighter()) {
            setTarget(mob);
            lastHurtByPlayerTick = tickCount;
        }
    }

    // ------------------------------------------------------------------ setup

    public void setup(int settlementId, int rank, int gender, String[] name, BlockPos nest, BlockPos home, int skin) {
        this.settlementId = settlementId;
        this.rank = rank;
        this.gender = gender;
        this.kitName = name[0];
        this.stem = name[1];
        this.ending = name[2];
        this.nest = nest == null ? null : nest.immutable();
        this.home = home.immutable();
        this.setCatType(skin);
        this.restrictTo(this.home, HOME_RADIUS);
        this.merchant.setOffers(CatTrades.forRank(rank));
        refreshName();
    }

    @Nullable
    public BlockPos getNest() {
        return nest;
    }

    public boolean isFormerKittypet() {
        return formerKittypet;
    }

    /** A kittypet that just came to the camp: keeps its Twoleg name till the sunset ceremony. */
    public void becomeFormerKittypet() {
        formerKittypet = true;
        awaitingName = true;
        refreshName();
    }

    public String getCatName() {
        if (awaitingName) {
            return kitName;
        }
        return CatNames.compose(rank, CatNames.LANG_RU, kitName, stem, ending);
    }

    public int getRank() {
        return rank;
    }

    public int getGender() {
        return gender;
    }

    public int getSettlementId() {
        return settlementId;
    }

    public void setRank(int rank) {
        this.rank = rank;
        this.merchant.setOffers(CatTrades.forRank(rank));
        refreshName();
    }

    private void refreshName() {
        this.setCustomName(new StringTextComponent(getCatName()));
    }

    /** "Целитель Совохвост" */
    public ITextComponent getTitleName() {
        return new TranslationTextComponent("gui.moonclaws.rank." + rank + "." + gender)
                .append(" ").append(getCatName());
    }

    public boolean isFighter() {
        return rank == CatState.RANK_WARRIOR || rank == CatState.RANK_DEPUTY || rank == CatState.RANK_LEADER;
    }

    // ------------------------------------------------------------------ AI

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.goalSelector.addGoal(1, new SleepOnNestGoal(this));
        this.goalSelector.addGoal(2, new HideFromHumansGoal(this));
        this.goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.3F) {
            @Override
            public boolean canUse() {
                return isFighter() && super.canUse();
            }
        });
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.3D, true) {
            @Override
            public boolean canUse() {
                return isFighter() && super.canUse();
            }
        });
        this.goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomWalkingGoal(this, 0.8D, 0.0F));
        this.goalSelector.addGoal(7, new LookAtGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.addGoal(8, new LookRandomlyGoal(this));
    }

    /** Vanilla wild cats get "avoid players" here; clan cats use their own hiding goal instead. */
    @Override
    protected void reassessTameGoals() {
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level.isClientSide) {
            merchant.tick();
            LivingEntity target = getTarget();
            if (target != null) {
                boolean tooLong = this.tickCount - lastHurtByPlayerTick > 600;
                boolean tooFar = home != null && target.position().distanceToSqr(Vector3d.atCenterOf(home)) > 28.0D * 28.0D;
                if (tooLong || tooFar || !target.isAlive()) {
                    setTarget(null);
                }
            }
            if (home != null) {
                // in a fight the cats may run out of the camp (up to 24 blocks), otherwise they stay inside
                int radius = getTarget() != null ? FIGHT_RADIUS : HOME_RADIUS;
                if (!hasRestriction() || getRestrictRadius() != radius) {
                    restrictTo(home, radius);
                }
            }
            if (tickCount % 20 == 0) {
                kittypetLife();
            }
        }
    }

    /** Sunset naming ceremony for a newcomer from a Twoleg house, and a bit of teasing later. */
    private void kittypetLife() {
        if (!formerKittypet || !(level instanceof ServerWorld)) {
            return;
        }
        if (awaitingName) {
            long t = timeOfDay();
            if (t >= 12000L && t < 13000L) {
                awaitingName = false;
                String oldName = kitName;
                refreshName();
                SettlementData.Settlement s = SettlementData.get((ServerWorld) level).getSettlement(settlementId);
                String leader = s != null ? s.leaderName : "";
                for (PlayerEntity p : level.getEntitiesOfClass(PlayerEntity.class, getBoundingBox().inflate(32.0D))) {
                    p.displayClientMessage(new TranslationTextComponent(gender == CatState.GENDER_SHE
                            ? "message.moonclaws.kittypet_ceremony_she" : "message.moonclaws.kittypet_ceremony",
                            leader, oldName, getCatName()), false);
                }
                playSound(SoundEvents.CAT_PURREOW, 1.0F, 1.0F);
            }
            return;
        }
        if (tickCount % 1200 != 0 || random.nextInt(3) != 0 || isLying()) {
            return;
        }
        List<PlayerEntity> players = level.getEntitiesOfClass(PlayerEntity.class, getBoundingBox().inflate(12.0D),
                CatState::isCat);
        if (players.isEmpty()) {
            return;
        }
        List<ClanCatEntity> others = level.getEntitiesOfClass(ClanCatEntity.class, getBoundingBox().inflate(8.0D),
                c -> c != this && c.settlementId == settlementId && !c.formerKittypet && !c.isLying()
                        && c.rank != CatState.RANK_KIT);
        if (others.isEmpty()) {
            return;
        }
        ClanCatEntity teaser = others.get(random.nextInt(others.size()));
        TranslationTextComponent line = new TranslationTextComponent("message.moonclaws.tease_" + random.nextInt(4),
                teaser.getCatName(), getCatName());
        for (PlayerEntity p : players) {
            p.displayClientMessage(line, true);
        }
    }

    /** Someone hit a clan cat: the fighters of this settlement nearby take revenge. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && !level.isClientSide && source.getEntity() instanceof LivingEntity
                && !(source.getEntity() instanceof ClanCatEntity
                     && ((ClanCatEntity) source.getEntity()).settlementId == this.settlementId)) {
            LivingEntity attacker = (LivingEntity) source.getEntity();
            if (!(attacker instanceof PlayerEntity)
                    || !((PlayerEntity) attacker).isCreative() && !((PlayerEntity) attacker).isSpectator()) {
                alarm(level, settlementId, attacker, this);
                setLying(false);
                lastHurtByPlayerTick = tickCount;
            }
        }
        return result;
    }

    /**
     * The whole camp hears it: all fighters of the settlement wake up and go for the attacker
     * (they may leave the camp up to FIGHT_RADIUS blocks).
     */
    public static int alarm(World level, int settlementId, LivingEntity attacker, net.minecraft.entity.Entity near) {
        List<ClanCatEntity> cats = level.getEntitiesOfClass(ClanCatEntity.class, near.getBoundingBox().inflate(40.0D),
                c -> c.settlementId == settlementId && c.isFighter() && c.isAlive());
        for (ClanCatEntity c : cats) {
            if (c.home != null) {
                c.restrictTo(c.home, FIGHT_RADIUS);
            }
            c.setTarget(attacker);
            c.lastHurtByPlayerTick = c.tickCount;
            c.setLying(false);
        }
        if (!cats.isEmpty()) {
            org.apache.logging.log4j.LogManager.getLogger("Moonclaws").info(
                    "[Moonclaws] alarm in settlement {}: {} fighters go for {}", settlementId, cats.size(),
                    attacker.getType().getRegistryName());
        }
        return cats.size();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level.isClientSide && rank == CatState.RANK_LEADER && level instanceof ServerWorld) {
            // the deputy becomes the new leader
            List<ClanCatEntity> deputies = level.getEntitiesOfClass(ClanCatEntity.class, getBoundingBox().inflate(48.0D),
                    c -> c != this && c.settlementId == this.settlementId && c.rank == CatState.RANK_DEPUTY && c.isAlive());
            SettlementData.Settlement s = SettlementData.get((ServerWorld) level).getSettlement(settlementId);
            if (!deputies.isEmpty()) {
                ClanCatEntity next = deputies.get(0);
                next.setRank(CatState.RANK_LEADER);
                if (s != null) {
                    s.leaderName = next.getCatName();
                    SettlementData.get((ServerWorld) level).setDirty();
                }
            }
        }
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public ActionResultType mobInteract(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResultType.PASS;
        }
        if (level.isClientSide) {
            return ActionResultType.SUCCESS;
        }
        if (!(player instanceof ServerPlayerEntity)) {
            return ActionResultType.PASS;
        }
        ServerPlayerEntity sp = (ServerPlayerEntity) player;
        if (!CatState.isCat(sp)) {
            playSound(SoundEvents.CAT_HISS, 1.0F, 1.0F);
            sp.displayClientMessage(new TranslationTextComponent("message.moonclaws.clan_hiss"), true);
            return ActionResultType.CONSUME;
        }
        if (isLying()) {
            sp.displayClientMessage(new TranslationTextComponent("message.moonclaws.clan_sleeping", getCatName()), true);
            return ActionResultType.CONSUME;
        }
        if (!merchant.hasOffers()) {
            sp.displayClientMessage(new TranslationTextComponent("message.moonclaws.clan_no_trade." + rank, getCatName()), true);
            return ActionResultType.CONSUME;
        }
        if (merchant.isTrading()) {
            return ActionResultType.CONSUME;
        }
        getNavigation().stop();
        merchant.open(sp, getTitleName());
        return ActionResultType.CONSUME;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canMate(AnimalEntity other) {
        return false;
    }

    @Nullable
    @Override
    public CatEntity getBreedOffspring(ServerWorld world, AgeableEntity other) {
        return null;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundNBT tag) {
        super.addAdditionalSaveData(tag);
        CompoundNBT c = new CompoundNBT();
        c.putInt("Settlement", settlementId);
        c.putInt("Rank", rank);
        c.putInt("Gender", gender);
        c.putString("Kit", kitName);
        c.putString("Stem", stem);
        c.putString("Ending", ending);
        if (nest != null) {
            c.putLong("Nest", nest.asLong());
        }
        if (home != null) {
            c.putLong("Home", home.asLong());
        }
        c.put("Trade", merchant.save());
        c.putBoolean("FormerKittypet", formerKittypet);
        c.putBoolean("AwaitingName", awaitingName);
        tag.put("Moonclaws", c);
    }

    @Override
    public void readAdditionalSaveData(CompoundNBT tag) {
        super.readAdditionalSaveData(tag);
        CompoundNBT c = tag.getCompound("Moonclaws");
        settlementId = c.getInt("Settlement");
        rank = c.contains("Rank") ? c.getInt("Rank") : CatState.RANK_WARRIOR;
        gender = c.contains("Gender") ? c.getInt("Gender") : CatState.GENDER_TOM;
        kitName = c.getString("Kit");
        stem = c.getString("Stem");
        ending = c.getString("Ending");
        nest = c.contains("Nest") ? BlockPos.of(c.getLong("Nest")) : null;
        home = c.contains("Home") ? BlockPos.of(c.getLong("Home")) : null;
        merchant.load(c.getCompound("Trade"));
        if (!merchant.hasOffers()) {
            merchant.setOffers(CatTrades.forRank(rank));
        }
        formerKittypet = c.getBoolean("FormerKittypet");
        awaitingName = c.getBoolean("AwaitingName");
        if (home != null) {
            restrictTo(home, HOME_RADIUS);
        }
        refreshName();
    }

    // ------------------------------------------------------------------ goals

    /** At night: go to the own nest and curl up on it. */
    static class SleepOnNestGoal extends Goal {
        private final ClanCatEntity cat;

        SleepOnNestGoal(ClanCatEntity cat) {
            this.cat = cat;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
        }

        private boolean nestOk() {
            return cat.nest != null && cat.level.getBlockState(cat.nest).getBlock() instanceof NestBlock;
        }

        @Override
        public boolean canUse() {
            if (cat.getTarget() != null || cat.merchant.isTrading() || !nestOk()) {
                return false;
            }
            boolean night = cat.level.isNight() && !cat.isGuardTonight();
            return night || cat.sleepsAfterGuard();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        private int startTick;

        @Override
        public void start() {
            startTick = cat.tickCount;
            cat.getNavigation().moveTo(cat.nest.getX() + 0.5D, cat.nest.getY(), cat.nest.getZ() + 0.5D, 1.0D);
        }

        private void lieDown() {
            cat.getNavigation().stop();
            cat.setPos(cat.nest.getX() + 0.5D, cat.nest.getY() + NestBlock.HOLLOW_Y - 0.12D, cat.nest.getZ() + 0.5D);
            cat.setLying(true);
        }

        @Override
        public void tick() {
            double dx = cat.getX() - (cat.nest.getX() + 0.5D);
            double dz = cat.getZ() - (cat.nest.getZ() + 0.5D);
            if (dx * dx + dz * dz < 1.0D) {
                lieDown();
            } else if (cat.tickCount - startTick > 1200) {
                // could not find the way in a minute (stuck at a den entrance): just go to the nest
                lieDown();
            } else if (cat.getNavigation().isDone()) {
                cat.getNavigation().moveTo(cat.nest.getX() + 0.5D, cat.nest.getY(), cat.nest.getZ() + 0.5D, 1.0D);
            }
        }

        @Override
        public void stop() {
            cat.setLying(false);
        }
    }

    /**
     * Night guard: the inner guard walks along the inside of the hedge, the outer one around the camp
     * about 5 blocks beyond it. Hostile mobs are neutral to cats, so the guard only stops and watches them;
     * the alarm is raised when someone actually hurts a clan cat (see hurt()).
     */
    static class NightGuardGoal extends Goal {
        private final ClanCatEntity cat;
        private int point;
        private int pointTicks;
        private int watchTicks;
        @Nullable
        private LivingEntity watched;

        NightGuardGoal(ClanCatEntity cat) {
            this.cat = cat;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return cat.level.isNight() && cat.getTarget() == null && !cat.merchant.isTrading()
                    && cat.getHome() != null && cat.isGuardTonight();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            cat.setLying(false);
            cat.lastGuardDay = cat.day();
            point = cat.getRandom().nextInt(8);
            watchTicks = 0;
            goNext();
        }

        private void goNext() {
            BlockPos home = cat.getHome();
            double r = cat.guardRole() == GUARD_OUTER ? 15.0D : 9.0D;
            pointTicks = 0;
            double a = point * Math.PI / 4.0D;
            int x = home.getX() + (int) Math.round(Math.cos(a) * r);
            int z = home.getZ() + (int) Math.round(Math.sin(a) * r);
            int y = cat.level.getHeight(net.minecraft.world.gen.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            boolean ok = cat.getNavigation().moveTo(x + 0.5D, y, z + 0.5D, 0.8D);
            net.minecraft.pathfinding.Path path = cat.getNavigation().getPath();
            if (!ok || path == null || !path.canReach()) {
                pointTicks = 240; // can't get there: try the next point soon
            }
        }

        @Override
        public void tick() {
            cat.lastGuardDay = cat.day();
            if (cat.tickCount % 10 == 0) {
                List<net.minecraft.entity.monster.MonsterEntity> mobs = cat.level.getEntitiesOfClass(
                        net.minecraft.entity.monster.MonsterEntity.class, cat.getBoundingBox().inflate(10.0D),
                        LivingEntity::isAlive);
                watched = mobs.isEmpty() ? null : mobs.get(0);
                if (watched == null) {
                    watchTicks = 0;
                }
            }
            // a mob nearby: stop and keep an eye on it for up to 10 s, but don't start a fight
            if (watched != null && watched.isAlive() && watchTicks < 200) {
                watchTicks++;
                cat.getNavigation().stop();
                cat.getLookControl().setLookAt(watched, 30.0F, 30.0F);
                if (watchTicks == 200) {
                    goNext();
                }
                return;
            }
            pointTicks++;
            // the next point when this one is reached, or can't be reached (trees, water, cliffs) within 15 s
            if (cat.getNavigation().isDone() || pointTicks > 300) {
                point = (point + 1) % 8;
                goNext();
            }
        }
    }

    /** Humans: hiss, run to the own den (or just away) and stay there while the human is close. */
    static class HideFromHumansGoal extends Goal {
        private final ClanCatEntity cat;
        @Nullable
        private PlayerEntity human;

        HideFromHumansGoal(ClanCatEntity cat) {
            this.cat = cat;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        private boolean isHuman(PlayerEntity p) {
            return p.isAlive() && !p.isSpectator() && !CatState.isCat(p);
        }

        @Override
        public boolean canUse() {
            if (cat.getTarget() != null) {
                return false;
            }
            human = cat.level.getNearestPlayer(cat, 8.0D);
            return human != null && isHuman(human);
        }

        @Override
        public boolean canContinueToUse() {
            return human != null && isHuman(human) && human.distanceToSqr(cat) < 14.0D * 14.0D && cat.getTarget() == null;
        }

        @Override
        public void start() {
            cat.setLying(false);
            cat.playSound(SoundEvents.CAT_HISS, 1.0F, 1.0F);
            run();
        }

        private void run() {
            if (cat.nest != null) {
                cat.getNavigation().moveTo(cat.nest.getX() + 0.5D, cat.nest.getY(), cat.nest.getZ() + 0.5D, 1.4D);
            } else if (human != null) {
                Vector3d away = RandomPositionGenerator.getPosAvoid((CreatureEntity) cat, 10, 4, human.position());
                if (away != null) {
                    cat.getNavigation().moveTo(away.x, away.y, away.z, 1.4D);
                }
            }
        }

        @Override
        public void tick() {
            if (cat.tickCount % 60 == 0 && human != null && human.distanceToSqr(cat) < 64.0D) {
                cat.playSound(SoundEvents.CAT_HISS, 1.0F, 1.0F);
            }
            if (cat.getNavigation().isDone() && cat.tickCount % 20 == 0) {
                run();
            }
        }

        @Override
        public void stop() {
            human = null;
            cat.getNavigation().stop();
        }
    }
}
