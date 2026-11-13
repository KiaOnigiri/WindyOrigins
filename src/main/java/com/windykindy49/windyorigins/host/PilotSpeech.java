package com.windykindy49.windyorigins.host;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Hears the requests that are for the body, not for Volta's own tricks: "иди ищи алмазы", "зафарми мне зомби",
 * "go kill that player", "geh nach Hause". Some words always mean the body (go, kill, farm, guard, flee, home).
 * Others are her ordinary requests (dig, find, hit, collect) and mean the body only when she already has it, or
 * when the sentence says so: "go and...", "...yourself".
 */
public final class PilotSpeech {
    /** What was asked of the body. */
    public record Order(String kind, String subject, int number, UUID player, boolean pointed, boolean anyPlayer, double[] coords, float bearing, String what) {
        /** "Go", and not a word about where. */
        public boolean vague() {
            return this.kind.equals("goto") && this.coords == null && Float.isNaN(this.bearing) && this.player == null && !this.pointed && this.subject.isBlank() && this.what.isEmpty();
        }
    }

    private PilotSpeech() {
    }

    private static final String[] GO = {
        "иди$", "идти$", "иди ка", "пойди", "сходи$", "сбегай", "беги$", "бегом", "побегай", "пробегись", "шагай", "топай", "ступай", "отправляйся", "двигай$", "дуй$",
        "марш$", "йди$", "іди$", "піди", "біжи", "побігай", "рушай", "go$", "goto$", "walk", "run$", "head to", "head for", "travel", "move to", "get to", "geh$",
        "gehe$", "lauf$", "laufe$", "renn$", "renne$", "marschier", "ve$", "vete$", "anda$", "camina", "corre$", "va$", "vas$", "marche$", "cours$", "allez$",
        "idz$", "pojdz", "biegnij", "biegaj", "rusz$", "vai$", "ande$", "corra$", "caminhe", "行って", "行け", "歩いて", "走って", "向かって", "去", "跑", "前往", "走"
    };
    private static final String[] SELF = {
        "сама$", "сам$", "самостоятельно", "телом$", "моим телом", "моими руками", "руками$", "вручную", "ножками", "пешком", "своими руками", "yourself", "by hand",
        "on foot", "with my body", "in my body", "manually", "selbst$", "selber", "zu fuss", "tu misma", "a pie$", "a mano$", "toi meme", "a pied$", "a la main",
        "recznie", "pieszo", "voce mesma", "a pe$", "自分で", "体で", "亲自", "自己"
    };
    private static final String[] TAKE = {
        "возьми тело", "возьми управление", "возьми контроль", "возьми руль", "бери тело", "бери управление", "бери контроль", "бери руль", "управляй", "веди$",
        "веди тело", "веди меня", "рули$", "порули", "автопилот", "вселись", "вселяйся", "візьми тіло", "візьми керування", "керуй", "take over", "take control",
        "take the wheel", "take my body", "take the body", "autopilot", "auto pilot", "drive$", "you drive", "pilot$", "ubernimm", "ubernehmen", "steuere",
        "nimm den korper", "nimm meinen korper",
        "toma el control", "toma el cuerpo", "piloto automatico", "conduce$", "prends le controle", "prends le corps", "pilote automatique", "pilote$", "przejmij",
        "steruj", "assuma", "assume o controle", "操縦", "オートパイロット", "体を使って", "接管", "自动驾驶", "控制身体",
        "меня под контроль", "меня под свой контроль", "меня под управление", "тело под контроль", "под контроль тело", "под контроль меня", "возьми меня",
        "контролируй меня", "контролируй тело", "захвати тело", "завладей телом", "управляй мной", "мене під контроль", "тіло під контроль", "контролюй мене",
        "take me over", "control me", "control my body", "take control of me", "possess me", "kontrolliere mich", "ubernimm mich", "controlame", "controla mi cuerpo",
        "controle moi", "prends moi", "kontroluj mnie", "przejmij kontrole", "me controle", "体を操って", "乗っ取って", "控制我", "接管身体"
    };
    private static final String[] RELEASE = {
        "верни тело", "верни управление", "верни контроль", "верни руль", "отдай тело", "отдай управление", "отдай руль", "отпусти", "я сам$", "я сама$",
        "дальше сам", "хватит рулить", "поверни тіло", "відпусти", "give it back", "give back", "give me control", "let go", "release", "i ll drive", "my turn",
        "hands off", "gib zuruck", "lass los", "ich ubernehme", "devuelve", "suelta", "yo conduzco", "rends", "lache", "oddaj", "pusc$", "devolva", "solta$",
        "返して", "離して", "还给我", "松手", "放开"
    };
    private static final String[] KILL = {
        "убей", "убить", "убивай", "прикончи", "добей", "замочи", "уничтожь", "перебей", "истреби", "вбий", "знищ", "kill", "slay", "murder", "finish off",
        "take out", "tote$", "toten$", "tot$", "erledige", "mata$", "matar$", "asesina", "tue$", "tuer$", "elimine", "zabij", "wykoncz", "mate$", "倒して", "殺して",
        "やっつけて", "杀", "干掉", "消灭"
    };
    private static final String[] FARM = {
        "зафарм", "нафарм", "фарми", "фарм$", "пофарм", "охоться", "поохоться", "охоть", "зачисти", "зачищай", "полюй", "farm", "hunt", "grind", "clear out",
        "jage$", "jag$", "jagd", "farme$", "caza$", "cazar$", "farmea", "chasse$", "poluj", "cace$", "狩って", "狩り", "打怪", "刷", "猎杀"
    };
    private static final String[] GUARD = {
        "охраняй", "сторожи", "карауль", "покарауль", "обороняй", "стой на страже", "держи оборону", "вартуй", "охороняй", "stand guard", "keep watch",
        "guard the", "guard this", "guard here", "guard my", "hold this", "hold the", "bewache", "halte wache", "vigila", "monta guardia", "monte la garde",
        "surveille", "pilnuj", "strzez", "vigie$", "monte guarda", "見張って", "放哨", "站岗", "守卫"
    };
    private static final String[] FLEE = {
        "убегай", "убеги", "беги отсюда", "спасайся", "отступай", "отступи", "валим", "сваливай", "драпай", "тікай", "run away", "flee", "retreat", "escape",
        "get out of here", "get away", "flieh", "lauf weg", "ruckzug", "huye", "escapa", "retirada", "fuis$", "enfuis", "sauve toi", "uciekaj", "wycofaj", "fuja$",
        "recue$", "逃げて", "逃げろ", "逃跑", "快跑", "撤退"
    };
    private static final String[] HOME = {
        "домой", "на базу", "к кровати", "к дому", "додому", "до дому", "home", "to base", "to my bed", "nach hause", "heim$", "a casa", "a la base",
        "a la maison", "do domu", "para casa", "家に", "帰って", "回家", "回基地"
    };
    private static final String[] EAT = {
        "поешь", "ешь$", "съешь", "покушай", "поесть", "перекуси", "жри$", "пожри", "поїж", "їж$", "eat$", "eat something", "have a meal", "iss$", "iss was",
        "comer$", "come algo", "mange$", "manger$", "jedz$", "zjedz", "coma$", "食べて", "吃"
    };
    private static final String[] SLEEP = {
        "поспи", "спи$", "ложись спать", "иди спать", "спать$", "засни", "лягай спати", "спи трохи", "sleep", "go to bed", "to bed", "schlaf$", "schlafen",
        "duerme", "a dormir", "dormir$", "dors$", "va dormir", "spij$", "idz spac", "durma", "寝て", "眠って", "睡觉", "去睡"
    };
    private static final String[] AGAIN = {
        "еще раз", "еще$", "повтори", "снова$", "заново", "ще раз", "again", "once more", "repeat", "one more time", "nochmal", "noch einmal", "wiederhole",
        "otra vez", "repite", "de nuevo", "encore$", "repete", "jeszcze raz", "powtorz", "de novo", "repita", "mais uma vez", "もう一回", "もう一度", "再来", "再一次", "重复"
    };
    /** "Carry on": what the heat, or the Host's own hands, broke off. */
    private static final String[] RESUME = {
        "продолж", "доделай", "закончи", "продовж", "continue", "carry on", "resume", "keep going", "finish it", "mach weiter", "weitermachen", "continua", "sigue$",
        "termina", "kontynuuj", "dokoncz", "続けて", "続き", "继续"
    };
    /** Putting things away, and where: chests by hand. */
    private static final String[] STORE = {
        "сложи", "положи", "закинь", "скинь", "выложи", "спрячь$", "склади", "поклади", "store$", "stash", "deposit", "unload", "lagere", "verstau", "guarda$",
        "deposita", "range$", "depose$", "schowaj", "odloz", "guarde$", "しまって", "入れて", "存放", "放进"
    };
    private static final String[] WITHDRAW = {
        "возьми$", "достань", "забери", "вытащи", "принеси", "візьми$", "дістань", "take$", "get$", "grab", "fetch", "withdraw", "bring", "nimm$", "hol$", "hole$",
        "toma$", "coge$", "saca$", "trae$", "prends$", "sors$", "apporte", "wez$", "wyjmij", "przynies", "pegue$", "tire$", "traga$", "取って", "取り出して", "拿", "取出"
    };
    private static final String[] CONTAINER = {
        "сундук", "бочк", "бочонок", "шалкер", "склад", "ящик", "хранилищ", "скрин", "діжк", "chest", "barrel", "shulker", "storage", "storeroom", "crate", "truhe",
        "fass$", "lager$", "cofre", "barril", "almacen", "coffre", "tonneau", "entrepot", "skrzyn", "beczk", "magazyn", "bau$", "deposito", "チェスト", "樽", "倉庫", "箱子", "木桶", "仓库"
    };
    private static final String[] BARREL = {"бочк", "бочонок", "діжк", "barrel", "fass$", "barril", "tonneau", "beczk", "樽", "木桶"};
    private static final String[] SHULKER = {"шалкер", "shulker", "シュルカー", "潜影盒"};
    private static final String[] CHEST = {"сундук", "скрин", "chest", "truhe", "cofre", "coffre", "skrzyn", "bau$", "チェスト", "箱子"};
    private static final String[] STOREROOM = {"склад", "хранилищ", "storage", "storeroom", "warehouse", "lager$", "almacen", "entrepot", "magazyn", "deposito", "倉庫", "仓库"};
    private static final String[] LOOT = {"лут", "добыт", "добыч", "награбленн", "собранн", "здобич", "здобут", "loot", "haul$", "spoils", "takings", "beute", "botin", "butin", "lup$", "saque$", "戦利品", "战利品"};
    private static final String[] LEFT = {"слева", "левый", "левую", "левом", "левой", "левая", "лівий", "ліворуч", "left", "links", "linke", "izquierda", "gauche", "lewo", "lewej", "esquerda", "左"};
    private static final String[] RIGHT = {"справа", "правый", "правую", "правом", "правой", "правая", "праворуч", "right", "rechts", "rechte", "derecha", "droite", "prawo", "prawej", "direita", "右"};
    /** "Deal with them", "killer mode": every enemy there is, players among them only while the Host has players switched on. */
    private static final String[] DEAL = {
        "разберись", "разделайся", "зачисти", "расправься", "розберись", "deal with", "sort them out", "take care of them", "clear them out", "wipe them out",
        "kummere dich", "raum auf", "encargate", "ocupate de", "occupe toi", "regle leur", "zajmij sie", "rozpraw sie", "cuide deles", "acabe com", "片付けて", "始末して", "解决他们", "收拾他们"
    };
    private static final String[] KILLER = {
        "режим убийцы", "режим убийств", "режим убийства", "режим боя", "боевой режим", "режим вбивці", "бойовий режим", "killer mode", "kill mode", "murder mode",
        "combat mode", "berserk", "rampage", "totungsmodus", "kampfmodus", "modo asesino", "modo combate", "mode tueur", "mode combat", "tryb zabojcy", "tryb walki",
        "modo assassino", "キラーモード", "戦闘モード", "杀手模式", "战斗模式"
    };
    private static final java.util.regex.Pattern LABELLED = java.util.regex.Pattern.compile("(?iu)(?<![\\p{L}\\d])(x|y|z|х|у|з|икс|игрек|зет)\\s*[:=]?\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final String[] CROPS = {
        "урожай", "посев", "грядк", "врожай", "crops", "the harvest", "the field", "ernte", "feld$", "cosecha", "cultivos", "recolte", "cultures", "plony",
        "zbiory", "colheita", "plantacao", "作物", "収穫", "庄稼", "农作物"
    };
    private static final String[] RETURN = {
        "вернись", "возвращайся", "вернуться", "обратно", "повертайся", "назад$", "come back", "return", "go back", "get back", "komm zuruck", "kehr zuruck",
        "zuruck$", "vuelve", "regresa", "reviens", "retourne", "wroc$", "wracaj", "volte$", "volta aqui", "retorne", "戻って", "帰ってきて", "回来", "返回"
    };
    private static final String[] CRAFT = {
        "скрафт", "крафт", "смастери", "изготовь", "сооруди", "створи", "змайструй", "craft", "stelle her", "stell her", "fertige", "crafte", "fabrica$", "craftea",
        "fabrique$", "wytworz", "stworz", "fabrique", "crie$", "クラフト", "作って", "合成", "制作"
    };
    /** "Make", which is as often "do": a request to craft only when nothing else in the sentence is hers to do. */
    private static final String[] MAKE = {"сделай", "зроби", "make", "mach$", "mache$", "haz$", "fais$", "zrob$", "faca$", "做"};
    private static final String[] SMELT = {
        "переплав", "расплавь", "плавь$", "выплави", "пожарь", "зажарь", "приготовь", "обожги", "переплави", "засмаж", "smelt", "cook", "roast", "schmelz",
        "brat$", "brate$", "koche", "funde$", "cocina$", "asa$", "fais fondre", "fonds$", "cuis$", "przetop", "upiecz", "usmaz", "derreta", "asse$", "cozinhe",
        "精錬", "焼いて", "烧", "熔炼", "烤"
    };
    private static final String[] BRIDGE = {"мост", "міст$", "моста$", "bridge", "brucke", "puente", "pont$", "most$", "mostu$", "ponte$", "橋", "桥"};
    private static final String[] CLIMB = {
        "столб", "поднимись", "наверх", "заберись", "вылези", "стовп", "піднімись", "pillar", "tower up", "climb up", "go up", "get out", "turm$", "hoch$",
        "pilar$", "sube$", "pilier", "slup$", "wejdz", "suba$", "柱", "登って", "上がって", "往上", "爬上去"
    };
    private static final String[] TORCH = {"факел", "смолоскип", "torch", "fackel", "antorcha", "torche", "pochodni", "tocha", "松明", "火把"};
    private static final String[] PUT = {
        "поставь", "установи", "воткни", "постав$", "place$", "put$", "stell$", "stelle$", "setz", "lege$", "leg$", "pon$", "coloca", "pose$", "mets$", "postaw", "coloque", "置いて", "放", "插"
    };
    private static final String[] SHELTER = {
        "укрыти", "спрячься", "закопайся", "убежищ", "схованк", "сховайся", "shelter", "hide$", "bunker", "dig in", "unterschlupf", "versteck", "refugio",
        "escondete", "abri$", "cache toi", "schron", "ukryj", "abrigo", "esconda", "隠れて", "シェルター", "躲起来", "避难所"
    };
    private static final String[] FOLLOW = {
        "следуй", "иди за", "ходи за", "беги за", "преследуй", "слідуй", "йди за", "follow", "chase", "go after", "folge", "verfolge", "sigue", "persigue",
        "suis$", "poursuis", "idz za", "podazaj", "sledz", "siga$", "persiga", "ついて", "追って", "跟着", "跟随", "追"
    };
    private static final String[] EXPLORE = {
        "исследуй", "разведай", "поисследуй", "погуляй", "прогуляйся", "побегай", "поброди", "поищи вокруг", "досліди", "розвідай", "побігай", "explore", "scout",
        "wander", "roam", "look around", "erkunde", "erforsche", "explora", "explore$", "exploruj", "zwiedzaj", "zbadaj", "探検", "偵察", "探索", "侦察", "逛"
    };
    private static final String[] HILL = {
        "холм", "гор$", "гору$", "горку", "горк", "бугор", "насып", "курган", "возвышен", "пагорб", "hill", "mound", "mountain", "heap", "pile$", "hugel", "berg$",
        "haufen", "colina", "monticulo", "cerro", "colline", "butte", "wzgorz", "pagorek", "gorke", "kopiec", "morro", "丘", "小山", "土堆"
    };
    private static final String[] RAZE = {
        "снеси", "сровняй", "срой$", "разровняй", "убери", "знеси", "level$", "flatten", "raze", "demolish", "remove", "tear down", "ebne", "reiss", "allana",
        "derriba", "rase$", "aplatis", "zrownaj", "wyrownaj", "nivele", "demol", "平らに", "崩して", "铲平", "夷平", "推平", "拆"
    };
    private static final String[] ANY = {
        "что нибудь", "что хочешь", "что угодно", "сама реши", "на свое усмотрение", "развлекайся", "займись чем", "поиграй за меня", "играй за меня",
        "поиграй сама", "що небудь", "що хочеш", "грай за мене", "do something", "do anything", "whatever you want", "anything you want", "your choice",
        "you decide", "play for me", "have fun", "mach irgendwas", "mach was du willst", "spiel fur mich", "haz algo", "lo que quieras", "juega por mi",
        "fais quelque chose", "ce que tu veux", "joue pour moi", "zrob cos", "co chcesz", "graj za mnie", "faca algo", "o que quiser", "jogue por mim",
        "何かして", "好きにして", "代わりに遊んで", "随便", "做点什么", "替我玩"
    };
    private static final String[] FOOD = {
        "еды$", "еду$", "еда$", "пожрать", "мяса$", "мясо$", "жратв", "провизи", "їжу$", "їжі$", "food", "meat", "something to eat", "essen$", "nahrung", "fleisch",
        "comida", "carne", "nourriture", "viande", "jedzeni", "mieso", "食べ物", "食料", "肉", "食物"
    };
    private static final String[] RESOURCES = {"ресурс", "materials", "resources", "rohstoff", "recursos", "ressources", "surowc", "資源", "资源", "材料"};
    private static final String[] HOSTILE = {
        "враг", "монстр", "моб", "ворог", "нечист", "всех$", "все живое", "enemy", "enemies", "monster", "mob$", "mobs$", "hostile", "everything", "everyone",
        "feind", "ennemi", "enemigo", "inimigo", "wrog", "potwor", "敵", "敌", "怪"
    };
    private static final String[] ANIMALS = {
        "животн", "зверей", "зверя", "звер", "скот", "дич", "тварин", "animal", "beast", "livestock", "tier$", "tiere$", "vieh", "animales", "ganado", "animaux",
        "betail", "zwierz", "animais", "gado$", "動物", "动物"
    };
    private static final String[] THAT = {
        "того$", "этого$", "эту$", "ту$", "этот$", "это$", "этой$", "той$", "эта$", "этих$", "тех$", "его$", "ее$", "вон$", "цього$", "цю$", "цієї$", "that$", "this$", "him$", "her$", "it$", "den$", "diesen$", "dieses$",
        "ese$", "esa$", "eso$", "celui", "cette$", "ce$", "tego$", "tamtego", "esse$", "aquele", "あれ", "あいつ", "あの", "その", "この", "那个", "那座", "这个"
    };
    private static final String[] PLAYER = {"игрок", "гравц", "player", "spieler", "jugador", "joueur", "gracz", "jogador", "プレイヤー", "玩家"};
    private static final String[] THERE = {"туда$", "туди$", "there$", "over there", "dorthin", "alli$", "alla$", "la bas", "tam$", "ali$", "あそこ", "那里", "那边"};
    private static final String[] FORWARD = {
        "вперед", "прямо", "forward", "ahead", "straight", "vorwarts", "geradeaus", "adelante", "recto$", "tout droit", "en avant", "naprzod", "prosto",
        "em frente", "前へ", "まっすぐ", "往前", "向前"
    };
    private static final String[] NORTH = {"север", "північ", "north", "norden", "norte", "nord$", "polnoc", "北"};
    private static final String[] SOUTH = {"юг$", "юга$", "південь", "south", "suden", "sur$", "sud$", "poludni", "sul$", "南"};
    private static final String[] EAST = {"восток", "схід$", "east", "osten", "est$", "wschod", "leste", "東", "东"};
    private static final String[] WEST = {"запад", "захід", "west", "westen", "oeste", "ouest", "zachod", "西"};
    private static final String[] LITTLE = {
        "мне$", "мені$", "нам$", "за$", "в$", "на$", "к$", "до$", "и$", "а$", "там$", "тут$", "здесь", "пожалуйста", "плиз", "потом", "затем", "и потом",
        "for$", "me$", "us$", "to$", "the$", "a$", "an$", "and$", "then$", "at$", "in$", "of$", "some$", "please", "fur$", "mich$", "mir$", "uns$", "zu$", "zum$",
        "zur$", "und$", "nach$", "bitte", "para$", "mi$", "por$", "favor$", "y$", "el$", "la$", "los$", "las$", "un$", "una$", "al$", "pour$", "moi$", "et$", "le$",
        "les$", "des$", "du$", "au$", "dla$", "mnie$", "i$", "do$", "prosze", "mim$", "e$", "o$", "os$", "as$", "um$", "uma$", "ao$", "を", "に", "へ", "の", "的", "给我"
    };
    private static final String[][] CONTROL = {GO, SELF, TAKE, RELEASE, KILL, FARM, GUARD, FLEE, HOME, EAT, SLEEP, AGAIN, RESUME, DEAL, KILLER, RETURN, CRAFT, MAKE, SMELT, BRIDGE, CLIMB, PUT, SHELTER, FOLLOW, EXPLORE, RAZE, THAT, THERE, FORWARD, NORTH, SOUTH, EAST, WEST, LITTLE};
    private static final Pattern COORDS = Pattern.compile("(-?\\d{1,7})[\\s,;]+(-?\\d{1,4})[\\s,;]+(-?\\d{1,7})|(-?\\d{1,7})[\\s,;]+(-?\\d{1,7})");

