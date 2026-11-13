package com.windykindy49.windyorigins.host;

import java.util.ArrayList;
import java.util.Arrays;
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
    static final Map<HostCommand, String[]> VERBS = new LinkedHashMap<>();
    private static final Map<String, String[]> TOPICS = new LinkedHashMap<>();
    private static final Map<Integer, String[]> FLAGS = new LinkedHashMap<>();
    public static final Map<String, String> ALIASES = new LinkedHashMap<>();
    static final Map<String, Integer> NUMBERS = new HashMap<>();
    static final Set<String> FILLER = new HashSet<>();
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

        // "стооой", "naaaidi": a letter held down is still one letter.
        return out.toString().trim().replaceAll("\\s+", " ").replaceAll("(\\p{L})\\1{2,}", "$1");
    }

    private static final String LAYOUT_LATIN = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`";
    private static final String LAYOUT_CYRILLIC = "йцукенгшщзхъфывапролджэячсмитьбюё";

    /**
     * What the message would have been on the other keyboard layout: "djknf yfqlb fkvfps" was meant
     * as "вольта найди алмазы", and "мщдеф аштв" as "volta find". Null when the text has no letters to turn.
     */
    public static String swapLayout(String text) {
        int latin = 0;
        int cyrillic = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if (c >= 'a' && c <= 'z') {
                latin++;
            } else if (c >= 'а' && c <= 'я' || c == 'ё') {
                cyrillic++;
            }
        }

        if (latin == 0 && cyrillic == 0) {
            return null;
        }

        boolean toCyrillic = latin >= cyrillic;
        String from = toCyrillic ? LAYOUT_LATIN : LAYOUT_CYRILLIC;
        String to = toCyrillic ? LAYOUT_CYRILLIC : LAYOUT_LATIN;
        StringBuilder out = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            int at = from.indexOf(c);
            // Digits, spaces and underscores are the same key on both layouts.
            out.append(at >= 0 && !(toCyrillic && (c == ',' || c == '.') && (i + 1 >= text.length() || text.charAt(i + 1) == ' ')) ? to.charAt(at) : c);
        }

        return out.toString();
    }

    /** Edit distance in which two neighbouring letters typed the wrong way round count as one slip. */
    public static int distance(String a, String b) {
        int[] before = new int[b.length() + 1];
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
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    cur[j] = Math.min(cur[j], before[j - 2] + 1);
                }
            }

            int[] swap = before;
            before = prev;
            prev = cur;
            cur = swap;
        }

        return prev[b.length()];
    }

    private static boolean ascii(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7F) {
                return false;
            }
        }

        return true;
    }

    private static boolean cyrillic(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0400 && c <= 0x04FF) {
                return true;
            }
        }

        return false;
    }

    /** Irons out the ways people spell Russian in Latin letters: naydi, naidi, najdi are one word. */
    private static String sound(String latin) {
        return latin.replace("shch", "sch").replace("kh", "h").replace("ts", "c").replace("tz", "c").replace('w', 'v').replace('y', 'i').replace('j', 'i').replace("ii", "i");
    }

    private static final Map<String, String> LATIN_OF = new HashMap<>();

    /** The keyword as someone without a Cyrillic keyboard would type it. */
    private static String latinOf(String keyword) {
        return LATIN_OF.computeIfAbsent(keyword, word -> sound(translit(word)));
    }

    static boolean cjk(String text) {
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
    /** Set by {@link #tokenMatches}: the last match was a guess at a misspelling, not the word itself. */
    static boolean guessed;

    private static boolean tokenMatches(String token, String keyword) {
        guessed = false;
        boolean exact = keyword.endsWith("$");
        if (exact) {
            keyword = keyword.substring(0, keyword.length() - 1);
        }

        if (ascii(token) && cyrillic(keyword)) {
            // "naidi almazy": Russian in Latin letters. Short words are left alone and short stems take
            // only an ending, or half of English would turn into Russian orders ("brother" is not "брось").
            String latin = latinOf(keyword);
            String heard = sound(token);
            if (latin.length() < 4) {
                return false;
            } else if (exact) {
                return heard.equals(latin);
            } else if (heard.startsWith(latin)) {
                return latin.length() >= 6 || heard.length() <= latin.length() + 2;
            } else {
                guessed = true;
                return latin.length() >= 6
                    && heard.length() >= latin.length() - 1
                    && heard.charAt(0) == latin.charAt(0)
                    && distance(heard.substring(0, Math.min(heard.length(), latin.length())), latin) <= 1;
            }
        }

        if (exact || keyword.length() <= 3) {
            return token.equals(keyword);
        }

        if (token.startsWith(keyword)) {
            return true;
        }

        if (keyword.length() >= 5 && token.length() >= keyword.length() - 1) {
            guessed = true;
            String head = token.substring(0, Math.min(token.length(), keyword.length()));
            return head.charAt(0) == keyword.charAt(0) && distance(head, keyword) <= (keyword.length() >= 8 ? 2 : 1);
        }

        return false;
    }

    /** Index of the first token that carries the keyword, or -1. Phrases and CJK match on the whole text. */
    static int find(String text, String[] tokens, String keyword) {
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

                // In a short message her name can sit anywhere: "найди, вольта, алмазы".
                if (opening || i == words.length - 1 || words.length <= 8) {
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
    static int nameScore(String word, String name) {
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

    static boolean knownWord(String token) {
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
        boolean unsure = false;

        for (Map.Entry<HostCommand, String[]> entry : VERBS.entrySet()) {
            for (String word : entry.getValue()) {
                guessed = false;
                int index = find(text, tokens, word);
                boolean guess = guessed;
                // Earliest word wins; on the same word, the real thing beats a guessed typo, then the longer match.
                if (index >= 0 && (index < at || index == at && (unsure && !guess || unsure == guess && word.length() > length))) {
                    command = entry.getKey();
                    at = index;
                    length = word.length();
                    unsure = guess;
                }
            }
        }

        Request players = playersSwitch(text, tokens, command, player);
        if (players != null) {
            return players;
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

        {
            // "покопай", "постреляй", "насобирай": the same verbs under a Russian prefix. They also win over a
            // word further on that merely looks like an order: in "накопай светокамень" the order is to dig.
            int before = command == null ? tokens.length : Math.min(at, tokens.length);
            search:
            for (int i = 0; i < before; i++) {
                String token = tokens[i];
                if (!cyrillic(token)) {
                    continue;
                }

                for (String prefix : PREFIXES) {
                    if (token.length() < prefix.length() + 3 || !token.startsWith(prefix)) {
                        continue;
                    }

                    String[] rest = new String[]{token.substring(prefix.length())};

                    for (Map.Entry<HostCommand, String[]> entry : VERBS.entrySet()) {
                        for (String word : entry.getValue()) {
                            if (word.indexOf(' ') < 0 && !cjk(word) && find(rest[0], rest, word) == 0) {
                                command = entry.getKey();
                                at = i;
                                break search;
                            }
                        }
                    }
                }
            }
        }

        if (command == null) {
            // "мне нужен уголь", "i need coal": wanting a thing is asking for it to be brought.
            for (String word : WANTS) {
                int index = find(text, tokens, word);
                if (index >= 0) {
                    command = HostCommand.FETCH;
                    at = index;
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
        // These take the name of a thing, and a thing may be called like an order: a torch ("факел") is not "light!".
        boolean names = command == HostCommand.DIG || command == HostCommand.HARVEST || command == HostCommand.MAGNET || command == HostCommand.FETCH;

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.matches("\\d{1,3}")) {
                number = Integer.parseInt(token);
            } else if (NUMBERS.containsKey(token)) {
                int value = NUMBERS.get(token);
                // "двадцать пять", "twenty five".
                number = number >= 20 && number % 10 == 0 && value < 10 && i > 0 && NUMBERS.containsKey(tokens[i - 1]) ? number + value : value;
            } else if (cjk(token)) {
                // Written without spaces, the order is stuck to what it is about: "挖石头", "剣を拾って".
                String[] verbs = VERBS.get(command);

                for (String word : verbs == null ? new String[0] : verbs) {
                    if (cjk(word)) {
                        token = token.replace(word, "");
                    }
                }

                if (!token.isEmpty()) {
                    subject.add(token);
                }
            } else if (i != at && !FILLER.contains(token) && !(names ? carriesVerbOf(token, command) : carriesVerb(token))) {
                subject.add(token);
            }
        }

        return new Request(command, String.join(" ", subject), number, player, flags, null);
    }

    // "Players" where the language has a plural of its own, and where it does not (then the word alone decides nothing).
    private static final String[] PLAYERS_MANY = new String[]{
        "игроков$", "игроки$", "игрокам$", "игроками$", "людей$", "гравців$", "гравці$", "гравцям$", "players$", "people$", "jugadores$", "joueurs$", "jogadores$",
        "graczy$", "gracze$"
    };
    private static final String[] PLAYERS_ANY = new String[]{"spieler", "プレイヤー", "玩家"};
    private static final String[] PVP_WORD = new String[]{"пвп$", "pvp$"};
    private static final String[] SWITCH_NO = new String[]{
        "не$", "ні$", "нельзя", "хватит", "перестань", "щади", "пощади", "береги", "выкл", "отключи", "вимкни", "dont", "don$", "not$", "never", "no$", "stop", "spare",
        "leave", "off$", "nicht", "kein", "verschone", "lass", "aus$", "nunca", "deja", "apaga", "desactiva", "ne$", "pas$", "epargne", "laisse", "desactive", "nao$", "poupe",
        "deixa", "desliga", "nie$", "oszczedz", "zostaw", "wylacz", "ない", "するな", "やめ", "オフ", "不", "别", "勿", "关"
    };
    private static final String[] SWITCH_YES = new String[]{
        "вкл", "включи", "можно", "разреша", "снова", "увімкни", "on$", "allow", "may$", "again", "erlaub", "wieder", "activa", "permite", "puedes", "active$",
        "autorise", "peux", "ativa", "pode$", "wlacz", "pozwol", "mozesz", "オン", "していい", "开", "可以"
    };

    private static boolean any(String text, String[] tokens, String[] words) {
        for (String word : words) {
            if (find(text, tokens, word) >= 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * "Не бей игроков", "players are off limits", "бей игроков", "pvp off": not a request to do something
     * now but a standing order about other players. Number 0 is "leave them alone", 1 is "they are fair game",
     * -1 is "the other way round".
     */
    private static Request playersSwitch(String text, String[] tokens, HostCommand command, UUID named) {
        boolean word = any(text, tokens, PVP_WORD);
        boolean many = any(text, tokens, PLAYERS_MANY);
        if (!word && !many && !any(text, tokens, PLAYERS_ANY)) {
            return null;
        }

        boolean no = any(text, tokens, SWITCH_NO);
        boolean yes = any(text, tokens, SWITCH_YES);
        if (no || yes || word) {
            return new Request(HostCommand.PVP, "", no ? 0 : (yes ? 1 : -1), null, 0, null);
        }

        // "Бей игроков", in the plural and at nobody in particular, is permission; "ударь игрока" is a strike.
        return many && named == null && command != null && command.targeted ? new Request(HostCommand.PVP, "", 1, null, 0, null) : null;
    }

    // Words that go with digging and say nothing about what to dig.
    private static final String[] NOT_A_THING = new String[]{
        "блок", "метр", "тоннел", "туннел", "тунел", "проход", "прохід", "шахт", "лестниц", "ступен", "сход", "вперед", "прямо", "глубин", "длин", "туда", "тут$",
        "здесь", "там$", "это$", "этот", "эту$", "эти$", "все$", "всю$", "весь$", "немного", "чуть", "block", "meter", "metre", "tunnel", "shaft", "stair", "steps",
        "forward", "ahead", "straight", "long$", "deep$", "here", "there", "this", "that", "these", "those", "all$", "it$", "some$", "bloc", "bloque", "bloco", "blok",
        "metr", "tunel", "schacht", "treppe", "geradeaus", "vorwarts", "escalier", "puits", "avant$", "escalera", "pozo$", "adelante", "escada", "poco$", "frente",
        "szyb", "schod", "prosto", "naprzod", "ブロック", "メートル", "トンネル", "階段", "隧道", "楼梯",
        // "сруби это дерево", "выбери жилу": the tree or the vein under the crosshair, not a block by that name.
        // "добудь мне золото": for whom is not what.
        "мне$", "мені$", "мени$", "нам$", "me$", "us$", "mir$", "uns$", "moi$", "nous$", "mnie$", "nam$", "mim$", "пожалуйста", "плиз", "please", "bitte",
        "дерев", "жил", "tree", "vein", "baum", "ader$", "arbol", "veta$", "arbre", "filon", "arvore", "veio$", "drzew", "zyle", "zyla"
    };

    // Words that go with picking things up and name nothing: "подбери все предметы", "pick up everything here".
    private static final String[] NOT_LOOT = new String[]{
        "все$", "всю$", "весь$", "усе$", "всi$", "всі$", "предмет", "вещ", "реч", "лут", "дроп", "шмот", "барахл", "добыч", "тут$", "здесь", "там$", "это$", "эти$",
        "этот", "эту$", "мне$", "мені$", "мени$", "нам$", "пожалуйста", "плиз", "all$", "everything", "every$", "item", "thing", "stuff", "loot", "drop", "up$",
        "it$", "them$", "me$", "us$", "here", "there", "that$", "those", "these", "this$", "please", "alles", "alle$", "allen$", "sachen", "gegenstand", "auf$",
        "mir$", "uns$", "bitte", "hier", "tout$", "tous$", "toutes", "toute$", "objet", "chose", "moi$", "ici$", "todo$", "todos$", "todas$", "toda$", "objeto",
        "cosa", "aqui", "tudo", "itens", "coisa", "mim$", "wszystk", "przedmiot", "rzecz", "mnie$", "tutaj", "全部", "すべて", "全て", "アイテム", "を", "物品",
        "所有", "东西", "起来", "起", "的"
    };
    private static final String[] OFF_THE_GROUND = new String[]{
        " с земли", " с пола", " з землі", " з підлоги", " from the ground", " off the ground", " from the floor", " off the floor", " vom boden", " du sol",
        " par terre", " del suelo", " do chao", " z ziemi", " z podlogi"
    };

    /** What is left of the subject of a picking-up request once "all", "items" and "from the ground" are out: the name of a thing, or nothing. */
    public static String loot(String subject) {
        if (subject == null || subject.isBlank()) {
            return "";
        }

        String text = " " + normalize(subject);

        for (String phrase : OFF_THE_GROUND) {
            text = text.replace(phrase, " ");
        }

        StringBuilder out = new StringBuilder();

        outer:
        for (String token : text.trim().split(" ")) {
            if (token.isEmpty()) {
                continue;
            }

            if (cjk(token)) {
                // Written without spaces, the verb and "all" are stuck to the name: "捡剑", "アイテムを全部拾って".
                for (String word : VERBS.get(HostCommand.MAGNET)) {
                    if (cjk(word)) {
                        token = token.replace(word, "");
                    }
                }

                for (String word : NOT_LOOT) {
                    if (cjk(word)) {
                        token = token.replace(word, "");
                    }
                }

                if (token.isEmpty()) {
                    continue;
                }
            } else {
                String[] one = new String[]{token};

                for (String word : NOT_LOOT) {
                    if (!cjk(word) && find(token, one, word) >= 0) {
                        continue outer;
                    }
                }
            }

            out.append(out.length() > 0 ? " " : "").append(token);
        }

        return out.toString();
    }

    /** What is left of the subject of a digging request once the directions and measures are out: the name of a block, or nothing. */
    public static String thing(String subject) {
        if (subject == null || subject.isBlank()) {
            return "";
        }

        StringBuilder out = new StringBuilder();

        outer:
        for (String token : normalize(subject).split(" ")) {
            if (token.isEmpty()) {
                continue;
            }

            String[] one = new String[]{token};

            for (String word : NOT_A_THING) {
                if (find(token, one, word) >= 0) {
                    continue outer;
                }
            }

            for (String[] words : FLAGS.values()) {
                for (String word : words) {
                    if (word.indexOf(' ') < 0 && find(token, one, word) >= 0) {
                        continue outer;
                    }
                }
            }

            out.append(out.length() > 0 ? " " : "").append(token);
        }

        return out.toString();
    }

    private static boolean carriesVerbOf(String token, HostCommand command) {
        String[] one = new String[]{token};
        String[] words = VERBS.get(command);

        for (String word : words == null ? new String[0] : words) {
            if (word.indexOf(' ') < 0 && !cjk(word) && find(token, one, word) == 0 && !guessed) {
                return true;
            }
        }

        return false;
    }

    private static boolean carriesVerb(String token) {
        String[] one = new String[]{token};

        for (String[] words : VERBS.values()) {
            for (String word : words) {
                if (word.indexOf(' ') < 0 && !cjk(word) && find(token, one, word) == 0 && !guessed) {
                    // A noun one slip away from some order ("espadas", "escada") is still the noun: only a sure match counts.
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

        if (ascii(text)) {
            String heard = sound(text);

            for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
                String key = entry.getKey();
                if (cyrillic(key) && key.trim().length() >= 4 && heard.contains(" " + latinOf(key.trim()))) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    private static final String[] PREFIXES = new String[]{"пере", "раз", "рас", "под", "при", "про", "по", "от", "вы", "за", "на", "из", "ис", "до", "об", "с", "у"};
    private static final String[] WANTS = new String[]{
        "нужен", "нужна", "нужно", "нужны", "хочу", "надо$", "потрібен", "потрібна", "потрібно", "need", "want", "brauche", "besoin", "necesito", "preciso", "potrzebuje", "欲しい", "需要"
    };

    private static String[] more(HostCommand command, String... words) {
        String[] old = VERBS.get(command);
        String[] all = Arrays.copyOf(old, old.length + words.length);
        System.arraycopy(words, 0, all, old.length, words.length);
        VERBS.put(command, all);
        return all;
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
        // Other ways people actually say it.
        more(HostCommand.STOP, "прекращай", "отставить", "не надо", "хорош$", "стопэ", "остановк", "тормози", "перестань", "забей", "nevermind", "never mind", "lass", "basta");
        more(HostCommand.FIND, "найд", "разыщи", "сыщи", "поиск", "ищу", "ищем", "ищи мне", "есть ли", "покажи", "подскажи где", "куда делся", "gde$", "look for", "show me", "is there", "any$");
        more(HostCommand.FETCH, "принес", "достан", "забери", "вытащи", "вынь", "возьми$", "возьми мне", "взять$", "дай мне", "неси мне", "подкинь мне", "take", "hand me", "pass me", "gib mir", "prends", "dame", "me da", "daj");
        more(
            HostCommand.OVERLOAD, "врежь", "вмажь", "долбани", "шандарахни", "жги$", "жгани", "пальни", "стрельни", "грохни", "замочи", "прикончи", "добей", "уничтож",
            "разнеси", "поджарь", "тресни", "вреж", "ебни", "ебани", "въеби", "ушатай", "вальни", "гаси$", "фас$", "destroy", "nuke", "bonk", "smack", "electrocute", "zapp"
        );
        more(HostCommand.VIRUS, "заразу", "яд$", "травани", "потрави", "чума", "чуму");
        more(HostCommand.BLIND, "глаза", "ослепл", "засвети", "выключи ему свет");
        more(HostCommand.IMPULSE, "пни$", "пинок", "пинка", "отшвырни", "запусти", "выкинь", "скинь", "сбрось", "отпихни", "вышвырни", "kick", "punt", "shove", "hurl");
        more(HostCommand.PUPPET, "возьми под контроль", "взять под контроль", "возьми под управление", "візьми під контроль", "марионет", "зомбируй", "перевербуй", "под контроль", "приручи", "завербуй", "натрави", "сделай своим", "mind control", "enslave");
        more(HostCommand.DECOY, "приман", "отвлек", "замани", "ложная цель", "fake me");
        more(HostCommand.TIMEWARP, "замедлен", "стоп время", "заморозь время", "время стоп", "слоу", "slow down", "freeze time");
        more(HostCommand.SIPHON, "высос", "вампир", "укради здоров", "забери хп", "отними здоров", "сифонь", "покушай", "lifesteal", "steal health");
        more(HostCommand.SWAP, "свап", "махнись", "обмен", "поменя", "поменяться", "рокир", "switch with");
        more(HostCommand.TURRET, "охраня", "страж", "поставь пушку", "караул", "guard this", "defend");
        more(HostCommand.SHIELD, "защищ", "укрой", "закрой меня", "прикрывай", "броню", "спаси$", "спасай", "cover me");
        more(HostCommand.GRAVITY, "взлет", "подвесь", "в воздух", "подкинь всех", "подбрось всех", "вверх всех", "juggle");
        more(HostCommand.REWIND, "верни назад", "вернись назад", "отмотк", "назад$", "отмени смерть", "вернуть назад", "back in time");
        more(HostCommand.REPAIR, "подлечи", "полечи", "хилни", "отхиль", "хп$", "здоровь", "аптечк", "лечени", "исцели", "залечи", "подхиль", "heal me", "hp$", "medic");
        more(HostCommand.BLINK, "прыжок", "рывок", "дэш", "деш$", "перемести", "телепортир", "скакни", "блинкни", "перепрыгни", "туда$", "dash", "hop$", "leap");
        more(HostCommand.SCAN, "радар", "сонар", "осмотрись", "осмотри", "что вокруг", "кто вокруг", "враги рядом", "есть кто", "пинг", "проверь", "чекни", "check", "ping", "sonar");
        more(HostCommand.MAGNET, "подбирай", "собир", "всоси", "лутай", "залут", "притяни лут", "пылесось", "hoover", "grab loot");
        more(HostCommand.HARVEST, "срубай", "сруб", "нарубай", "спили", "добыч", "выдолби", "сними жилу", "вали дерево", "дерево руби", "заготовь", "lumberjack", "timber");
        more(HostCommand.DIG, "копа", "проход", "шахт", "пробей", "проруби", "прокладывай", "бури$", "пробури", "вниз копай", "drill", "bore$", "shaft");
        more(HostCommand.HARVEST, "сломай", "ломай", "разбей", "разломай", "снеси$", "розбий", "break$", "smash", "zerstore", "zerbrich", "casse$", "detruis", "rompe$", "destruye", "quebra", "destrua", "rozbij", "zniszcz", "壊して", "破坏");
        VERBS.put(HostCommand.PVP, new String[]{"пвп$", "pvp$"});
        more(HostCommand.BUILD, "построи", "мостик", "стенк", "перекрыт", "заделай", "замуруй", "проложи", "закрой проход", "забаррикадир", "construct", "barricade");
        more(HostCommand.TOGGLE, "откро", "закро", "ворот", "калитк", "кнопк", "щелкни", "активируй", "жми$", "отвори", "затвори", "дерни рычаг", "switch on", "unlock", "push the");
        more(HostCommand.LIGHT, "подсвети", "темнот", "ничего не вижу", "не видно", "фонар", "ламп", "освещ", "ночное зрение", "включи свет", "поставь факел", "cant see", "too dark", "lamp");
        more(HostCommand.QUIET, "тише", "умолкни", "тсс", "цыц", "не болтай", "помолч", "хватит болтать", "be quiet", "stfu", "sei leise");
        more(HostCommand.STATUS, "перегрелась", "сколько градус", "как нагрев", "темп$", "ты как", "жива", "чип как", "how are your", "overheating");
        more(HostCommand.HELP, "хелп", "помоги", "список команд", "инструкц", "что ты можешь", "чего умеешь", "как тобой", "как пользоваться", "how do i", "what do you do");
        FLAGS.put(FLAG_DOWN, new String[]{
            "лестниц", "ступен", "спуск", "вниз", "сход", "stair", "steps", "down", "treppe", "runter", "escalier", "bas$", "escalera", "abajo", "escada", "baixo", "schod",
            "dol$", "階段", "下", "楼梯"
        });
        FLAGS.put(FLAG_WALL, new String[]{
            "стен", "стін", "заслон", "укрыти", "wall", "cover", "mauer", "mur$", "muro", "parede", "scian", "壁", "墙"
        });
        FLAGS.put(FLAG_PULL, new String[]{
            "притяни", "тяни", "тащи", "ко мне", "сюда", "притягни", "тягни", "до мене", "сюди", "pull", "yank", "to me", "here", "zieh", "tire$", "attire", "jala$", "atrae",
            "puxa", "puxe", "przyciagnij", "引き寄せ", "拉"
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

        numbers(1, "одну", "одна", "одного", "one", "eins", "uno", "jeden", "um$");
        numbers(2, "два", "две", "двух", "пару", "two", "zwei", "dos", "deux", "dwa", "dois");
        numbers(3, "три", "трех", "тройку", "three", "drei", "tres", "trois", "trzy");
        numbers(4, "четыре", "четырех", "чотири", "four", "vier", "cuatro", "quatre", "cztery", "quatro");
        numbers(5, "пять", "пяти", "пяток", "five", "funf", "cinco", "cinq", "piec");
        numbers(6, "шесть", "шести", "шість", "six", "sechs", "seis", "szesc");
        numbers(7, "семь", "семи", "сім", "seven", "sieben", "siete", "sept", "siedem", "sete");
        numbers(8, "восемь", "восьми", "вісім", "eight", "acht", "ocho", "huit", "osiem", "oito");
        numbers(9, "девять", "девяти", "nine", "neun", "nueve", "neuf", "dziewiec", "nove");
        numbers(10, "десять", "десяти", "ten", "десяток", "zehn", "diez", "dix", "dziesiec", "dez");
        numbers(11, "одиннадцать", "eleven");
        numbers(12, "двенадцать", "дюжину", "twelve", "dozen");
        numbers(13, "тринадцать", "thirteen");
        numbers(14, "четырнадцать", "fourteen");
        numbers(15, "пятнадцать", "пятнадцати", "fifteen");
        numbers(16, "шестнадцать", "sixteen");
        numbers(17, "семнадцать", "seventeen");
        numbers(18, "восемнадцать", "eighteen");
        numbers(19, "девятнадцать", "nineteen");
        numbers(20, "двадцать", "двадцати", "twenty", "zwanzig", "veinte", "vingt", "dwadziescia", "vinte");
        numbers(30, "тридцать", "тридцати", "thirty", "dreissig", "treinta", "trente", "trinta");
        numbers(40, "сорок", "сорока", "forty");
        numbers(50, "пятьдесят", "полсотни", "fifty");
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
