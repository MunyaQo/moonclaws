package com.moonclaws.mod;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.entity.AgeableEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomWalkingGoal;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

/**
 * A kittypet: a house cat of the Twolegs, living in a village (or the witch's cat in a swamp hut).
 * Always wears a collar, has a Twoleg name, trades house things for "wild" things with cat-players.
 * Hidden: neutered (70%) and the pull of the forest (0-100). A not neutered kittypet with a strong pull
 * agrees to leave; then it slips out at night and follows the player to a clan camp before dawn.
 */
public class KittypetEntity extends CatEntity {

    public static final int JOIN_PULL = 80;
    public static final int ALL_BLACK = 10;

    private static final String[] TOM_NAMES = {
            "Барсик", "Мурзик", "Пушок", "Персик", "Тимоша", "Васька", "Кузя", "Тиша", "Боня", "Маркиз", "Котофей", "Филя"
    };
    private static final String[] SHE_NAMES = {
            "Мурка", "Муся", "Дуся", "Белла", "Соня", "Маруся", "Зефирка", "Ириска", "Тучка", "Пуша", "Лапушка", "Василиса"
    };
    private static final String[] WITCH_NAMES = {"Сажа", "Ворожея", "Мгла", "Полынь"};

    private final CatMerchant merchant = new CatMerchant(this);
    private String petName = "";
    private int gender = CatState.GENDER_TOM;
    private boolean neutered = true;
    private int pull;
    private boolean witch;
    private long lastGiftDay = -100;
    private long lastHealDay = -100;
    @Nullable
    private UUID agreedWith;
    private boolean following;
    @Nullable
    private BlockPos homePos;

    public KittypetEntity(EntityType<? extends CatEntity> type, World world) {
        super(type, world);
        this.setPersistenceRequired();
        this.goalSelector.addGoal(0, new TradeStillGoal(this, merchant));
        this.goalSelector.addGoal(1, new FollowEscapeGoal(this));
        // livelier than a vanilla house cat: strolls around the village and watches the villagers
        WaterAvoidingRandomWalkingGoal stroll = new WaterAvoidingRandomWalkingGoal(this, 0.8D);
        stroll.setInterval(40);
        this.goalSelector.addGoal(6, stroll);
        this.goalSelector.addGoal(9, new LookAtGoal(this, VillagerEntity.class, 8.0F));
    }

    /** Wander radius around the house. */
    private static final int HOME_RADIUS = 24;
    private boolean sitRemoved;

    /** A freshly made kittypet: name, collar, hidden traits, trades. */
    public void setupNew(Random rnd, boolean witchCat, int skin) {
        this.witch = witchCat;
        this.gender = witchCat ? CatState.GENDER_SHE : (rnd.nextBoolean() ? CatState.GENDER_TOM : CatState.GENDER_SHE);
        String[] names = witchCat ? WITCH_NAMES : gender == CatState.GENDER_SHE ? SHE_NAMES : TOM_NAMES;
        this.petName = names[rnd.nextInt(names.length)];
        this.neutered = rnd.nextInt(10) < 7;
        this.pull = rnd.nextInt(41);
        this.setCatType(witchCat ? ALL_BLACK : skin);
        this.setTame(true); // the collar is drawn only for tame cats; there is no owner yet
        this.setCollarColor(witchCat ? DyeColor.PURPLE : DyeColor.byId(rnd.nextInt(16)));
        this.merchant.setOffers(witchCat ? CatTrades.witchCat(rnd) : CatTrades.kittypet(rnd));
        this.setCustomName(new StringTextComponent(petName));
    }

    /** For the test command: a cat that is ready to go. */
    public void makeWild() {
        this.neutered = false;
        this.pull = JOIN_PULL - 5;
    }

    public String getPetName() {
        return petName;
    }

    public boolean isWitchCat() {
        return witch;
    }

    public boolean isNeutered() {
        return neutered;
    }

    public int getPull() {
        return pull;
    }

    /** Lang key with the she-cat variant when needed. */
    private String g(String key) {
        return gender == CatState.GENDER_SHE ? key + "_she" : key;
    }

    int getGender() {
        return gender;
    }

    private long day() {
        return level.getDayTime() / 24000L;
    }

    private void addPull(int amount) {
        pull = Math.min(100, pull + amount);
    }

    /** Called by the merchant after every trade. */
    void onTrade() {
        addPull(5);
    }

    // ------------------------------------------------------------------ interaction

