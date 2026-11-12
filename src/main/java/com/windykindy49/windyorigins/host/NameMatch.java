package com.windykindy49.windyorigins.host;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds the things a spoken name can mean: "алмазную кирку", "diamond pickaxes", "Diamantspitzhacke",
 * "钻石镐". The names come from the game's own translations (the client has them, the server does not),
 * so every item and block of every installed mod is known under the name the player sees.
 *
 * A name is <em>exact</em> when the words said cover the whole name ("алмазную кирку" for "Алмазная кирка")
 * and <em>partial</em> when they cover part of it ("кирку" for every pickaxe).
 */
public final class NameMatch {
    /** One name of one thing, already cut into words. */
    public record Name(String id, String text, String[] words, boolean dense) {
    }

    private record Hit(String id, double score, String text, int words) {
    }

    private NameMatch() {
    }

    public static Name name(String id, String shown) {
        String text = normalize(shown);
        return new Name(id, text, words(text), dense(text));
    }

    /** Lower case, no accents, no punctuation. "Ё" and "Е", "é" and "e" are the same letter here. */
    public static String normalize(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        StringBuilder taken = new StringBuilder(lower.length() + 8);

        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            // Only alphabets are taken apart: kana and hangul must stay whole.
            if (c >= 'À' && c < 'ᄀ') {
                taken.append(Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD));
            } else {
                taken.append(c);
            }
        }

        String split = taken.toString();
        StringBuilder out = new StringBuilder(split.length());
        boolean gap = true;

        for (int i = 0; i < split.length(); i++) {
            char c = split.charAt(i);
            if (c >= '̀' && c <= 'ͯ') {
                continue;
            }

            if (c == 'ł') {
                c = 'l';
            } else if (c == 'ß') {
                out.append('s');
                c = 's';
            }

            if (Character.isLetterOrDigit(c)) {
                out.append(c);
                gap = false;
            } else if (!gap) {
                out.append(' ');
                gap = true;
            }
        }

        return out.toString().trim();
    }

    /** Words worth comparing: the little ones ("of", "de", "из") only when there is nothing else. */
    private static String[] words(String text) {
        if (text.isEmpty()) {
            return new String[0];
        }

        String[] all = text.split(" ");
        List<String> kept = new ArrayList<>();

        for (String word : all) {
            if (word.length() > 2 || dense(word)) {
                kept.add(word);
            }
        }

        return kept.isEmpty() ? all : kept.toArray(new String[0]);
    }

    private static final java.util.Set<String> LITTLE = java.util.Set.of(
        "the", "some", "any", "and", "for", "from", "with", "please", "der", "die", "das", "den", "dem", "ein", "eine", "einen", "einem", "mir", "bitte", "los", "las",
        "una", "unos", "unas", "del", "por", "favor", "les", "des", "une", "mon", "moi", "plait", "uns", "umas", "dos", "meu", "minha", "mnie", "prosze", "мне",
        "мой", "мою", "мои", "пожалуйста", "плиз", "мені", "мени", "будь", "ласка"
    );

    /** What was said, without the little words that are in no name ("bring <the> diamond pickaxe"). */
    private static String[] asked(String[] words) {
        List<String> kept = new ArrayList<>();

        for (String word : words) {
            if (!LITTLE.contains(word)) {
                kept.add(word);
            }
        }

        return kept.isEmpty() ? words : kept.toArray(new String[0]);
    }

    /** The longest run of characters the two have in common. */
    private static int shared(String a, String b) {
        int best = 0;
        int[] row = new int[b.length() + 1];

        for (int i = 1; i <= a.length(); i++) {
            int diagonal = 0;

            for (int j = 1; j <= b.length(); j++) {
                int above = row[j];
                row[j] = a.charAt(i - 1) == b.charAt(j - 1) ? diagonal + 1 : 0;
                best = Math.max(best, row[j]);
                diagonal = above;
            }
        }

        return best;
    }

    /** Chinese, Japanese, Korean: written without spaces, so compared as a run of characters. */
    private static boolean dense(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '⺀' && c <= '鿿' || c >= '가' && c <= '힯' || c >= '豈' && c <= '﫿') {
                return true;
            }
        }

        return false;
    }

    /**
     * Fills {@code exact} and {@code partial} with ids, best first, without repeats. A thing that is exact under
     * one of its names is not listed as partial as well.
     */
    public static void match(String said, Iterable<Name> names, List<String> exact, List<String> partial, int limit) {
        String query = normalize(said);
        if (query.isEmpty()) {
            return;
        }

        String[] asked = asked(words(query));
        String packed = query.replace(" ", "");
        boolean denseQuery = dense(query);
        Map<String, Hit> whole = new LinkedHashMap<>();
        Map<String, Hit> part = new LinkedHashMap<>();

        for (Name name : names) {
            if (name.text().isEmpty()) {
                continue;
            }

            double score;
            boolean full;
            int found = 1;
            if (name.dense() || denseQuery) {
                String run = name.text().replace(" ", "");
                if (packed.equals(run)) {
                    score = 3.0;
                    full = true;
                } else if (!run.isEmpty() && packed.contains(run)) {
                    // The name sits inside what was said ("拿钻石镐"): the longer the name, the surer.
                    score = 1.0 + Math.min(0.99, run.length() / 100.0);
                    full = true;
                } else {
                    // Part of the name was said ("木板" for any planks), with a verb stuck to it or not.
                    // One character said alone is a word of its own ("剑" for any sword).
                    int shared = shared(packed, run);
                    if (shared < Math.min(2, packed.length())) {
                        continue;
                    }

                    score = (double)shared / (double)run.length();
                    full = false;
                }
            } else {
                String[] have = name.words();
                boolean[] covered = new boolean[have.length];
                double sum = 0.0;
                boolean all = true;
                boolean sure = true;
                int matched = 0;

                for (String word : asked) {
                    double best = 0.0;

                    for (int i = 0; i < have.length; i++) {
                        double like = alike(word, have[i]);
                        if (like >= 0.7) {
                            covered[i] = true;
                        }

                        best = Math.max(best, like);
                    }

                    if (best < 0.7) {
                        // A word that is in no name ("какие-нибудь", "that", "big"): the rest may still name the thing in part.
                        all = false;
                        continue;
                    }

                    // A word found only inside a longer one, or only by forgiving a slip, names the thing in part.
                    sure &= best >= 0.8;
                    sum += best;
                    matched++;
                }

                if (matched == 0) {
                    continue;
                }

                int count = 0;

                for (boolean hit : covered) {
                    count += hit ? 1 : 0;
                }

                full = all && sure && count == have.length;
                found = matched;
                score = sum / (double)asked.length * (0.5 + 0.5 * (double)count / (double)have.length);
            }

            Map<String, Hit> into = full ? whole : part;
            Hit old = into.get(name.id());
            if (old == null || old.score() < score) {
                into.put(name.id(), new Hit(name.id(), score, name.text().replace(" ", ""), found));
            }
        }

        List<Hit> best = new ArrayList<>(whole.values());
        if (denseQuery) {
            // "ダイヤモンドのツルハシ" also contains "ダイヤモンド": the longer name is the one that was meant.
            best.removeIf(hit -> whole.values().stream().anyMatch(other -> other != hit && other.text().length() > hit.text().length() && other.text().contains(hit.text())));
        }

        best.sort(Comparator.comparingDouble(Hit::score).reversed());

        for (Hit hit : best) {
            if (exact.size() < limit) {
                exact.add(hit.id());
            }
        }

        List<Hit> rest = new ArrayList<>(part.values());
        rest.removeIf(hit -> whole.containsKey(hit.id()));
        // Of the things named in part, only those that explain the most of what was said.
        int most = rest.stream().mapToInt(Hit::words).max().orElse(0);
        rest.removeIf(hit -> hit.words() < most);
        rest.sort(Comparator.comparingDouble(Hit::score).reversed());

        for (Hit hit : rest) {
            if (partial.size() < limit) {
                partial.add(hit.id());
            }
        }
    }

    /**
     * How much two words are the same word: 1 for equal, less for the same word in another case or number
     * ("кирку" and "кирка", "досок" and "доски", "pickaxes" and "pickaxe"), for a word inside a compound
     * ("Spitzhacke" in "Diamantspitzhacke") and for a slip of the finger.
     */
    static double alike(String a, String b) {
        if (a.equals(b)) {
            return 1.0;
        }

        int shorter = Math.min(a.length(), b.length());
        int longer = Math.max(a.length(), b.length());
        int same = 0;

        while (same < shorter && a.charAt(same) == b.charAt(same)) {
            same++;
        }

        if (same >= 3 && same >= longer - 3) {
            return 0.85;
        }

        if (shorter >= 4 && (a.contains(b) || b.contains(a))) {
            return 0.75;
        }

        if (shorter >= 5 && same >= 2 && Math.abs(a.length() - b.length()) <= 1 && slips(a, b) <= 1) {
            return 0.7;
        }

        return 0.0;
    }

    /** Letters to change, add, drop or swap to turn one word into the other. */
    private static int slips(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }

        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }

        return d[a.length()][b.length()];
    }
}
