package com.moonclaws.mod;

import java.util.UUID;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraftforge.fml.network.PacketDistributor;

/** All logic about the cat form of a player: stored data, abilities, syncing. */
public final class CatState {

    public static final int SKINS = 11;

    public static final int CLAN_NONE = 0;
    public static final int CLAN_THUNDER = 1;
    public static final int CLAN_RIVER = 2;
    public static final int CLAN_WIND = 3;
    public static final int CLAN_SHADOW = 4;

    public static final int GENDER_NONE = 0;
    public static final int GENDER_TOM = 1;
    public static final int GENDER_SHE = 2;

    public static final int RANK_NONE = 0;
    public static final int RANK_KIT = 1;
    public static final int RANK_APPRENTICE = 2;
    public static final int RANK_WARRIOR = 3;
    public static final int RANK_HEALER = 4;
    public static final int RANK_DEPUTY = 5;
    public static final int RANK_LEADER = 6;
    public static final int RANK_ELDER = 7;

    public static final int LEADER_LIVES = 9;

    private static final String K_CAT = "moonclaws_cat";
    private static final String K_CLAN = "moonclaws_clan";
    private static final String K_SKIN = "moonclaws_skin";
    private static final String K_GENDER = "moonclaws_gender";
    private static final String K_RANK = "moonclaws_rank";
    private static final String K_KIT = "moonclaws_kit_name";
    private static final String K_STEM = "moonclaws_stem";
    private static final String K_ENDING = "moonclaws_ending";
    private static final String K_LANG = "moonclaws_name_lang";
    private static final String K_CARD = "moonclaws_card_done";
    static final String K_LIVES = "moonclaws_lives";
    static final String K_ELDER_DAY = "moonclaws_elder_day";
    static final String K_SAVED_ITEMS = "moonclaws_saved_items";
    static final String K_SAVED_XP = "moonclaws_saved_xp";

    private static final UUID DAMAGE_ID = UUID.fromString("5f1c0b8e-6b3a-4c1e-9a0d-2f7e4a6b1c01");
    private static final UUID ATTACK_SPEED_ID = UUID.fromString("5f1c0b8e-6b3a-4c1e-9a0d-2f7e4a6b1c02");
    private static final UUID WIND_SPEED_ID = UUID.fromString("5f1c0b8e-6b3a-4c1e-9a0d-2f7e4a6b1c03");
    private static final UUID RANK_HEALTH_ID = UUID.fromString("5f1c0b8e-6b3a-4c1e-9a0d-2f7e4a6b1c04");
    private static final UUID RANK_DAMAGE_ID = UUID.fromString("5f1c0b8e-6b3a-4c1e-9a0d-2f7e4a6b1c05");
    private static final UUID RANK_SPEED_ID = UUID.fromString("5f1c0b8e-6b3a-4c1e-9a0d-2f7e4a6b1c06");

    private CatState() {
    }

    /** Everything the cat card holds. */
    public static final class Card {
        public int clan;
        public int gender;
        public int rank;
        public int skin;
        public String kitName = "";
        public String stem = "";
        public String ending = "";
        public int lang;
        public boolean done;
    }

    // ---------------------------------------------------------------- stored data (server)

    static CompoundNBT data(PlayerEntity p) {
        return p.getPersistentData().getCompound(PlayerEntity.PERSISTED_NBT_TAG);
    }

    static void save(PlayerEntity p, CompoundNBT d) {
        p.getPersistentData().put(PlayerEntity.PERSISTED_NBT_TAG, d);
    }

    public static Card readCard(PlayerEntity p) {
        CompoundNBT d = data(p);
        Card c = new Card();
        c.clan = d.getInt(K_CLAN);
        c.gender = d.getInt(K_GENDER);
        c.rank = d.getInt(K_RANK);
        c.skin = d.contains(K_SKIN) ? d.getInt(K_SKIN) : -1;
        c.kitName = d.getString(K_KIT);
        c.stem = d.getString(K_STEM);
        c.ending = d.getString(K_ENDING);
        c.lang = d.getInt(K_LANG);
        c.done = d.getBoolean(K_CARD);
        return c;
    }

    // ---------------------------------------------------------------- queries (both sides)

    private static ClientCatData.Info client(PlayerEntity p) {
        return ClientCatData.get(p.getUUID());
    }

    private static boolean onClient(PlayerEntity p) {
        return p.level != null && p.level.isClientSide;
    }

