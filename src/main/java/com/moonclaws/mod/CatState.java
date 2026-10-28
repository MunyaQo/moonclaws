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

    private static final String K_CAT = "moonclaws_cat";
    private static final String K_CLAN = "moonclaws_clan";
    private static final String K_SKIN = "moonclaws_skin";

    private static final UUID DAMAGE_ID = UUID.fromString("5f1c0b8e-6b3a-4c1e-9a0d-2f7e4a6b1c01");
    private static final UUID ATTACK_SPEED_ID = UUID.fromString("5f1c0b8e-6b3a-4c1e-9a0d-2f7e4a6b1c02");
    private static final UUID WIND_SPEED_ID = UUID.fromString("5f1c0b8e-6b3a-4c1e-9a0d-2f7e4a6b1c03");

    private CatState() {
    }

    // ---------------------------------------------------------------- stored data (server)

    private static CompoundNBT data(PlayerEntity p) {
        return p.getPersistentData().getCompound(PlayerEntity.PERSISTED_NBT_TAG);
    }

    private static void save(PlayerEntity p, CompoundNBT d) {
        p.getPersistentData().put(PlayerEntity.PERSISTED_NBT_TAG, d);
    }

    // ---------------------------------------------------------------- queries (both sides)

    public static boolean isCat(PlayerEntity p) {
        if (p == null || p.level == null) {
            return false;
        }
        if (p.level.isClientSide) {
            ClientCatData.Info i = ClientCatData.get(p.getUUID());
            return i != null && i.cat;
        }
        return data(p).getBoolean(K_CAT);
    }

    public static int getClan(PlayerEntity p) {
        if (p.level != null && p.level.isClientSide) {
            ClientCatData.Info i = ClientCatData.get(p.getUUID());
            return i == null ? CLAN_NONE : i.clan;
        }
        return data(p).getInt(K_CLAN);
    }

    public static int getSkin(PlayerEntity p) {
        if (p.level != null && p.level.isClientSide) {
            ClientCatData.Info i = ClientCatData.get(p.getUUID());
            return i == null ? -1 : i.skin;
        }
        CompoundNBT d = data(p);
        return d.contains(K_SKIN) ? d.getInt(K_SKIN) : -1;
    }

    // ---------------------------------------------------------------- changing state (server)

    public static void chooseClan(ServerPlayerEntity sp, int clan) {
        CompoundNBT d = data(sp);
        d.putInt(K_CLAN, clan);
        save(sp, d);
        setCat(sp, true);
    }

    public static void setCat(ServerPlayerEntity sp, boolean cat) {
        CompoundNBT d = data(sp);
        if (cat && !d.contains(K_SKIN)) {
            // random skin, chosen once and kept forever
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
        sync(sp);
    }

    // ---------------------------------------------------------------- abilities (server)

    /** Called when the state changes and every 10 ticks while online. */
    public static void applyAbilities(ServerPlayerEntity sp) {
        boolean cat = isCat(sp);
        int clan = getClan(sp);

        // cleanup: the first version muted the player, it is not used anymore
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

        if (!cat) {
            return;
        }

        refreshEffect(sp, Effects.NIGHT_VISION, 0, 400, 300); // night vision
        refreshEffect(sp, Effects.JUMP, 0, 60, 30);           // higher jump
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

    /** Send this player's state to himself and to everyone who sees him. */
    public static void sync(ServerPlayerEntity sp) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp), packetFor(sp));
    }

    /** Send target's state to one viewer. */
    public static void syncTo(ServerPlayerEntity target, ServerPlayerEntity viewer) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packetFor(target));
    }

    private static ModNetwork.SyncCatPacket packetFor(ServerPlayerEntity sp) {
        return new ModNetwork.SyncCatPacket(sp.getUUID(), isCat(sp), getSkin(sp), getClan(sp));
    }
}
