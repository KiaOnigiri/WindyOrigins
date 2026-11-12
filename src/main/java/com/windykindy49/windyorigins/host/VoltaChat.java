package com.windykindy49.windyorigins.host;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Understands what is said to Volta. Requests are free-form ("Вольта, сруби-ка это дерево",
 * "hey volta could you find Steve_02 please", "ボルタ、ダイヤを探して"): the sentence is cleaned up,
 * player names are picked out before anything can mangle them, and the remaining words are matched
 * against word stems in ten languages, forgiving a typo.
 */
public final class VoltaChat {
    public static final int FLAG_DOWN = 1;
    public static final int FLAG_WALL = 2;
    public static final int FLAG_PULL = 4;
    public static final int FLAG_TALK = 8;
    private static final String[] NAME_STEMS = new String[]{"вольт", "volta", "wolta"};
    private static final String[] NAME_CJK = new String[]{"ヴォルタ", "ボルタ", "沃尔塔", "沃塔"};
    private static final Map<HostCommand, String[]> VERBS = new LinkedHashMap<>();
    private static final Map<String, String[]> TOPICS = new LinkedHashMap<>();
    private static final Map<Integer, String[]> FLAGS = new LinkedHashMap<>();
    public static final Map<String, String> ALIASES = new LinkedHashMap<>();
    private static final Map<String, Integer> NUMBERS = new HashMap<>();
    private static final Set<String> FILLER = new HashSet<>();
    private static final Set<String> GREETERS = Set.of("эй", "ей", "слушай", "слышь", "hey", "hi", "ok", "okay", "окей", "ок", "yo", "ну", "а", "oi", "ey", "he", "dis");
    public static final String[] ENEMY_WORDS = new String[]{
        "враг", "монстр", "моб", "ворог", "нечист", "enemy", "enemies", "monster", "mob", "hostile", "feind", "ennemi", "enemigo", "inimigo", "wrog", "敵", "敌", "怪"
    };

    private VoltaChat() {
    }

    public static record Request(HostCommand command, String subject, int number, UUID player, int flags, String topic) {
        public boolean has(int flag) {
            return (this.flags & flag) != 0;
        }

        public static Request of(HostCommand command) {
            return new Request(command, "", -1, null, 0, null);
        }
    }

    // ------------------------------------------------------------------ text helpers

    /** Lower case, ё→е, accents off Latin letters, punctuation to spaces (underscores survive for nicknames). */
    public static String normalize(String text) {
        StringBuilder out = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if (c == 'ё') {
                c = 'е';
            } else if (c == 'ł') {
                c = 'l';
            } else if (c == 'ß') {
                out.append("ss");
                continue;
            } else if (c >= 0x00C0 && c <= 0x024F) {
                String d = java.text.Normalizer.normalize(String.valueOf(c), java.text.Normalizer.Form.NFD);
                c = d.charAt(0);
            }

            if (Character.isLetterOrDigit(c) || c == '_') {
                out.append(c);
            } else {
                out.append(' ');
            }
        }