    public static boolean isCat(PlayerEntity p) {
        if (p == null || p.level == null) {
            return false;
        }
        if (p.level.isClientSide) {
            ClientCatData.Info i = client(p);
            return i != null && i.cat;
        }
        return data(p).getBoolean(K_CAT);
    }

    public static int getClan(PlayerEntity p) {
        if (onClient(p)) {
            ClientCatData.Info i = client(p);
            return i == null ? CLAN_NONE : i.clan;
        }
        return data(p).getInt(K_CLAN);
    }

    public static int getSkin(PlayerEntity p) {
        if (onClient(p)) {
            ClientCatData.Info i = client(p);
            return i == null ? -1 : i.skin;
        }
        CompoundNBT d = data(p);
        return d.contains(K_SKIN) ? d.getInt(K_SKIN) : -1;
    }

    public static int getRank(PlayerEntity p) {
        if (onClient(p)) {
            ClientCatData.Info i = client(p);
            return i == null ? RANK_NONE : i.rank;
        }
        return data(p).getInt(K_RANK);
    }

    public static int getGender(PlayerEntity p) {
        if (onClient(p)) {
            ClientCatData.Info i = client(p);
            return i == null ? GENDER_NONE : i.gender;
        }
        return data(p).getInt(K_GENDER);
    }

    /** Cat name for the current rank (empty if the card was never filled). */
    public static String getCatName(PlayerEntity p) {
        if (onClient(p)) {
            ClientCatData.Info i = client(p);
            return i == null ? "" : i.name;
        }
        Card c = readCard(p);
        if (!c.done) {
            return "";
        }
        return CatNames.compose(c.rank, c.lang, c.kitName, c.stem, c.ending);
    }

    public static boolean isCardDone(PlayerEntity p) {
        return data(p).getBoolean(K_CARD);
    }

    public static int getLives(PlayerEntity p) {
        return data(p).getInt(K_LIVES);
    }

    public static void setLives(PlayerEntity p, int lives) {
        CompoundNBT d = data(p);
        d.putInt(K_LIVES, lives);
        save(p, d);
    }

    // ---------------------------------------------------------------- rank numbers

    /** Hearts change in health points (2 points = 1 heart). Base cat: 8 hearts. */
    public static double healthDelta(int rank) {
        switch (rank) {
            case RANK_KIT: return -8.0D;       // 6 hearts
            case RANK_WARRIOR: return 0.0D;    // 10 hearts
            case RANK_ELDER: return -6.0D;     // 7 hearts
            default: return -4.0D;             // 8 hearts
        }
    }

    public static double rankDamage(int rank) {
        switch (rank) {
            case RANK_WARRIOR: return 1.0D;
            case RANK_KIT:
            case RANK_HEALER: return -1.0D;
            default: return 0.0D;
        }
    }

    public static double rankSpeed(int rank) {
        switch (rank) {
            case RANK_APPRENTICE: return 0.10D;
            case RANK_ELDER: return -0.10D;
            default: return 0.0D;
        }
    }

    /** Extra items in stray cat trades. */
    public static int tradeBonus(int rank) {
        switch (rank) {
            case RANK_DEPUTY: return 1;
            case RANK_ELDER: return 2;
            default: return 0;
        }
    }

    // ---------------------------------------------------------------- changing state (server)

    /** The player confirmed the cat card (or closed it as an old cat: defaults are filled in). */
    public static void applyCard(ServerPlayerEntity sp, Card card) {
        CompoundNBT d = data(sp);
        int oldRank = d.getInt(K_RANK);
        d.putInt(K_CLAN, card.clan);
        d.putInt(K_GENDER, card.gender);
        d.putInt(K_RANK, card.rank);
        d.putInt(K_SKIN, card.skin);
        d.putString(K_KIT, CatNames.clean(card.kitName));
        d.putString(K_STEM, CatNames.clean(card.stem));
        d.putString(K_ENDING, CatNames.clean(card.ending));
        d.putInt(K_LANG, card.lang);
        d.putBoolean(K_CARD, true);
        // a new leader gets nine lives (only the first time: lives are not refilled by switching classes)
        if (card.rank == RANK_LEADER && oldRank != RANK_LEADER && !d.contains(K_LIVES)) {
            d.putInt(K_LIVES, LEADER_LIVES);
        }
        save(sp, d);
        setCat(sp, true);
    }

