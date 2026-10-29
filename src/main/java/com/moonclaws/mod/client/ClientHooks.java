package com.moonclaws.mod.client;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;

/** Client-only helpers (only called through DistExecutor). */
public final class ClientHooks {

    private ClientHooks() {
    }

    public static void openClanScreen() {
        Minecraft.getInstance().setScreen(new ClanScreen());
    }

    public static void openTradeScreen() {
        Minecraft.getInstance().setScreen(new TradeScreen());
    }

    /** New state of a player arrived: recalculate his hitbox size. */
    public static void onSync(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        PlayerEntity player = mc.level.getPlayerByUUID(id);
        if (player != null) {
            player.refreshDimensions();
        }
    }
}
