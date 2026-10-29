package com.moonclaws.mod.client;

import com.moonclaws.mod.ModEntities;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only setup: the stray cat uses the vanilla cat renderer. */
public final class ClientSetup {

    private ClientSetup() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientSetup::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        RenderingRegistry.registerEntityRenderingHandler(ModEntities.STRAY_CAT.get(), manager -> new CatRenderer(manager));
    }
}
