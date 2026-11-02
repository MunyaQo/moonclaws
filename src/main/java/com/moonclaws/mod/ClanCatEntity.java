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

    public ClanCatEntity(EntityType<? extends CatEntity> type, World world) {
        super(type, world);
        this.setPersistenceRequired();
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

    public String getCatName() {
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
                boolean tooFar = home != null && target.position().distanceToSqr(Vector3d.atCenterOf(home)) > 24.0D * 24.0D;
                if (tooLong || tooFar || !target.isAlive()) {
                    setTarget(null);
                }
            }
            if (home != null && !hasRestriction()) {
                restrictTo(home, HOME_RADIUS);
            }
        }
    }

    /** Someone hit a clan cat: the fighters of this settlement nearby take revenge. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && !level.isClientSide && source.getEntity() instanceof PlayerEntity) {
            PlayerEntity attacker = (PlayerEntity) source.getEntity();
            if (!attacker.isCreative() && !attacker.isSpectator()) {
                List<ClanCatEntity> cats = level.getEntitiesOfClass(ClanCatEntity.class, getBoundingBox().inflate(24.0D),
                        c -> c.settlementId == this.settlementId && c.isFighter());
                for (ClanCatEntity c : cats) {
                    c.setTarget(attacker);
                    c.lastHurtByPlayerTick = c.tickCount;
                    c.setLying(false);
                }
                setLying(false);
                lastHurtByPlayerTick = tickCount;
            }
        }
        return result;
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
            return cat.level.isNight() && cat.getTarget() == null && nestOk();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            cat.getNavigation().moveTo(cat.nest.getX() + 0.5D, cat.nest.getY(), cat.nest.getZ() + 0.5D, 1.0D);
        }

        @Override
        public void tick() {
            double dx = cat.getX() - (cat.nest.getX() + 0.5D);
            double dz = cat.getZ() - (cat.nest.getZ() + 0.5D);
            if (dx * dx + dz * dz < 1.0D) {
                cat.getNavigation().stop();
                cat.setPos(cat.nest.getX() + 0.5D, cat.nest.getY() + NestBlock.HOLLOW_Y - 0.12D, cat.nest.getZ() + 0.5D);
                cat.setLying(true);
            } else if (cat.getNavigation().isDone()) {
                cat.getNavigation().moveTo(cat.nest.getX() + 0.5D, cat.nest.getY(), cat.nest.getZ() + 0.5D, 1.0D);
            }
        }

        @Override
        public void stop() {
            cat.setLying(false);
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
