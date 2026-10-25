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

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MoonclawsMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenClanScreenPacket.class,
                OpenClanScreenPacket::encode, OpenClanScreenPacket::decode, OpenClanScreenPacket::handle);
        CHANNEL.registerMessage(id++, ChooseClanPacket.class,
                ChooseClanPacket::encode, ChooseClanPacket::decode, ChooseClanPacket::handle);
        CHANNEL.registerMessage(id++, SyncCatPacket.class,
                SyncCatPacket::encode, SyncCatPacket::decode, SyncCatPacket::handle);
    }

    // server -> client: open the clan selection screen
    public static class OpenClanScreenPacket {
        public static void encode(OpenClanScreenPacket msg, PacketBuffer buf) {
        }

        public static OpenClanScreenPacket decode(PacketBuffer buf) {
            return new OpenClanScreenPacket();
        }

        public static void handle(OpenClanScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHooks::openClanScreen));
            ctx.get().setPacketHandled(true);
        }
    }

    // client -> server: the player picked a clan
    public static class ChooseClanPacket {
        public final int clan;

        public ChooseClanPacket(int clan) {
            this.clan = clan;
        }

        public static void encode(ChooseClanPacket msg, PacketBuffer buf) {
            buf.writeInt(msg.clan);
        }

        public static ChooseClanPacket decode(PacketBuffer buf) {
            return new ChooseClanPacket(buf.readInt());
        }

        public static void handle(ChooseClanPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayerEntity sender = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sender == null) {
                    return;
                }
                if (msg.clan < CatState.CLAN_THUNDER || msg.clan > CatState.CLAN_SHADOW) {
                    return;
                }
                // only allowed for the very first transformation
                if (CatState.isCat(sender) || CatState.getClan(sender) != CatState.CLAN_NONE) {
                    return;
                }
                CatState.chooseClan(sender, msg.clan);
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

        public SyncCatPacket(UUID id, boolean cat, int skin, int clan) {
            this.id = id;
            this.cat = cat;
            this.skin = skin;
            this.clan = clan;
        }

        public static void encode(SyncCatPacket msg, PacketBuffer buf) {
            buf.writeUUID(msg.id);
            buf.writeBoolean(msg.cat);
            buf.writeInt(msg.skin);
            buf.writeInt(msg.clan);
        }

        public static SyncCatPacket decode(PacketBuffer buf) {
            return new SyncCatPacket(buf.readUUID(), buf.readBoolean(), buf.readInt(), buf.readInt());
        }

        public static void handle(SyncCatPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ClientCatData.put(msg.id, msg.cat, msg.skin, msg.clan);
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.onSync(msg.id));
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