    public static void setCat(ServerPlayerEntity sp, boolean cat) {
        CompoundNBT d = data(sp);
        if (cat && !d.contains(K_SKIN)) {
            d.putInt(K_SKIN, sp.getRandom().nextInt(SKINS));
        }
        d.putBoolean(K_CAT, cat);
        save(sp, d);

        if (!cat) {
            sp.removeEffect(Effects.NIGHT_VISION);
            sp.removeEffect(Effects.JUMP);
            sp.removeEffect(Effects.DOLPHINS_GRACE);
            sp.removeEffect(Effects.MOVEMENT_SPEED);
            sp.setSilent(false);
        }
        applyAbilities(sp);
        sp.refreshDimensions();
        sp.refreshDisplayName();
        sync(sp);
    }

    // ---------------------------------------------------------------- abilities (server)

    /** Called when the state changes and every 10 ticks while online. */
    public static void applyAbilities(ServerPlayerEntity sp) {
        boolean cat = isCat(sp);
        int clan = getClan(sp);
        int rank = getRank(sp);

        if (sp.isSilent()) {
            sp.setSilent(false);
        }

        // claws: a bit more damage, much faster attacks
        setModifier(sp, Attributes.ATTACK_DAMAGE, DAMAGE_ID, "Moonclaws claws",
                clan == CLAN_THUNDER ? 2.0D : 1.0D, AttributeModifier.Operation.ADDITION, cat);
        setModifier(sp, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, "Moonclaws quick claws",
                2.0D, AttributeModifier.Operation.ADDITION, cat);
        // WindClan: faster running
        setModifier(sp, Attributes.MOVEMENT_SPEED, WIND_SPEED_ID, "Moonclaws wind clan",
                0.15D, AttributeModifier.Operation.MULTIPLY_TOTAL, cat && clan == CLAN_WIND);

        // class: hearts, claws, speed
        double health = healthDelta(rank);
        setModifier(sp, Attributes.MAX_HEALTH, RANK_HEALTH_ID, "Moonclaws class hearts",
                health, AttributeModifier.Operation.ADDITION, cat && health != 0.0D);
        double dmg = rankDamage(rank);
        setModifier(sp, Attributes.ATTACK_DAMAGE, RANK_DAMAGE_ID, "Moonclaws class claws",
                dmg, AttributeModifier.Operation.ADDITION, cat && dmg != 0.0D);
        double speed = rankSpeed(rank);
        setModifier(sp, Attributes.MOVEMENT_SPEED, RANK_SPEED_ID, "Moonclaws class speed",
                speed, AttributeModifier.Operation.MULTIPLY_TOTAL, cat && speed != 0.0D);
        if (sp.getHealth() > sp.getMaxHealth()) {
            sp.setHealth(sp.getMaxHealth());
        }

        if (!cat) {
            return;
        }

        refreshEffect(sp, Effects.NIGHT_VISION, 0, 400, 300);
        refreshEffect(sp, Effects.JUMP, 0, 60, 30);
        if (clan == CLAN_RIVER) {
            refreshEffect(sp, Effects.DOLPHINS_GRACE, 0, 60, 30);
        }
        if (clan == CLAN_SHADOW && sp.level.getMaxLocalRawBrightness(sp.blockPosition()) <= 7) {
            refreshEffect(sp, Effects.MOVEMENT_SPEED, 0, 40, 20);
        }
    }

    private static void refreshEffect(ServerPlayerEntity sp, Effect effect, int amplifier, int duration, int minRemaining) {
        EffectInstance current = sp.getEffect(effect);
        if (current == null || current.getDuration() < minRemaining || current.getAmplifier() < amplifier) {
            sp.addEffect(new EffectInstance(effect, duration, amplifier, false, false));
        }
    }

    private static void setModifier(PlayerEntity p, Attribute attribute, UUID id, String name, double amount,
                                    AttributeModifier.Operation op, boolean enabled) {
        ModifiableAttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) {
            return;
        }
        AttributeModifier existing = inst.getModifier(id);
        if (!enabled) {
            if (existing != null) {
                inst.removeModifier(id);
            }
            return;
        }
        if (existing != null && existing.getAmount() == amount) {
            return;
        }
        if (existing != null) {
            inst.removeModifier(id);
        }
        inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    // ---------------------------------------------------------------- syncing (server -> clients)

    public static void sync(ServerPlayerEntity sp) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp), packetFor(sp));
    }

    public static void syncTo(ServerPlayerEntity target, ServerPlayerEntity viewer) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packetFor(target));
    }

    private static ModNetwork.SyncCatPacket packetFor(ServerPlayerEntity sp) {
        return new ModNetwork.SyncCatPacket(sp.getUUID(), isCat(sp), getSkin(sp), getClan(sp),
                getGender(sp), getRank(sp), getCatName(sp));
    }
}
