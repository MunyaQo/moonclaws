package com.moonclaws.mod.client;

import com.moonclaws.mod.ModEntities;
import com.moonclaws.mod.MoonclawsMod;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only setup: stray cat renderer, Healer's Stone window. */
public final class ClientSetup {

    private ClientSetup() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientSetup::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        RenderingRegistry.registerEntityRenderingHandler(ModEntities.STRAY_CAT.get(), manager -> new CatRenderer(manager));
        RenderingRegistry.registerEntityRenderingHandler(ModEntities.CLAN_CAT.get(), manager -> new CatRenderer(manager));
        RenderingRegistry.registerEntityRenderingHandler(ModEntities.KITTYPET.get(), manager -> new CatRenderer(manager));
        event.enqueueWork(() -> {
            ScreenManager.register(MoonclawsMod.HEALER_CONTAINER.get(), HealerScreen::new);
            ScreenManager.register(MoonclawsMod.CAT_TRADE.get(), CatTradeScreen::new);
        });
    }
}
