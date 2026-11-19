package dev.teamnicks;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Does the actual name -> nickname swap on text that is about to be drawn. */
public final class NickReplacer {
    /** Text styled with this insertion is never rewritten (used for the /nicks command's own output). */
    public static final String RAW_MARKER = "teamnicknames:raw";

    private static final int CACHE_LIMIT = 4096;
    private static final Map<String, String> cache = new ConcurrentHashMap<>();
    private static volatile Pattern pattern = null;
    private static volatile Map<String, String> lookup = Map.of();

    /** Per-class answer to "does this class belong to the ping mod?" */
    private static final Map<Class<?>, Boolean> pingClassCache = new ConcurrentHashMap<>();
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static long lastDebugLog = 0;

    private static final Object STOP = new Object();

    private NickReplacer() {}

    static void rebuild() {
        Map<String, String> snapshot = NickConfig.snapshot();
        cache.clear();
        pingClassCache.clear();
        if (snapshot.isEmpty()) {
            pattern = null;
            lookup = Map.of();
            return;
        }
        Map<String, String> newLookup = new HashMap<>();
        List<String> names = new ArrayList<>(snapshot.keySet());
        // Longest first so "alt10" wins over "alt1".
        names.sort(Comparator.comparingInt(String::length).reversed());
        StringBuilder alternation = new StringBuilder();
        String fmt = NickConfig.format();
        for (String name : names) {
            if (alternation.length() > 0) alternation.append('|');
            alternation.append(Pattern.quote(name));
            String shown = fmt.replace("{nick}", snapshot.get(name)).replace("{name}", name);
            newLookup.put(name.toLowerCase(Locale.ROOT), shown);
        }
        lookup = newLookup;
        // Whole-name matches only: "alt1" must not hit "dr4nkalt1" or "alt12".
        pattern = Pattern.compile("(?<![A-Za-z0-9_])(?:" + alternation + ")(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
    }

    private static boolean active() {
        NickConfig.pollForChanges();
        return NickConfig.isEnabled() && pattern != null;
    }

    public static String replace(String s) {
        Pattern p = pattern;
        if (p == null || s == null || s.isEmpty()) return s;
        String cached = cache.get(s);
        if (cached != null) return cached;

        Matcher m = p.matcher(s);
        String result;
        if (!m.find()) {
            result = s;
        } else {
            StringBuilder sb = new StringBuilder(s.length() + 16);
            Map<String, String> lk = lookup;
            do {
                String nick = lk.getOrDefault(m.group().toLowerCase(Locale.ROOT), m.group());
                m.appendReplacement(sb, Matcher.quoteReplacement(nick));
            } while (m.find());
            m.appendTail(sb);
            result = sb.toString();
        }
        if (cache.size() > CACHE_LIMIT) cache.clear();
        cache.put(s, result);
        return result;
    }

    // ------------------------------------------------------------------ who is drawing?

    /**
     * Only true when the text is being drawn by the ping mod (or chat, if enabled).
     * Nametags, scoreboard and tab list are drawn by Minecraft itself, so they keep real names.
     * Only runs once we already know the text contains a nicknamed player, so it is cheap.
     */
    private static boolean allowedHere() {
        if (!NickConfig.pingsOnly()) return true;
        boolean chat = NickConfig.alsoInChat();
        boolean allowed = WALKER.walk(frames -> frames.anyMatch(f -> {
            Class<?> c = f.getDeclaringClass();
            if (chat && c == ChatComponent.class) return true;
            return pingClassCache.computeIfAbsent(c, NickReplacer::isPingModClass);
        }));
        if (NickConfig.debug()) debugLog(allowed);
        return allowed;
    }

    private static boolean isPingModClass(Class<?> c) {
        List<String> needles = NickConfig.pingMods();
        if (needles.isEmpty()) return false;
        String className = c.getName().toLowerCase(Locale.ROOT);
        if (className.startsWith("dev.teamnicks.")) return false;
        for (String n : needles) if (className.contains(n)) return true;
        String jar = jarName(c);
        if (jar != null) for (String n : needles) if (jar.contains(n)) return true;
        return false;
    }

    /** File name (not full path) of the jar a class came from, lower-case; null if unknown. */
    private static String jarName(Class<?> c) {
        try {
            CodeSource cs = c.getProtectionDomain().getCodeSource();
            if (cs == null) return null;
            URL url = cs.getLocation();
            if (url == null) return null;
            String path = url.getPath();
            int bang = path.indexOf("!/");
            if (bang >= 0) path = path.substring(0, bang);
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            return path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void debugLog(boolean allowed) {
        long now = System.currentTimeMillis();
        if (now - lastDebugLog < 3000) return;
        lastDebugLog = now;
        Set<String> callers = WALKER.walk(frames -> {
            Set<String> out = new LinkedHashSet<>();
            frames.map(f -> f.getDeclaringClass())
                    .filter(c -> !c.getName().startsWith("java.") && !c.getName().startsWith("dev.teamnicks."))
                    .limit(12)
                    .forEach(c -> out.add(c.getName() + " [" + jarName(c) + "]"));
            return out;
        });
        TeamNicknames.LOGGER.info("[TeamNicknames debug] swap={} drawn by: {}", allowed, callers);
    }

    // ------------------------------------------------------------------ text rewriting

    private static boolean isRaw(Style style) {
        return RAW_MARKER.equals(style.getInsertion());
    }

    /** Returns the same object when nothing needs changing, so untouched text costs almost nothing. */
    public static FormattedText apply(FormattedText text) {
        if (text == null || !active()) return text;

        Optional<Object> hit = text.visit((style, part) -> {
            if (!isRaw(style) && !replace(part).equals(part)) return Optional.of(STOP);
            return Optional.empty();
        }, Style.EMPTY);
        if (hit.isEmpty() || !allowedHere()) return text;

        MutableComponent out = Component.empty();
        text.visit((style, part) -> {
            out.append(Component.literal(isRaw(style) ? part : replace(part)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /** Plain-string version, used for width measuring of text the ping mod draws. */
    public static String applyPlain(String s) {
        if (s == null || !active()) return s;
        String r = replace(s);
        if (r.equals(s) || !allowedHere()) return s;
        return r;
    }
}
