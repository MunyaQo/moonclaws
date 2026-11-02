package com.moonclaws.mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side copy of players' cat state (filled by sync packets). No Minecraft client classes here. */
public final class ClientCatData {

    public static final class Info {
        public final boolean cat;
        public final int skin;
        public final int clan;
        public final int gender;
        public final int rank;
        public final String name;

        public Info(boolean cat, int skin, int clan, int gender, int rank, String name) {
            this.cat = cat;
            this.skin = skin;
            this.clan = clan;
            this.gender = gender;
            this.rank = rank;
            this.name = name == null ? "" : name;
        }
    }

    private static final Map<UUID, Info> MAP = new ConcurrentHashMap<>();

    private ClientCatData() {
    }

    public static void put(UUID id, Info info) {
        MAP.put(id, info);
    }

    public static Info get(UUID id) {
        return MAP.get(id);
    }

    public static void clear() {
        MAP.clear();
    }
}