        return out.toString().trim().replaceAll("\\s+", " ");
    }

    public static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;

            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }

            int[] swap = prev;
            prev = cur;
            cur = swap;
        }

        return prev[b.length()];
    }

    private static boolean cjk(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) >= 0x2E80) {
                return true;
            }
        }

        return false;
    }

    /**
     * Does this token carry the keyword? Stems match by prefix, very short words and words marked
     * with a trailing '$' only exactly, and longer words forgive one slip of the finger.
     */
    private static boolean tokenMatches(String token, String keyword) {
        boolean exact = keyword.endsWith("$");
        if (exact) {
            keyword = keyword.substring(0, keyword.length() - 1);
        }

        if (exact || keyword.length() <= 3) {
            return token.equals(keyword);
        }

        if (token.startsWith(keyword)) {
            return true;
        }

        if (keyword.length() >= 5 && token.length() >= keyword.length() - 1) {
            String head = token.substring(0, Math.min(token.length(), keyword.length()));
            return head.charAt(0) == keyword.charAt(0) && distance(head, keyword) <= 1;
        }

        return false;
    }

    /** Index of the first token that carries the keyword, or -1. Phrases and CJK match on the whole text. */
    private static int find(String text, String[] tokens, String keyword) {
        if (cjk(keyword)) {
            int at = text.indexOf(keyword);
            return at < 0 ? -1 : at / 2;
        }

        if (keyword.indexOf(' ') >= 0) {
            int at = (" " + text + " ").indexOf(" " + keyword + " ");
            if (at < 0) {
                at = (" " + text + " ").indexOf(" " + keyword);
                if (at < 0) {
                    return -1;
                }
            }

            int index = 0;

            for (int i = 0; i < at && i < text.length(); i++) {
                if (text.charAt(i) == ' ') {
                    index++;
                }
            }

            return index;
        }

        for (int i = 0; i < tokens.length; i++) {
            if (tokenMatches(tokens[i], keyword)) {
                return i;
            }
        }

        return -1;
    }

    private static boolean isName(String token) {
        for (String stem : NAME_STEMS) {
            if (token.startsWith(stem) && token.length() <= stem.length() + 5) {
                return true;
            }
        }

        return token.length() >= 5 && token.length() <= 7 && (distance(token, "вольта") <= 1 || distance(token, "volta") <= 1);
    }

    /**
     * If the message is spoken to Volta, returns what was said to her with her name taken out
     * (original spelling kept, so nicknames in it survive); otherwise null. Her name may open the
     * sentence, follow a "hey", or close it.
     */
    public static String addressed(String message) {
        String raw = message.trim();

        for (String name : NAME_CJK) {
            int at = raw.indexOf(name);
            if (at >= 0) {
                return (raw.substring(0, at) + " " + raw.substring(at + name.length())).replaceFirst("^[\\s,.:;!?、，。！？]+", "").trim();
            }
        }

        String[] words = raw.split("\\s+");
        int found = -1;

        for (int i = 0; i < words.length; i++) {
            String token = normalize(words[i]);
            if (token.indexOf(' ') < 0 && isName(token)) {
                boolean opening = i <= 2;

                for (int j = 0; j < i && opening; j++) {
                    opening = GREETERS.contains(normalize(words[j]));
                }

                if (opening || i == words.length - 1) {
                    found = i;
                    break;
                }
            }
        }

        if (found < 0) {
            return null;
        }

        StringBuilder rest = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i != found && !(i < found && GREETERS.contains(normalize(words[i])))) {
                rest.append(words[i]).append(' ');
            }
        }

        return rest.toString().replaceFirst("^[\\s,.:;!?]+", "").trim();
    }

    private static final String CYR = "абвгдежзийклмнопрстуфхцчшщъыьэюяіїєґ";
    private static final String[] LAT = new String[]{
        "a", "b", "v", "g", "d", "e", "zh", "z", "i", "y", "k", "l", "m", "n", "o", "p", "r", "s", "t", "u", "f", "h", "c", "ch", "sh", "sch", "", "y", "", "e", "yu",
        "ya", "i", "yi", "e", "g"
    };

    private static String translit(String text) {
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            int at = CYR.indexOf(text.charAt(i));
            out.append(at >= 0 ? LAT[at] : String.valueOf(text.charAt(i)));
        }

        return out.toString();
    }

    private static String bare(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** How surely a typed word means this player name, 0 for not at all. */
    private static int nameScore(String word, String name) {
        String w = word.toLowerCase(Locale.ROOT);
        String n = name.toLowerCase(Locale.ROOT);
        if (w.equals(n)) {
            return 100;
        }

        String bw = bare(w);
        String bn = bare(n);
        if (bw.length() < 2 || bn.isEmpty()) {
            return 0;
        }

        if (bw.equals(bn)) {
            return 95;
        }

        String tw = translit(bw);
        if (tw.equals(bn)) {
            return 90;
        }

        if (bw.length() >= 3 && (bn.startsWith(bw) || bn.startsWith(tw))) {
            return 70 + Math.min(15, bw.length() * 2);
        }

        if (bw.length() >= 4) {
            int allowed = Math.max(1, bn.length() / 4);
            int d = Math.min(distance(bw, bn), distance(tw, bn));
            if (d <= allowed) {
                return 68 - d * 4;
            }
        }

        // Russian speakers decline nicknames: "найди Стива", "ударь Alex'а".
        if (bw.length() >= 4 && bw.length() <= bn.length() + 3) {
            for (int cut = 1; cut <= 2 && bw.length() - cut >= 3; cut++) {
                String stem = bw.substring(0, bw.length() - cut);
                String tstem = translit(stem);
                if (stem.equals(bn) || tstem.equals(bn) || bn.length() >= 4 && distance(tstem, bn) <= 1) {
                    return 62;
                }
            }
        }

        return 0;
    }

    private static boolean knownWord(String token) {
        if (FILLER.contains(token) || NUMBERS.containsKey(token)) {
            return true;
        }

        String[] one = new String[]{token};

        for (String[] words : VERBS.values()) {
            for (String word : words) {
                if (word.indexOf(' ') < 0 && !cjk(word) && find(token, one, word) == 0) {
                    return true;
                }
            }
        }

        for (String alias : ALIASES.keySet()) {
            if (!cjk(alias) && token.startsWith(alias.trim())) {
                return true;
            }
        }

        return false;
    }

    // ------------------------------------------------------------------ parsing

    public static Request parse(String said, ServerPlayerEntity speaker, Collection<ServerPlayerEntity> online) {
        // Nicknames first, on the untouched text: "Cool_Guy_07" must not fall apart into words and a number.
        String[] rawWords = said.trim().split("[\\s,.:;!?]+");
        UUID player = null;
        int bestScore = 0;
        int bestWord = -1;

        for (int i = 0; i < rawWords.length; i++) {
            String word = rawWords[i];
            if (word.isEmpty()) {
                continue;
            }

            for (ServerPlayerEntity other : online) {
                if (other == speaker) {
                    continue;
                }

                int score = nameScore(word, other.getGameProfile().getName());
                if (score < 90 && knownWord(normalize(word))) {
                    score = 0;
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestWord = i;
                    player = other.getUuid();
                }
            }
        }

        if (bestScore < 60) {
            player = null;
            bestWord = -1;
        }

        StringBuilder kept = new StringBuilder();

        for (int i = 0; i < rawWords.length; i++) {
            if (i != bestWord) {
                kept.append(rawWords[i]).append(' ');
            }
        }

        String text = normalize(kept.toString());
        String[] tokens = text.isEmpty() ? new String[0] : text.split(" ");
        HostCommand command = null;
        int at = Integer.MAX_VALUE;
        int length = 0;

        for (Map.Entry<HostCommand, String[]> entry : VERBS.entrySet()) {
            for (String word : entry.getValue()) {
                int index = find(text, tokens, word);
                if (index >= 0 && (index < at || index == at && word.length() > length)) {
                    command = entry.getKey();
                    at = index;
                    length = word.length();
                }
            }
        }

        int flags = 0;

        for (Map.Entry<Integer, String[]> entry : FLAGS.entrySet()) {
            for (String word : entry.getValue()) {
                if (find(text, tokens, word) >= 0) {
                    flags |= entry.getKey();
                    break;
                }
            }
        }

        if (command == null) {
            for (Map.Entry<String, String[]> entry : TOPICS.entrySet()) {
                for (String word : entry.getValue()) {
                    if (find(text, tokens, word) >= 0) {
                        return new Request(HostCommand.CHAT, "", -1, player, flags, entry.getKey());
                    }
                }
            }

            if (tokens.length == 0 && player == null) {
                return new Request(HostCommand.CHAT, "", -1, null, flags, "hello");
            }

            // "Вольта, Steve?" — a bare name can only mean "where is he".
            return player != null ? new Request(HostCommand.FIND, "", -1, player, flags, null) : null;
        }

        int number = -1;
        List<String> subject = new ArrayList<>();

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.matches("\\d{1,3}")) {
                number = Integer.parseInt(token);
            } else if (NUMBERS.containsKey(token)) {
                number = NUMBERS.get(token);
            } else if (i != at && !FILLER.contains(token) && !carriesVerb(token)) {
                subject.add(token);
            }
        }

        return new Request(command, String.join(" ", subject), number, player, flags, null);
    }

    private static boolean carriesVerb(String token) {
        String[] one = new String[]{token};

        for (String[] words : VERBS.values()) {
            for (String word : words) {
                if (word.indexOf(' ') < 0 && !cjk(word) && find(token, one, word) == 0) {
                    return true;
                }
            }
        }

        return false;
    }

    public static String canonical(String subject) {
        String text = " " + normalize(subject) + " ";

        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            String key = entry.getKey();
            if (cjk(key) ? text.contains(key) : text.contains(" " + key)) {
                return entry.getValue();
            }
        }

        return null;
    }

    public static boolean wantsEnemy(String subject) {
        String text = normalize(subject);

        for (String word : ENEMY_WORDS) {
            if (text.contains(word)) {
                return true;
            }
        }

        return text.isBlank();
    }

    private static void alias(String id, String... words) {
        for (String word : words) {
            ALIASES.put(word, id);
        }
    }

    private static void numbers(int value, String... words) {
        for (String word : words) {
            NUMBERS.put(word, value);
        }
    }

    static {
        // Order matters only for ties; the earliest matching word in the sentence wins.
        VERBS.put(HostCommand.STOP, new String[]{
            "стоп", "стой$", "хватит", "отмена", "отмени", "отбой", "прекрати", "остановись", "досить", "зупинись", "stop", "cancel", "abort", "halt$", "enough",
            "stopp", "abbrechen", "arrete", "annule", "para$", "detente", "cancela", "pare$", "przestan", "anuluj", "やめて", "止まれ", "中止", "停", "取消"
        });
        VERBS.put(HostCommand.FIND, new String[]{
            "найди", "найти", "ищи$", "поищи", "отыщи", "где$", "покажи где", "знайди", "шукай", "де$", "find", "search", "locate", "where", "finde", "suche", "wo$",
            "wo ist", "trouve", "cherche", "ou est", "ou$", "encuentra", "busca", "donde", "encontre", "procure", "onde", "znajdz", "szukaj", "gdzie", "探して", "さがして",
            "探せ", "どこ", "找", "寻找", "哪里"
        });
        VERBS.put(HostCommand.FETCH, new String[]{
            "принеси", "достань", "подай", "притащи", "дай$", "неси$", "принести", "принеси", "bring", "fetch", "get me", "give me", "hol$", "hole$", "bring mir",
            "apporte", "ramene", "donne", "trae", "traeme", "traiga", "dame", "traga", "pega$", "przynies", "podaj", "持ってきて", "取って", "拿", "取来"
        });
        VERBS.put(HostCommand.OVERLOAD, new String[]{
            "ударь", "удар", "бей$", "бахни", "жахни", "шарахни", "разряд", "молни", "атакуй", "атак", "убей", "убить", "стреляй", "шмальни", "вдарь", "вдар", "бий$",
            "завали", "вали$", "мочи$", "zap", "shock", "strike", "hit$", "attack", "shoot", "kill", "blast", "fry$", "lightning", "smite", "schlag", "blitz", "angriff",
            "greif", "tote", "frappe", "foudre", "attaque", "tue$", "golpea", "ataca", "rayo", "mata$", "ataque", "raio", "mate$", "uderz", "atakuj", "zabij", "攻撃", "撃て",
            "雷", "攻击", "打", "电"
        });
        VERBS.put(HostCommand.VIRUS, new String[]{
            "вирус", "зарази", "заража", "отрав", "инфици", "інфіку", "virus", "infect", "poison", "plague", "vergift", "infiz", "empoisonne", "infecte", "envenena",
            "infecta", "zaraz", "otruj", "wirus", "ウイルス", "感染", "病毒", "毒"
        });
        VERBS.put(HostCommand.BLIND, new String[]{
            "ослеп", "слепи", "оптик", "зрени", "осліп", "blind", "optics", "dazzle", "blende", "aveugle", "ciega", "cega", "oslep", "目潰し", "盲", "致盲"
        });
        VERBS.put(HostCommand.IMPULSE, new String[]{
            "отбрось", "оттолкни", "толкни", "швырни", "брось", "кинь", "откинь", "подбрось", "притяни", "тяни", "тащи", "відкинь", "штовхни", "throw", "push", "yeet",
            "toss", "launch", "fling", "pull", "yank", "knock", "wirf", "stoss", "zieh", "lance", "pousse", "tire$", "lanza", "empuja", "jala$", "tira$", "empurra", "puxa",
            "rzuc", "pchnij", "przyciagnij", "投げ", "飛ばせ", "引き寄せ", "扔", "推", "拉"
        });
        VERBS.put(HostCommand.PUPPET, new String[]{
            "обрати", "перехват", "взломай", "взлом", "подчини", "захвати", "перемани", "зламай", "підкори", "hack", "puppet", "control", "convert", "hijack", "takeover",
            "turn$", "ubernimm", "kontrollier", "pirate", "controle", "hackea", "controla", "hackeia", "zhakuj", "przejmij", "ハック", "操れ", "黑", "控制"
        });
        VERBS.put(HostCommand.DECOY, new String[]{
            "приманк", "обманк", "отвлеки", "голограмм", "двойник", "відволічи", "decoy", "distract", "hologram", "bait", "lure", "koder", "ablenk", "leurre", "distrais",
            "senuelo", "distrae", "isca", "distraia", "wabik", "odwroc", "おとり", "囮", "诱饵", "分身"
        });
        VERBS.put(HostCommand.TIMEWARP, new String[]{
            "замедли", "замедл", "рапид", "слоумо", "останови время", "сповільн", "slowmo", "slow", "bullet time", "zeitlupe", "verlangsam", "ralenti", "ralentis",
            "camara lenta", "ralentiza", "devagar", "spowolnij", "スロー", "慢动作", "减速"
        });
        VERBS.put(HostCommand.SIPHON, new String[]{
            "высоси", "выпей", "вытяни", "осуши", "перекач", "сифон", "висмокч", "drain", "siphon", "leech", "suck", "saug", "draine", "aspire", "drena", "absorbe",
            "drene", "wyssij", "吸収", "吸い取", "吸取"
        });
        VERBS.put(HostCommand.SWAP, new String[]{
            "поменяй", "поменяйся", "рокиров", "местами", "поміняй", "swap", "switch places", "trade places", "tausch", "echange", "intercambia", "cambia",
            "troca", "zamien", "入れ替え", "交换"
        });
        VERBS.put(HostCommand.TURRET, new String[]{
            "турел", "пушк", "сторож", "турель", "turret", "sentry", "geschutz", "tourelle", "torreta", "torre$", "wiezyczk", "タレット", "炮塔"
        });
        VERBS.put(HostCommand.SHIELD, new String[]{
            "щит", "барьер", "защит", "прикрой", "бар'єр", "захист", "shield", "barrier", "protect", "guard me", "schild", "schutz", "bouclier", "protege",
            "escudo", "tarcz", "oslon", "シールド", "盾", "护盾"
        });
        VERBS.put(HostCommand.GRAVITY, new String[]{
            "подними", "невесом", "гравитац", "левитац", "підніми", "lift", "levitate", "gravity", "float them", "hebe", "schwebe", "souleve", "levita", "eleva",
            "podnies", "浮かせ", "浮空", "重力"
        });
        VERBS.put(HostCommand.REWIND, new String[]{
            "откат", "откати", "отмотай", "перемотай", "верни меня", "назад во времени", "відкат", "rewind", "recall", "undo", "go back", "zuruckspul", "rembobine",
            "rebobina", "cofnij", "巻き戻", "倒带", "回溯"
        });
        VERBS.put(HostCommand.REPAIR, new String[]{
            "почини", "вылечи", "вылеч", "лечи", "подлатай", "полатай", "хил$", "хилл", "хильни", "лікуй", "полікуй", "repair", "heal", "fix me", "patch me", "heile",
            "reparier", "soigne", "repare", "cura$", "curame", "sana$", "repara", "cure$", "ulecz", "napraw", "治して", "回復", "治疗", "修复"
        });
        VERBS.put(HostCommand.BLINK, new String[]{
            "скачок", "скачи", "телепорт", "тп$", "прыгни", "блинк", "перенеси", "стрибок", "blink", "teleport", "tp$", "warp", "jump me", "springe", "teletransporta",
            "teleporta", "teleportuj", "テレポート", "ワープ", "传送", "闪现"
        });
        VERBS.put(HostCommand.SCAN, new String[]{
            "скан", "просканируй", "сканируй", "просвети", "разведай", "кто рядом", "кто тут", "проскануй", "scan", "detect", "radar", "who is near", "whos near",
            "scanne", "ortung", "detecte", "escanea", "detecta", "escaneie", "skanuj", "スキャン", "探知", "扫描", "侦测"
        });
        VERBS.put(HostCommand.MAGNET, new String[]{
            "собери", "подбери", "магнит", "пылесос", "лут$", "залутай", "збери", "підбери", "collect", "gather", "pick up", "loot", "vacuum", "magnet", "sammle", "aufheb",
            "ramasse", "aimant", "recoge", "iman", "recolha", "junte", "apanha", "zbierz", "podnies", "集めて", "拾って", "收集", "捡"
        });
        VERBS.put(HostCommand.HARVEST, new String[]{
            "сруби", "руби$", "вырубь", "вырубай", "наруби", "добудь", "добывай", "выкопай", "жилу", "жила$", "зрубай", "рубай", "видобудь", "chop", "fell$", "harvest",
            "mine$", "mine the", "mine this", "mine that", "vein", "cut down", "lumber", "falle", "hacke ab", "abbau", "coupe", "abats", "tala$", "corta", "mina$",
            "corte$", "derruba", "minera", "zetnij", "scinaj", "wydobadz", "伐採", "切って", "採掘", "砍", "伐", "采矿"
        });
        VERBS.put(HostCommand.DIG, new String[]{
            "копай", "прокопай", "вырой", "рой$", "копни", "тоннел", "туннел", "лестниц", "ступен", "спуск", "прорий", "тунел", "сходи$", "dig", "tunnel", "mine forward",
            "stair", "steps", "grab$", "graben", "treppe", "creuse", "escalier", "cava$", "excava", "tunel", "escalera", "escada", "kop$", "kopaj", "wykop", "schod", "掘って",
            "掘れ", "トンネル", "階段", "挖", "隧道", "楼梯"
        });
        VERBS.put(HostCommand.BUILD, new String[]{
            "построй", "строй", "поставь стен", "возведи", "мост", "стен", "перекрой", "побудуй", "збудуй", "міст", "стін", "build", "bridge", "wall", "baue", "brucke",
            "mauer", "construis", "pont$", "mur$", "construye", "puente", "muro", "construa", "ponte", "parede", "zbuduj", "most$", "scian", "建てて", "橋", "壁", "建", "桥",
            "墙"
        });
        VERBS.put(HostCommand.TOGGLE, new String[]{
            "открой", "закрой", "дерни", "нажми", "переключи", "рычаг", "двер", "люк", "відкрий", "закрий", "натисни", "open", "close", "toggle", "flip", "press", "door",
            "lever", "offne", "schliess", "druck", "ouvre", "ferme", "appuie", "abre", "cierra", "pulsa", "abra", "feche", "otworz", "zamknij", "開けて", "閉めて", "押して", "开",
            "关", "按"
        });
        VERBS.put(HostCommand.LIGHT, new String[]{
            "свет", "освети", "посвети", "факел", "темно", "світло", "освіти", "light", "torch", "illuminate", "dark", "licht", "fackel", "beleuchte", "lumiere", "torche",
            "eclaire", "luz$", "antorcha", "ilumina", "tocha", "swiatlo", "pochodni", "oswietl", "明かり", "照らして", "松明", "照亮", "火把", "灯"
        });
        VERBS.put(HostCommand.QUIET, new String[]{
            "молчи", "тихо", "замолчи", "заткнись", "помолчи", "тишина", "не мешай", "отстань", "мовчи", "тихіше", "говори", "можешь говорить", "quiet", "silence",
            "shut up", "hush", "mute", "talk$", "speak", "unmute", "ruhe", "sei still", "rede$", "sprich", "tais", "parle", "calla", "silencio", "habla", "cala$", "quieta",
            "fale$", "cicho", "zamilcz", "mow$", "静かに", "黙って", "話して", "安静", "闭嘴", "说话"
        });
        VERBS.put(HostCommand.STATUS, new String[]{
            "статус", "нагрев", "температур", "как чип", "перегрев", "состояни", "стан$", "status", "heat$", "temperature", "how hot", "zustand", "hitze", "temperatur",
            "etat", "chaleur", "estado", "calor", "stan$", "状態", "温度", "状态"
        });
        VERBS.put(HostCommand.HELP, new String[]{
            "помощь", "что ты умеешь", "что умеешь", "что можешь", "команды", "умеешь", "справка", "допомога", "вмієш", "help", "commands", "what can you", "hilfe",
            "befehle", "aide$", "commandes", "ayuda", "comandos", "ajuda", "pomoc", "komendy", "ヘルプ", "何ができる", "帮助", "命令"
        });
        FLAGS.put(FLAG_DOWN, new String[]{
            "лестниц", "ступен", "спуск", "вниз", "сход", "stair", "steps", "down", "treppe", "runter", "escalier", "bas$", "escalera", "abajo", "escada", "baixo", "schod",
            "dol$", "階段", "下", "楼梯"
        });
        FLAGS.put(FLAG_WALL, new String[]{
            "стен", "стін", "заслон", "укрыти", "wall", "cover", "mauer", "mur$", "muro", "parede", "scian", "壁", "墙"
        });
        FLAGS.put(FLAG_PULL, new String[]{
            "притяни", "тяни", "тащи", "ко мне", "сюда", "pull", "yank", "to me", "here", "zieh", "tire$", "jala$", "puxa", "przyciagnij", "引き寄せ", "拉"
        });
        FLAGS.put(FLAG_TALK, new String[]{
            "говори", "можешь говорить", "вернись", "talk$", "speak", "unmute", "rede$", "sprich", "parle", "habla", "fale$", "mow$", "話して", "说话"
        });
        TOPICS.put("thanks", new String[]{
            "спасибо", "спс$", "благодарю", "пасиб", "молодец", "умница", "красава", "дякую", "thanks", "thank", "thx$", "good job", "nice$", "well done", "danke", "merci",
            "gracias", "obrigad", "dzieki", "ありがとう", "谢谢"
        });
        TOPICS.put("how", new String[]{
            "как дела", "как ты", "как жизнь", "че как", "як справи", "how are you", "how are", "sup$", "wie geht", "ca va", "que tal", "como estas", "como vai", "tudo bem",
            "jak sie masz", "co tam", "元気", "你好吗"
        });
        TOPICS.put("who", new String[]{
            "кто ты", "ты кто", "что ты такое", "хто ти", "who are you", "what are you", "wer bist", "qui es", "quien eres", "quem e", "kim jestes", "誰", "你是谁"
        });
        TOPICS.put("joke", new String[]{
            "шутк", "анекдот", "пошути", "рассмеши", "жарт", "joke", "funny", "witz", "blague", "chiste", "piada", "zart$", "冗談", "笑话"
        });
        TOPICS.put("love", new String[]{
            "люблю", "обожаю", "ты лучшая", "милая", "кохаю", "love you", "love", "cute", "liebe", "t aime", "te quiero", "te amo", "kocham", "好き", "爱你"
        });
        TOPICS.put("insult", new String[]{
            "дура", "тупая", "глупая", "бесполезн", "бесишь", "ненавижу", "идиот", "дурна", "stupid", "dumb", "useless", "idiot", "hate you", "annoying", "dumm", "blod",
            "stupide", "tonta", "estupida", "burra", "glupia", "バカ", "笨"
        });
        TOPICS.put("bye", new String[]{
            "пока$", "до встречи", "спокойной", "бувай", "bye", "goodbye", "see you", "tschuss", "au revoir", "adios", "tchau", "pa pa", "さようなら", "再见"
        });
        TOPICS.put("hello", new String[]{
            "привет", "здравствуй", "хай$", "ку$", "здарова", "йо$", "привіт", "hello", "hi$", "hey$", "yo$", "hallo", "salut", "bonjour", "hola", "ola$", "oi$", "czesc",
            "hej$", "こんにちは", "やあ", "你好", "嗨"
        });
        for (String word : new String[]{
            "пожалуйста", "плиз", "пж", "пжлст", "please", "pls", "plz", "bitte", "por", "favor", "sil", "te", "plait", "prosze", "можешь", "можно", "могла", "бы", "давай",
            "ну", "а", "и", "ка", "мне", "меня", "нам", "нас", "тут", "там", "вон", "этот", "эту", "это", "этого", "того", "ту", "тот", "на", "в", "во", "к", "ко", "у", "из",
            "с", "для", "по", "до", "блок", "блока", "блоков", "блоки", "штук", "метров", "the", "a", "an", "to", "for", "me", "my", "some", "of", "at", "in", "on", "that",
            "this", "there", "blocks", "block", "can", "you", "could", "would", "will", "is", "are", "it", "какой", "нибудь", "рядом", "ближайший", "ближайшего", "ближайшую",
            "ближайшие", "nearest", "closest", "near", "here", "сюда", "туда", "вперед", "forward", "ahead", "несколько", "немного", "быстро", "срочно", "now", "сейчас",
            "длиной", "длиною", "long", "вниз", "вверх", "down", "up", "его", "ее", "их", "him", "her", "them", "один", "une", "un", "el", "la", "le", "der", "die", "das",
            "mir", "moi", "тебя", "ты", "за", "от"
        }) {
            FILLER.add(word);
        }

        numbers(1, "одну", "одна", "one");
        numbers(2, "два", "две", "пару", "two");
        numbers(3, "три", "three");
        numbers(4, "четыре", "four");
        numbers(5, "пять", "five");
        numbers(6, "шесть", "six");
        numbers(7, "семь", "seven");
        numbers(8, "восемь", "eight");
        numbers(9, "девять", "nine");
        numbers(10, "десять", "ten", "десяток");
        numbers(12, "двенадцать", "twelve");
        numbers(15, "пятнадцать", "fifteen");
        numbers(16, "шестнадцать", "sixteen");
        numbers(20, "двадцать", "twenty");
        numbers(30, "тридцать", "thirty");
        alias("ancient_debris", "обломк", "древн", "уламк", "debris", "schutt", "restos", "destrocos", "pozostalos", "незерит", "netherite", "残骸");
        alias("diamond", "алмаз", "діамант", "diamant", "diamond", "diament", "ダイヤ", "钻石");
        alias("iron", "желез", "залізн", "залізо", "iron", "eisen", "fer ", "hierro", "ferro", "zelaz", "鉄", "铁");
        alias("gold", "золот", "gold", "oro ", "ouro", "zlot", "金", "or ");
        alias("coal", "угол", "уголь", "вугіл", "coal", "kohle", "charbon", "carbon", "carvao", "wegl", "wegiel", "石炭", "煤");
        alias("copper", "медь", "меди", "мідь", "мiдь", "copper", "kupfer", "cuivre", "cobre", "miedz", "銅", "铜");
        alias("emerald", "изумруд", "смарагд", "emerald", "smaragd", "emeraude", "esmeralda", "szmaragd", "エメラルド", "绿宝石");
        alias("redstone", "редстоун", "редстон", "redstone", "レッドストーン", "红石");
        alias("lapis", "лазурит", "lapis", "ラピス", "青金石");
        alias("chest", "сундук", "скрин", "chest", "truhe", "coffre", "cofre", "bau ", "skrzyn", "チェスト", "箱子");
        alias("spawner", "спавнер", "рассадник", "spawner", "spawne", "スポナー", "刷怪");
        alias("log", "дерев", "древес", "бревн", "log", "wood", "tree", "holz", "bois", "madera", "madeira", "drewn", "原木", "木");
        alias("zombie", "зомби", "зомбі", "zombie", "zombi", "ゾンビ", "僵尸");
        alias("skeleton", "скелет", "skeleton", "skelett", "squelette", "esqueleto", "szkielet", "スケルトン", "骷髅");
        alias("creeper", "крипер", "кріпер", "creeper", "クリーパー", "苦力怕");
        alias("spider", "паук", "павук", "spider", "spinne", "araignee", "arana", "aranha", "pajak", "クモ", "蜘蛛");
        alias("witch", "ведьм", "відьм", "witch", "hexe", "sorciere", "bruja", "bruxa", "wiedzm", "魔女", "女巫");
        alias("pillager", "разбойник", "пилладжер", "розбійник", "pillager", "plunderer", "pillard", "saqueador", "rozbojnik");
        alias("slime", "слизн", "слайм", "slime", "schleim", "スライム", "史莱姆");
        alias("blaze", "блейз", "ифрит", "blaze", "lohe", "ブレイズ", "烈焰");
        alias("phantom", "фантом", "phantom", "ファントム", "幻翼");
        alias("drowned", "утопленник", "утоплен", "drowned", "ertrunken", "noye", "ahogado", "afogado", "utopiec");
        alias("iron_golem", "голем", "golem", "ゴーレム", "傀儡");
        alias("villager", "жител", "селян", "мешкан", "villager", "dorfbewohner", "villageois", "aldeano", "aldeao", "osadnik", "村人", "村民");
        alias("cow", "коров", "cow", "kuh", "vache", "vaca", "krow", "牛");
        alias("pig", "свин", "pig", "schwein", "cochon", "cerdo", "porco", "swini", "豚", "猪");
        alias("sheep", "овц", "вівц", "овеч", "sheep", "schaf", "mouton", "oveja", "ovelha", "owc", "羊");
        alias("chicken", "куриц", "куру", "кур ", "chicken", "huhn", "poulet", "gallina", "galinha", "kurcz", "ニワトリ", "鸡");
        alias("horse", "лошад", "коня", "конь", "кінь", "horse", "pferd", "cheval", "caballo", "cavalo", "kon ", "馬", "马");
        alias("enderman", "эндермен", "эндермэн", "ендермен", "enderman", "エンダーマン", "末影人");
        alias("wolf", "волк", "вовк", "wolf", "loup", "lobo", "wilk", "オオカミ", "狼");
        alias("cat", "кот ", "кота", "кіш", "кошк", "cat ", "katze", "chat ", "gato", "kot ", "猫");
        alias("water", "вод", "water", "wasser", "eau ", "agua", "wod", "水");
        alias("lava", "лав", "lava", "溶岩", "岩浆");
    }
}