    private static boolean has(String text, String[] tokens, String[] words) {
        return at(text, tokens, words) >= 0;
    }

    /** Where in the sentence one of the words stands, typos not forgiven: these words take the body. */
    private static int at(String text, String[] tokens, String[] words) {
        int first = -1;

        for (String word : words) {
            VoltaChat.guessed = false;
            int index = VoltaChat.find(text, tokens, word);
            if (index >= 0 && !VoltaChat.guessed && (first < 0 || index < first)) {
                first = index;
            }
        }

        return first;
    }

    /** One of the words this class listens for: "eat" is an order even with a player called Eater about. */
    private static boolean known(String token) {
        String[] one = new String[]{token};

        for (String[][] lists : new String[][][]{CONTROL, {HILL, ANY, FOOD, RESOURCES, HOSTILE, ANIMALS, PLAYER}}) {
            for (String[] words : lists) {
                for (String word : words) {
                    if (word.indexOf(' ') < 0 && !VoltaChat.cjk(word)) {
                        VoltaChat.guessed = false;
                        if (VoltaChat.find(token, one, word) == 0 && !VoltaChat.guessed) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /** Nothing in the sentence is one of her ordinary requests: "make a pickaxe", not "make a shield" (hers) or "make light". */
    private static boolean plain(String said, ServerPlayerEntity speaker, Collection<ServerPlayerEntity> online) {
        VoltaChat.Request request = VoltaChat.parse(said, speaker, online);
        return request == null || request.command() == HostCommand.CHAT;
    }

    private static boolean verb(String text, String[] tokens, HostCommand command) {
        String[] words = VoltaChat.VERBS.get(command);
        return words != null && has(text, tokens, words);
    }

    private static final Pattern THEN = Pattern.compile(
        "(?iu)[\\s,;.]+(?:а\\s+|и\\s+|and\\s+|und\\s+|y\\s+|et\\s+|i\\s+|e\\s+)?(?:потом|затем|после\\s+этого|после\\s+чего|тоді|потім|then|afterwards|after\\s+that|danach|dann|luego|despu[eé]s|puis|ensuite|potem|nast[eę]pnie|depois|em\\s+seguida)[\\s,]+|(?:それから|その後|そして|然后|接着|之后|再)"
    );
    private static final Pattern AND = Pattern.compile("(?iu)\\s+(?:и|і|та|and|und|y|et|i|e)\\s+|[,;]\\s+");

    /**
     * Everything asked of the body in one sentence, in order: "найди деревню, потом убей там всех". Null when the
     * sentence is not for the body at all. What follows the first request is heard as said to her with the body.
     */
    public static List<Order> parseAll(String said, ServerPlayerEntity speaker, Collection<ServerPlayerEntity> online, boolean held) {
        List<String> units = new ArrayList<>();

        for (String part : THEN.split(said.trim())) {
            if (part.isBlank()) {
                continue;
            }

            // "Kill the zombies and go home": two requests only if each half is one by itself.
            String[] halves = AND.split(part.trim());
            boolean each = halves.length >= 2 && halves.length <= 4;

            for (int i = 0; each && i < halves.length; i++) {
                each = !halves[i].isBlank() && parse(halves[i], speaker, online, true) != null;
            }

            if (each) {
                units.addAll(List.of(halves));
            } else {
                units.add(part);
            }
        }

        if (units.size() <= 1) {
            Order whole = parse(said, speaker, online, held);
            return whole == null ? null : List.of(whole);
        }

        // One part that can only mean the body makes the whole sentence about the body.
        boolean body = held;

        for (String unit : units) {
            body |= parse(unit, speaker, online, false) != null;
        }

        if (!body) {
            return null;
        }

        List<Order> out = new ArrayList<>();

        for (String unit : units) {
            Order one = parse(unit, speaker, online, true);
            if (one != null) {
                out.add(one);
            }
        }

        // "Take the body and go there": the taking is leave for the job, and with the job done the body goes back.
        if (out.size() > 1) {
            out.removeIf(order -> order.kind().equals("take"));
        }

        return out.isEmpty() ? null : out;
    }

    /** The request for the body in what was said, or null when it is not one. */
    public static Order parse(String said, ServerPlayerEntity speaker, Collection<ServerPlayerEntity> online, boolean held) {
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

                int score = VoltaChat.nameScore(word, other.getGameProfile().getName());
                if (score < 90 && (VoltaChat.knownWord(VoltaChat.normalize(word)) || known(VoltaChat.normalize(word)))) {
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

        String text = VoltaChat.normalize(kept.toString());
        String[] tokens = text.isEmpty() ? new String[0] : text.split(" ");
        if (tokens.length == 0 && player == null) {
            return null;
        }

        boolean release = has(text, tokens, RELEASE);
        if (release) {
            return held ? new Order("release", "", -1, null, false, false, null, Float.NaN, "") : null;
        }

        int goAt = at(text, tokens, GO);
        // Ukrainian "сходи" is a staircase; as "go" it leads the sentence.
        if (goAt > 0 && goAt < tokens.length && tokens[goAt].equals("сходи")) {
            goAt = -1;
        }

        boolean go = goAt >= 0;
        boolean self = has(text, tokens, SELF);
        boolean take = has(text, tokens, TAKE);
        // The body is hers only when it is asked for by name, here or in the message before: "take the body and...",
        // or with the body already in her hands. Without that the same words are for her ordinary abilities. The one
        // thing that needs no asking twice is going on with a job she was given the body for and had to break off.
        boolean body = held || take || has(text, tokens, RESUME) && tokens.length <= 3 && Pilot.paused(speaker);
        if (!body) {
            return null;
        }

        boolean strike = verb(text, tokens, HostCommand.OVERLOAD);
        boolean mine = verb(text, tokens, HostCommand.DIG) || verb(text, tokens, HostCommand.HARVEST);
        boolean find = verb(text, tokens, HostCommand.FIND);
        boolean collect = verb(text, tokens, HostCommand.MAGNET);
        boolean pointed = has(text, tokens, THAT);
        boolean anyPlayer = has(text, tokens, PLAYER);
        boolean food = has(text, tokens, FOOD);
        boolean resources = has(text, tokens, RESOURCES);
        boolean crops = has(text, tokens, CROPS);
        String what = crops ? "crops" : (food ? "food" : (resources ? "resources" : (has(text, tokens, ANIMALS) ? "animals" : (has(text, tokens, HOSTILE) ? "hostile" : ""))));
        // "Make four torches": a number of things to make is a request to make them, whatever else the words might mean.
        boolean counted = java.util.Arrays.stream(tokens).anyMatch(token -> token.matches("\\d+") || VoltaChat.NUMBERS.containsKey(token));
        String kind;
        if (has(text, tokens, FLEE)) {
            kind = "flee";
        } else if (has(text, tokens, RESUME) && tokens.length <= 3) {
            kind = "resume";
        } else if (has(text, tokens, AGAIN) && tokens.length <= 3) {
            kind = "again";
        } else if (has(text, tokens, RETURN) && (body || tokens.length <= 3)) {
            kind = "return";
        } else if (has(text, tokens, SLEEP)) {
            kind = "sleep";
        } else if (has(text, tokens, SHELTER)) {
            kind = "shelter";
        } else if (has(text, tokens, BRIDGE) && body) {
            kind = "bridge";
        } else if (has(text, tokens, STORE) || has(text, tokens, PUT) && (has(text, tokens, CONTAINER) || has(text, tokens, LOOT))) {
            kind = "stash";
        } else if (has(text, tokens, WITHDRAW) && !take && body && !has(text, tokens, CRAFT)) {
            kind = "withdraw";
        } else if (go && has(text, tokens, STOREROOM)) {
            kind = "goto";
        } else if (has(text, tokens, TORCH) && has(text, tokens, PUT)) {
            kind = "torch";
        } else if (has(text, tokens, SMELT)) {
            kind = "smelt";
        } else if (has(text, tokens, CRAFT) || has(text, tokens, MAKE) && !has(text, tokens, ANY) && (body || counted || has(text, tokens, TORCH) || plain(said, speaker, online))) {
            kind = "craft";
        } else if (has(text, tokens, CLIMB) && !mine) {
            kind = "pillar";
        } else if (crops && (mine || collect)) {
            kind = "mine";
        } else if (has(text, tokens, EAT) && !food) {
            kind = "eat";
        } else if (has(text, tokens, GUARD)) {
            kind = "guard";
        } else if (has(text, tokens, KILL) || has(text, tokens, FARM) || has(text, tokens, DEAL) || has(text, tokens, KILLER) || body && strike) {
            kind = has(text, tokens, FARM) || has(text, tokens, KILLER) ? "farm" : "hunt";
            if (has(text, tokens, KILLER)) {
                what = "killer";
            } else if (has(text, tokens, DEAL) && what.isEmpty()) {
                what = "hostile";
            }
        } else if (has(text, tokens, FOLLOW) && (player != null || pointed || body)) {
            kind = "follow";
        } else if (has(text, tokens, HILL) && (mine || has(text, tokens, RAZE))) {
            kind = "clear";
        } else if (body && mine) {
            kind = "mine";
        } else if (body && collect) {
            kind = "collect";
        } else if (has(text, tokens, HOME) && (body || tokens.length <= 3)) {
            kind = "home";
        } else if (body && find || has(text, tokens, EXPLORE)) {
            kind = "find";
        } else if (has(text, tokens, ANY) && (body || tokens.length <= 5)) {
            kind = "auto";
        } else if (go) {
            kind = "goto";
        } else if (take) {
            kind = "take";
        } else {
            return null;
        }

        // What is left once every word that only steers the sentence is out: the thing itself.
        List<String> rest = new ArrayList<>();
        int number = -1;

        outer:
        for (String token : tokens) {
            if (token.matches("\\d{1,4}")) {
                number = number < 0 ? Integer.parseInt(token) : number;
                continue;
            }

            if (VoltaChat.NUMBERS.containsKey(token)) {
                number = number < 0 ? VoltaChat.NUMBERS.get(token) : number;
                continue;
            }

            if (VoltaChat.FILLER.contains(token)) {
                continue;
            }

            String piece = token;
            boolean dense = VoltaChat.cjk(token);
            String[] one = new String[]{token};

            for (String[] words : CONTROL) {
                for (String word : words) {
                    if (dense && VoltaChat.cjk(word)) {
                        piece = piece.replace(word, "");
                    } else if (!dense && word.indexOf(' ') < 0 && VoltaChat.find(token, one, word) == 0 && !VoltaChat.guessed) {
                        continue outer;
                    }
                }
            }

            for (String[] words : VoltaChat.VERBS.values()) {
                for (String word : words) {
                    if (dense && VoltaChat.cjk(word)) {
                        piece = piece.replace(word, "");
                    } else if (!dense && word.indexOf(' ') < 0 && !VoltaChat.cjk(word) && isOrder(words, kind) && VoltaChat.find(token, one, word) == 0 && !VoltaChat.guessed) {
                        continue outer;
                    }
                }
            }

            if (!piece.isEmpty()) {
                rest.add(piece);
            }
        }

        if (kind.equals("stash") || kind.equals("withdraw")) {
            // Which chest and which side are not part of the thing's name.
            what = (has(text, tokens, LOOT) ? "loot " : "") + (has(text, tokens, LEFT) ? "left " : "") + (has(text, tokens, RIGHT) ? "right " : "")
                + (has(text, tokens, BARREL) ? "barrel " : "") + (has(text, tokens, SHULKER) ? "shulker " : "") + (has(text, tokens, CHEST) ? "chest " : "");
            String[][] steering = {STORE, WITHDRAW, CONTAINER, LOOT, LEFT, RIGHT, PUT, SHULKER};
            rest.removeIf(piece -> {
                for (String[] words : steering) {
                    for (String word : words) {
                        VoltaChat.guessed = false;
                        if (word.indexOf(' ') < 0 && VoltaChat.find(piece, new String[]{piece}, word) == 0 && !VoltaChat.guessed) {
                            return true;
                        }
                    }
                }

                return false;
            });
        } else if ((kind.equals("find") || kind.equals("goto")) && has(text, tokens, STOREROOM)) {
            what = "storage";
        }

        if (take) {
            // "Take the body" and the like are how it was asked, not what was asked for.
            for (String phrase : TAKE) {
                VoltaChat.guessed = false;
                if (phrase.indexOf(' ') > 0 && VoltaChat.find(text, tokens, phrase) >= 0 && !VoltaChat.guessed) {
                    for (String word : phrase.split(" ")) {
                        rest.remove(word);
                    }
                }
            }

            rest.removeIf(piece -> java.util.Set.of("меня", "мной", "мене", "me", "my", "mich", "возьми", "бери", "візьми", "take", "nimm", "toma", "prends", "wez", "pegue").contains(piece));
        }

        String subject = String.join(" ", rest);
        double[] coords = null;
        float bearing = Float.NaN;
        if (kind.equals("goto") || kind.equals("find")) {
            // "x 40 z 33 y 100": each number with its letter, in any order, the height or not.
            Matcher labelled = LABELLED.matcher(said);
            double[] by = {Double.NaN, Double.NaN, Double.NaN};

            while (labelled.find()) {
                String letter = labelled.group(1).toLowerCase(java.util.Locale.ROOT);
                int axis = letter.equals("x") || letter.equals("х") || letter.equals("икс") ? 0 : (letter.equals("y") || letter.equals("у") || letter.equals("игрек") ? 1 : 2);
                by[axis] = Double.parseDouble(labelled.group(2));
            }

            Matcher numbers = COORDS.matcher(said);
            if (!Double.isNaN(by[0]) && !Double.isNaN(by[2])) {
                kind = "goto";
                coords = by;
            } else if (kind.equals("goto") && numbers.find()) {
                coords = numbers.group(1) != null
                    ? new double[]{Double.parseDouble(numbers.group(1)), Double.parseDouble(numbers.group(2)), Double.parseDouble(numbers.group(3))}
                    : new double[]{Double.parseDouble(numbers.group(4)), Double.NaN, Double.parseDouble(numbers.group(5))};
            }

            if (has(text, tokens, NORTH)) {
                bearing = 180.0F;
            } else if (has(text, tokens, SOUTH)) {
                bearing = 0.0F;
            } else if (has(text, tokens, EAST)) {
                bearing = -90.0F;
            } else if (has(text, tokens, WEST)) {
                bearing = 90.0F;
            } else if (has(text, tokens, FORWARD)) {
                bearing = 1000.0F;
            }
        }

        return new Order(kind, subject, number, player, pointed || has(text, tokens, THERE), anyPlayer, coords, bearing, what);
    }

    /** Is this list of verbs the one the order itself was given with? Other verb-like words may be the name of a thing. */
    private static boolean isOrder(String[] words, String kind) {
        HostCommand[] mine = switch (kind) {
            case "mine", "clear" -> new HostCommand[]{HostCommand.DIG, HostCommand.HARVEST};
            case "find", "goto" -> new HostCommand[]{HostCommand.FIND};
            case "collect" -> new HostCommand[]{HostCommand.MAGNET};
            case "hunt", "farm", "guard" -> new HostCommand[]{HostCommand.OVERLOAD};
            default -> new HostCommand[0];
        };

        for (HostCommand command : mine) {
            if (VoltaChat.VERBS.get(command) == words) {
                return true;
            }
        }

        return false;
    }
}
