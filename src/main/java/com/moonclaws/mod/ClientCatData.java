package com.moonclaws.mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side copy of other players' cat state (filled by sync packets). No Minecraft client classes here. */
public final class ClientCatData {

    public static final class Info {
        public final boolean cat;
        public final int skin;
        public final int clan;

        public Info(boolean cat, int skin, int clan) {
            this.cat = cat;
            this.skin = skin;
            this.clan = clan;
        }
    }

    private static final Map<UUID, Info> MAP = new ConcurrentHashMap<>();

    private ClientCatData() {
    }

    public static void put(UUID id, boolean cat, int skin, int clan) {
        MAP.put(id, new Info(cat, skin, clan));
    }

    public static Info get(UUID id) {
        return MAP.get(id);
    }

    public static void clear() {
        MAP.clear();
    }
}
