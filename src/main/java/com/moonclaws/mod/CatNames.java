package com.moonclaws.mod;

import java.util.Random;

/**
 * Cat names "as in the books": a start (the kit name and the stem for adult names) plus an ending by rank.
 * Kit: "Ласточка". Apprentice: "Ласточко" + "лап". Warrior-like: "Ласточко" + chosen ending. Leader: + "звёзд".
 * No Minecraft client classes here: used on both sides.
 */
public final class CatNames {

    public static final int LANG_RU = 0;
    public static final int LANG_EN = 1;

    public static final int MAX_LENGTH = 16;

    /** {kit name, stem} pairs, Russian. */
    private static final String[][] RU = {
            {"Огонёк", "Огне"}, {"Пеплинка", "Пепло"}, {"Ласточка", "Ласточко"}, {"Ежевичка", "Ежевико"},
            {"Листик", "Листо"}, {"Лунка", "Луно"}, {"Туманчик", "Тумано"}, {"Ручеёк", "Ручье"},
            {"Камушек", "Камне"}, {"Мышка", "Мыше"}, {"Ветерок", "Ветро"}, {"Дождик", "Дожде"},
            {"Рябинка", "Рябино"}, {"Берёзка", "Берёзо"}, {"Ольшинка", "Ольхо"}, {"Совушка", "Сово"},
            {"Ястребок", "Ястребо"}, {"Пёрышко", "Пёрышко"}, {"Снежок", "Снего"}, {"Инейка", "Инее"},
            {"Белка", "Бело"}, {"Чернушка", "Черно"}, {"Рыжик", "Рыже"}, {"Серка", "Серо"},
            {"Пёстрик", "Пёстро"}, {"Полосатик", "Полосо"}, {"Солнышко", "Солнце"}, {"Зоренька", "Заре"},
            {"Ночка", "Ночно"}, {"Тенёк", "Тене"}, {"Песчинка", "Песко"}, {"Тёрнушка", "Терно"},
            {"Колючка", "Колюче"}, {"Крапивка", "Крапиво"}, {"Мятка", "Мято"}, {"Вересок", "Вереско"},
            {"Осинка", "Осино"}, {"Дубок", "Дубо"}, {"Клёнушка", "Клёно"}, {"Хвоинка", "Хвое"},
            {"Шишка", "Шишко"}, {"Мшинка", "Мохо"}, {"Травинка", "Траво"}, {"Росинка", "Росо"},
            {"Искорка", "Искро"}, {"Уголёк", "Угле"}, {"Дымок", "Дымо"}, {"Громушка", "Громо"},
            {"Бурька", "Буре"}, {"Вихрик", "Вихре"}, {"Ледок", "Ледо"}, {"Сойка", "Сойко"},
            {"Зяблик", "Зябло"}, {"Соколик", "Соколо"}, {"Лисичка", "Лисо"}, {"Ёжик", "Еже"},
            {"Камышок", "Камыше"}, {"Осочка", "Осоко"}, {"Ивушка", "Иво"}, {"Лужок", "Луго"}
    };

    private static final String[] RU_ENDINGS = {
            "хвост", "грив", "лист", "коготь", "сердце", "шёрстка", "ус", "клык", "глаз", "нос", "шаг", "шкур", "цвет"
    };

    private static final String[] EN_STEMS = {
            "Fire", "Ash", "Swallow", "Bramble", "Leaf", "Moon", "Mist", "Brook", "Stone", "Mouse", "Wind",
            "Rain", "Rowan", "Birch", "Alder", "Owl", "Hawk", "Feather", "Snow", "Frost", "White", "Black",
            "Russet", "Gray", "Dapple", "Stripe", "Sun", "Dawn", "Night", "Shade", "Sand", "Thorn", "Nettle",
            "Mint", "Heather", "Oak", "Maple", "Pine", "Moss", "Dew", "Spark", "Ember", "Smoke", "Thunder",
            "Storm", "Ice", "Jay", "Finch", "Falcon", "Fox", "Reed", "Sedge", "Willow", "Meadow", "Pebble"
    };

    private static final String[] EN_ENDINGS = {
            "tail", "heart", "claw", "pelt", "whisker", "fang", "eye", "nose", "step", "fur", "leaf", "stripe", "foot"
    };

    private CatNames() {
    }

    public static String[] endings(int lang) {
        return lang == LANG_EN ? EN_ENDINGS : RU_ENDINGS;
    }

    /** Result of the "random name" button: {kit name, stem, ending}. */
    public static String[] random(int lang, Random random) {
        if (lang == LANG_EN) {
            String stem = EN_STEMS[random.nextInt(EN_STEMS.length)];
            return new String[] {stem + "kit", stem, EN_ENDINGS[random.nextInt(EN_ENDINGS.length)]};
        }
        String[] pair = RU[random.nextInt(RU.length)];
        return new String[] {pair[0], pair[1], RU_ENDINGS[random.nextInt(RU_ENDINGS.length)]};
    }

    /** Stem guessed from a typed kit name (the player can correct it in the card). */
    public static String guessStem(String kitName, int lang) {
        String s = clean(kitName);
        if (s.isEmpty()) {
            return s;
        }
        if (lang == LANG_EN) {
            return s.toLowerCase().endsWith("kit") && s.length() > 3 ? s.substring(0, s.length() - 3) : s;
        }
        // known names first
        for (String[] pair : RU) {
            if (pair[0].equalsIgnoreCase(s)) {
                return pair[1];
            }
        }
        char last = Character.toLowerCase(s.charAt(s.length() - 1));
        String body = s.substring(0, s.length() - 1);
        switch (last) {
            case 'а': case 'о': case 'у': case 'ы':
                return body + "о";
            case 'я': case 'е': case 'и': case 'ь': case 'й': case 'ё': case 'ю':
                return body + "е";
            default:
                return s + "о";
        }
    }

    /** Full name for a rank. */
    public static String compose(int rank, int lang, String kitName, String stem, String ending) {
        String kit = clean(kitName);
        String st = clean(stem);
        if (st.isEmpty()) {
            st = guessStem(kit, lang);
        }
        if (kit.isEmpty()) {
            kit = st;
        }
        boolean en = lang == LANG_EN;
        switch (rank) {
            case CatState.RANK_KIT:
                return kit;
            case CatState.RANK_APPRENTICE:
                return st + (en ? "paw" : "лап");
            case CatState.RANK_LEADER:
                return st + (en ? "star" : "звёзд");
            case CatState.RANK_WARRIOR:
            case CatState.RANK_HEALER:
            case CatState.RANK_DEPUTY:
            case CatState.RANK_ELDER:
                String e = clean(ending);
                if (e.isEmpty()) {
                    e = endings(lang)[0];
                }
                return st + e.toLowerCase();
            default:
                return kit;
        }
    }

    /** Removes formatting codes and odd characters, limits the length. */
    public static String clean(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '§') {
                i++; // skip the formatting code and the letter after it
                continue;
            }
            if (Character.isLetter(c) || c == '-' || c == ' ' || c == '\'') {
                b.append(c);
            }
        }
        String out = b.toString().trim();
        if (out.length() > MAX_LENGTH) {
            out = out.substring(0, MAX_LENGTH);
        }
        return out;
    }
}