    private static boolean isRawPrey(Item item) {
        return item == Items.RABBIT || item == Items.CHICKEN || item == Items.COD || item == Items.SALMON;
    }

    @Override
    public ActionResultType mobInteract(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResultType.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        boolean cat = CatState.isCat(player);
        if (!cat) {
            // humans: a kittypet without an owner can be tamed with fish, like a normal cat
            if (getOwnerUUID() == null && isFood(held)) {
                if (!level.isClientSide) {
                    if (!player.abilities.instabuild) {
                        held.shrink(1);
                    }
                    if (random.nextInt(3) == 0) {
                        tame(player);
                        if (sitRemoved) {
                            goalSelector.addGoal(1, new SitGoal(this));
                            sitRemoved = false;
                        }
                        restrictTo(BlockPos.ZERO, -1);
                        setOrderedToSit(true);
                        level.broadcastEntityEvent(this, (byte) 7);
                    } else {
                        level.broadcastEntityEvent(this, (byte) 6);
                    }
                }
                return ActionResultType.sidedSuccess(level.isClientSide);
            }
            return super.mobInteract(player, hand);
        }
        if (level.isClientSide) {
            return ActionResultType.SUCCESS;
        }
        if (!(player instanceof ServerPlayerEntity)) {
            return ActionResultType.PASS;
        }
        ServerPlayerEntity sp = (ServerPlayerEntity) player;

        // witch's cat: herbs for healing, once a day
        if (witch && Medicine.isHerb(held)) {
            if (sp.getHealth() >= sp.getMaxHealth() / 2.0F) {
                sp.displayClientMessage(new TranslationTextComponent("message.moonclaws.witch_not_hurt", petName), true);
            } else if (lastHealDay == day()) {
                sp.displayClientMessage(new TranslationTextComponent("message.moonclaws.witch_tired", petName), true);
            } else if (held.getCount() < 2 && !sp.abilities.instabuild) {
                sp.displayClientMessage(new TranslationTextComponent("message.moonclaws.witch_two_herbs", petName), true);
            } else {
                if (!sp.abilities.instabuild) {
                    held.shrink(2);
                }
                lastHealDay = day();
                sp.addEffect(new EffectInstance(Effects.REGENERATION, 12 * 20, 1));
                playSound(SoundEvents.CAT_PURR, 1.0F, 1.0F);
                sp.displayClientMessage(new TranslationTextComponent("message.moonclaws.witch_heals", petName), true);
            }
            return ActionResultType.CONSUME;
        }

        // a gift of raw prey: the forest calls louder
        if (isRawPrey(held.getItem())) {
            if (lastGiftDay == day()) {
                sp.displayClientMessage(new TranslationTextComponent(g("message.moonclaws.kittypet_full"), petName), true);
            } else {
                if (!sp.abilities.instabuild) {
                    held.shrink(1);
                }
                lastGiftDay = day();
                addPull(10);
                playSound(SoundEvents.CAT_EAT, 1.0F, 1.0F);
                sp.displayClientMessage(new TranslationTextComponent("message.moonclaws.kittypet_gift", petName), true);
            }
            return ActionResultType.CONSUME;
        }

        if (!held.isEmpty()) {
            return ActionResultType.PASS;
        }

        // Shift + empty paw: "will you come with us?"
        if (sp.isShiftKeyDown()) {
            ask(sp);
            return ActionResultType.CONSUME;
        }

        if (!merchant.isTrading()) {
            getNavigation().stop();
            merchant.open(sp, new TranslationTextComponent(witch ? "gui.moonclaws.witch_cat_title" : "gui.moonclaws.kittypet_title", petName));
        }
        return ActionResultType.CONSUME;
    }

    private void ask(ServerPlayerEntity sp) {
        if (agreedWith != null && agreedWith.equals(sp.getUUID())) {
            sp.displayClientMessage(new TranslationTextComponent(following
                    ? g("message.moonclaws.kittypet_going") : g("message.moonclaws.kittypet_wait_night"), petName), true);
            return;
        }
        String key;
        if (pull < 30) {
            key = "message.moonclaws.kittypet_pull_0";
        } else if (pull < 60) {
            key = "message.moonclaws.kittypet_pull_1";
        } else if (pull < JOIN_PULL) {
            key = "message.moonclaws.kittypet_pull_2";
        } else if (neutered) {
            key = g("message.moonclaws.kittypet_neutered");
        } else {
            agreedWith = sp.getUUID();
            following = false;
            key = "message.moonclaws.kittypet_agrees";
        }
        sp.displayClientMessage(new TranslationTextComponent(key, petName), false);
    }

