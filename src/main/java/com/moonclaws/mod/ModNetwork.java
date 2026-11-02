package com.moonclaws.mod;

import com.moonclaws.mod.client.ClientHooks;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public final class ModNetwork {

    private static final String PROTOCOL = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MoonclawsMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenCardPacket.class,
                OpenCardPacket::encode, OpenCardPacket::decode, OpenCardPacket::handle);
        CHANNEL.registerMessage(id++, ApplyCardPacket.class,
                ApplyCardPacket::encode, ApplyCardPacket::decode, ApplyCardPacket::handle);
        CHANNEL.registerMessage(id++, SyncCatPacket.class,
                SyncCatPacket::encode, SyncCatPacket::decode, SyncCatPacket::handle);
        CHANNEL.registerMessage(id++, OpenTradePacket.class,
                OpenTradePacket::encode, OpenTradePacket::decode, OpenTradePacket::handle);
        CHANNEL.registerMessage(id++, TradePacket.class,
                TradePacket::encode, TradePacket::decode, TradePacket::handle);
        CHANNEL.registerMessage(id++, ElderMemoryPacket.class,
                ElderMemoryPacket::encode, ElderMemoryPacket::decode, ElderMemoryPacket::handle);
    }

    static void writeCard(CatState.Card c, PacketBuffer buf) {
        buf.writeInt(c.clan);
        buf.writeInt(c.gender);
        buf.writeInt(c.rank);
        buf.writeInt(c.skin);
        buf.writeUtf(c.kitName, 64);
        buf.writeUtf(c.stem, 64);
        buf.writeUtf(c.ending, 64);
        buf.writeInt(c.lang);
        buf.writeBoolean(c.done);
    }

    static CatState.Card readCard(PacketBuffer buf) {
        CatState.Card c = new CatState.Card();
        c.clan = buf.readInt();
        c.gender = buf.readInt();
        c.rank = buf.readInt();
        c.skin = buf.readInt();
        c.kitName = buf.readUtf(64);
        c.stem = buf.readUtf(64);
        c.ending = buf.readUtf(64);
        c.lang = buf.readInt();
        c.done = buf.readBoolean();
        return c;
    }

    // server -> client: open the cat card with the current data
    public static class OpenCardPacket {
        public final CatState.Card card;

        public OpenCardPacket(CatState.Card card) {
            this.card = card;
        }

        public static void encode(OpenCardPacket msg, PacketBuffer buf) {
            writeCard(msg.card, buf);
        }

        public static OpenCardPacket decode(PacketBuffer buf) {
            return new OpenCardPacket(readCard(buf));
        }

        public static void handle(OpenCardPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openCardScreen(msg.card)));
            ctx.get().setPacketHandled(true);
        }
    }

    // client -> server: the card was confirmed (or closed by an old cat: then defaults are used)
    public static class ApplyCardPacket {
        public final CatState.Card card;
        public final boolean dismissed;

        public ApplyCardPacket(CatState.Card card, boolean dismissed) {
            this.card = card;
            this.dismissed = dismissed;
        }

        public static void encode(ApplyCardPacket msg, PacketBuffer buf) {
            writeCard(msg.card, buf);
            buf.writeBoolean(msg.dismissed);
        }

        public static ApplyCardPacket decode(PacketBuffer buf) {
            return new ApplyCardPacket(readCard(buf), buf.readBoolean());
        }

        public static void handle(ApplyCardPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayerEntity sender = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sender == null) {
                    return;
                }
                // the card is opened with the Moon Stone, so it must be at hand
                if (sender.getMainHandItem().getItem() != MoonclawsMod.MOON_STONE.get()
                        && sender.getOffhandItem().getItem() != MoonclawsMod.MOON_STONE.get()) {
                    return;
                }
                CatState.Card old = CatState.readCard(sender);
                CatState.Card c = msg.card;
                if (msg.dismissed) {
                    // only an old cat (has a clan, never saw the card) gets defaults when closing it
                    if (old.clan == CatState.CLAN_NONE || old.done) {
                        return;
                    }
                    c = old;
                    c.gender = sender.getRandom().nextBoolean() ? CatState.GENDER_TOM : CatState.GENDER_SHE;
                    c.rank = CatState.RANK_WARRIOR;
                    if (c.skin < 0) {
                        c.skin = sender.getRandom().nextInt(CatState.SKINS);
                    }
                    String[] name = CatNames.random(msg.card.lang, sender.getRandom());
                    c.kitName = name[0];
                    c.stem = name[1];
                    c.ending = name[2];
                    c.lang = msg.card.lang;
                }
                if (c.clan < CatState.CLAN_THUNDER || c.clan > CatState.CLAN_SHADOW) {
                    return;
                }
                if (c.gender != CatState.GENDER_TOM && c.gender != CatState.GENDER_SHE) {
                    return;
                }
                if (c.rank < CatState.RANK_KIT || c.rank > CatState.RANK_ELDER) {
                    return;
                }
                if (c.skin < 0 || c.skin >= CatState.SKINS) {
                    c.skin = sender.getRandom().nextInt(CatState.SKINS);
                }
                if (c.lang != CatNames.LANG_EN) {
                    c.lang = CatNames.LANG_RU;
                }
                if (CatNames.clean(c.kitName).isEmpty() && CatNames.clean(c.stem).isEmpty()) {
                    String[] name = CatNames.random(c.lang, sender.getRandom());
                    c.kitName = name[0];
                    c.stem = name[1];
                    c.ending = name[2];
                }
                CatState.applyCard(sender, c);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // server -> client: state of one player
    public static class SyncCatPacket {
        public final UUID id;
        public final boolean cat;
        public final int skin;
        public final int clan;
        public final int gender;
        public final int rank;
        public final String name;

        public SyncCatPacket(UUID id, boolean cat, int skin, int clan, int gender, int rank, String name) {
            this.id = id;
            this.cat = cat;
            this.skin = skin;
            this.clan = clan;
            this.gender = gender;
            this.rank = rank;
            this.name = name;
        }

        public static void encode(SyncCatPacket msg, PacketBuffer buf) {
            buf.writeUUID(msg.id);
            buf.writeBoolean(msg.cat);
            buf.writeInt(msg.skin);
            buf.writeInt(msg.clan);
            buf.writeInt(msg.gender);
            buf.writeInt(msg.rank);
            buf.writeUtf(msg.name, 64);
        }

        public static SyncCatPacket decode(PacketBuffer buf) {
            return new SyncCatPacket(buf.readUUID(), buf.readBoolean(), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readUtf(64));
        }

        public static void handle(SyncCatPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ClientCatData.put(msg.id, new ClientCatData.Info(msg.cat, msg.skin, msg.clan, msg.gender, msg.rank, msg.name));
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.onSync(msg.id));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // server -> client: open the stray cat trade window
    public static class OpenTradePacket {
        public static void encode(OpenTradePacket msg, PacketBuffer buf) {
        }

        public static OpenTradePacket decode(PacketBuffer buf) {
            return new OpenTradePacket();
        }

        public static void handle(OpenTradePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHooks::openTradeScreen));
            ctx.get().setPacketHandled(true);
        }
    }

    // client -> server: the player pressed a trade button
    public static class TradePacket {
        public final int index;

        public TradePacket(int index) {
            this.index = index;
        }

        public static void encode(TradePacket msg, PacketBuffer buf) {
            buf.writeInt(msg.index);
        }

        public static TradePacket decode(PacketBuffer buf) {
            return new TradePacket(buf.readInt());
        }

        public static void handle(TradePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayerEntity sender = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sender == null || !CatState.isCat(sender)) {
                    return;
                }
                if (sender.level.getEntitiesOfClass(StrayCatEntity.class, sender.getBoundingBox().inflate(6.0D)).isEmpty()) {
                    return;
                }
                StrayTrades.execute(sender, msg.index);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // client -> server: an elder used "memory" (Shift + right click with an empty paw)
    public static class ElderMemoryPacket {
        public static void encode(ElderMemoryPacket msg, PacketBuffer buf) {
        }

        public static ElderMemoryPacket decode(PacketBuffer buf) {
            return new ElderMemoryPacket();
        }

        public static void handle(ElderMemoryPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayerEntity sender = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sender != null) {
                    RankEvents.elderMemory(sender);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