    // ------------------------------------------------------------------ the night escape

    @Override
    public void aiStep() {
        super.aiStep();
        if (level.isClientSide) {
            return;
        }
        merchant.tick();
        if (homePos == null) {
            homePos = blockPosition();
        }
        if (getOwnerUUID() == null) {
            // a tame cat without an owner would sit forever (vanilla rule): drop that goal
            if (!sitRemoved) {
                List<Goal> sit = new ArrayList<>();
                goalSelector.getRunningGoals().map(PrioritizedGoal::getGoal)
                        .filter(gl -> gl instanceof SitGoal).forEach(sit::add);
                for (Goal gl : sit) {
                    goalSelector.removeGoal(gl);
                    sitRemoved = true;
                }
            }
            if (isOrderedToSit() || isInSittingPose()) {
                setOrderedToSit(false);
                setInSittingPose(false);
            }
            if (following) {
                if (hasRestriction()) {
                    restrictTo(BlockPos.ZERO, -1);
                }
            } else if (!hasRestriction() || getRestrictRadius() != HOME_RADIUS) {
                restrictTo(homePos, HOME_RADIUS);
            }
        }
        if (agreedWith == null || tickCount % 10 != 0 || !(level instanceof ServerWorld)) {
            return;
        }
        ServerWorld world = (ServerWorld) level;
        PlayerEntity player = world.getPlayerByUUID(agreedWith);
        boolean night = world.isNight();
        if (!following) {
            if (night && player != null && player.distanceToSqr(this) < 36.0D) {
                following = true;
                setOrderedToSit(false);
                player.displayClientMessage(new TranslationTextComponent(g("message.moonclaws.kittypet_slips_out"), petName), false);
            }
            return;
        }
        if (!night) {
            // dawn caught them on the way: back home, try another night
            following = false;
            if (homePos != null) {
                teleportTo(homePos.getX() + 0.5D, homePos.getY(), homePos.getZ() + 0.5D);
            }
            if (player != null) {
                player.displayClientMessage(new TranslationTextComponent(g("message.moonclaws.kittypet_dawn"), petName), false);
            }
            return;
        }
        SettlementData.Settlement s = SettlementData.get(world).settlementNear(getX(), getZ(), 12.0D);
        if (s != null) {
            join(world, s, player);
        }
    }

    /** Becomes an apprentice of the settlement (if there is a free nest). */
    private void join(ServerWorld world, SettlementData.Settlement s, @Nullable PlayerEntity player) {
        List<ClanCatEntity> cats = world.getEntitiesOfClass(ClanCatEntity.class,
                new AxisAlignedBB(s.center).inflate(40.0D), c -> c.getSettlementId() == s.id && c.isAlive());
        Set<BlockPos> used = new HashSet<>();
        for (ClanCatEntity c : cats) {
            if (c.getNest() != null) {
                used.add(c.getNest());
            }
        }
        BlockPos free = null;
        List<BlockPos> others = new ArrayList<>();
        for (BlockPos n : s.nests) {
            if (used.contains(n) || !(world.getBlockState(n).getBlock() instanceof NestBlock)) {
                continue;
            }
            SettlementData.Den den = s.denAt(n.getX() + 0.5D, n.getY() + 0.5D, n.getZ() + 0.5D);
            if (den != null && den.kind == SettlementData.DEN_APPRENTICES) {
                free = n;
                break;
            }
            others.add(n);
        }
        if (free == null && !others.isEmpty()) {
            free = others.get(0);
        }
        if (free == null) {
            following = false;
            if (player != null) {
                player.displayClientMessage(new TranslationTextComponent("message.moonclaws.kittypet_no_room",
                        s.leaderName, petName), false);
            }
            if (homePos != null) {
                teleportTo(homePos.getX() + 0.5D, homePos.getY(), homePos.getZ() + 0.5D);
            }
            return;
        }
        ClanCatEntity cat = ModEntities.CLAN_CAT.get().create(world);
        if (cat == null) {
            return;
        }
        cat.moveTo(getX(), getY(), getZ(), yRot, 0.0F);
        cat.setup(s.id, CatState.RANK_APPRENTICE, gender, new String[] {petName, stemFor(getCatType(), random), ""},
                free, s.center.below(3), getCatType());
        cat.becomeFormerKittypet();
        world.addFreshEntity(cat);
        if (player != null) {
            player.displayClientMessage(new TranslationTextComponent(g("message.moonclaws.kittypet_joined"), petName), false);
        }
        remove();
    }

    /** A clan name stem that fits the coat (red → "Огне", like in the books), sometimes a random one. */
    public static String stemFor(int skin, Random rnd) {
        String[][] bySkin = {
                {"Полосо", "Пёстро"},   // 0 tabby
                {"Черно", "Пёстро"},    // 1 tuxedo
                {"Огне", "Рыже"},       // 2 red
                {"Песко", "Тене"},      // 3 siamese
                {"Серо", "Дымо"},       // 4 british
                {"Пёстро", "Рябино"},   // 5 calico
                {"Рыже", "Солнце"},     // 6 persian
                {"Бело", "Пёрышко"},    // 7 ragdoll
                {"Снего", "Бело"},      // 8 white
                {"Тумано", "Серо"},     // 9 jellie
                {"Ночно", "Угле"}       // 10 black
        };
        if (skin < 0 || skin >= bySkin.length || rnd.nextInt(4) == 0) {
            return CatNames.random(CatNames.LANG_RU, rnd)[1];
        }
        return bySkin[skin][rnd.nextInt(2)];
    }

    /** Follows the player while escaping (waits if the player is too far). */
    static class FollowEscapeGoal extends Goal {
        private final KittypetEntity cat;
        @Nullable
        private PlayerEntity player;

        FollowEscapeGoal(KittypetEntity cat) {
            this.cat = cat;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!cat.following || cat.agreedWith == null) {
                return false;
            }
            player = cat.level.getPlayerByUUID(cat.agreedWith);
            return player != null && player.distanceToSqr(cat) < 24.0D * 24.0D;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            if (player == null) {
                return;
            }
            cat.getLookControl().setLookAt(player, 10.0F, cat.getMaxHeadXRot());
            if (cat.tickCount % 10 == 0) {
                if (cat.distanceToSqr(player) > 9.0D) {
                    cat.getNavigation().moveTo(player, 1.1D);
                } else {
                    cat.getNavigation().stop();
                }
            }
        }

        @Override
        public void stop() {
            cat.getNavigation().stop();
        }
    }

    // ------------------------------------------------------------------ misc

    @Override
    protected void reassessTameGoals() {
        // no "avoid players" goal: kittypets are used to people
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

    @Override
    public void addAdditionalSaveData(CompoundNBT tag) {
        super.addAdditionalSaveData(tag);
        CompoundNBT c = new CompoundNBT();
        c.putString("Name", petName);
        c.putInt("Gender", gender);
        c.putBoolean("Neutered", neutered);
        c.putInt("Pull", pull);
        c.putBoolean("Witch", witch);
        c.putLong("GiftDay", lastGiftDay);
        c.putLong("HealDay", lastHealDay);
        if (agreedWith != null) {
            c.putUUID("Agreed", agreedWith);
        }
        c.putBoolean("Following", following);
        if (homePos != null) {
            c.putLong("Home", homePos.asLong());
        }
        c.put("Trade", merchant.save());
        tag.put("Kittypet", c);
    }

    @Override
    public void readAdditionalSaveData(CompoundNBT tag) {
        super.readAdditionalSaveData(tag);
        CompoundNBT c = tag.getCompound("Kittypet");
        petName = c.getString("Name");
        gender = c.getInt("Gender");
        neutered = !c.contains("Neutered") || c.getBoolean("Neutered");
        pull = c.getInt("Pull");
        witch = c.getBoolean("Witch");
        lastGiftDay = c.contains("GiftDay") ? c.getLong("GiftDay") : -100;
        lastHealDay = c.contains("HealDay") ? c.getLong("HealDay") : -100;
        agreedWith = c.hasUUID("Agreed") ? c.getUUID("Agreed") : null;
        following = c.getBoolean("Following");
        homePos = c.contains("Home") ? BlockPos.of(c.getLong("Home")) : null;
        merchant.load(c.getCompound("Trade"));
        if (!merchant.hasOffers()) {
            merchant.setOffers(witch ? CatTrades.witchCat(random) : CatTrades.kittypet(random));
        }
        if (petName.isEmpty()) {
            petName = TOM_NAMES[random.nextInt(TOM_NAMES.length)];
        }
        setCustomName(new StringTextComponent(petName));
    }
}
