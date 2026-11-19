package dev.teamnicks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stores nicknames in config/teamnicknames.json and reloads automatically when the file changes,
 * so a teammate can drop in a shared file without restarting the game.
 */
public final class NickConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("teamnicknames.json");
    private static final long CHECK_INTERVAL_MS = 1000;
    private static final List<String> DEFAULT_PING_MODS = List.of("cosmicpings", "cosmic", "minecoin");

    /** real name (as typed) -> nickname */
    private static final Map<String, String> nicknames = new LinkedHashMap<>();
    private static boolean enabled = true;
    /** How the replacement is shown. {nick} and {name} are substituted. */
    private static String format = "{nick}";
    /** true = only swap names in text drawn by the ping mod; false = swap everywhere. */
    private static boolean pingsOnly = true;
    /** Also swap names in chat (all chat, including ping messages). */
    private static boolean alsoInChat = false;
    /** Text matched against class names / jar file names to recognise the ping mod. */
    private static List<String> pingMods = new ArrayList<>(DEFAULT_PING_MODS);
    /** Logs which mods draw a nickname-matched name, to help set pingMods. */
    private static boolean debug = false;

    private static long lastModified = -1;
    private static long lastCheck = 0;

    private NickConfig() {}

    public static synchronized void load() {
        try {
            if (!Files.exists(FILE)) {
                nicknames.clear();
                nicknames.put("girlscoutalt1", "beltuser1");
                nicknames.put("girlscoutalt2", "beltuser2");
                save();
                return;
            }
            String raw = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            enabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
            format = root.has("format") ? root.get("format").getAsString() : "{nick}";
            pingsOnly = !root.has("pingsOnly") || root.get("pingsOnly").getAsBoolean();
            alsoInChat = root.has("alsoInChat") && root.get("alsoInChat").getAsBoolean();
            debug = root.has("debug") && root.get("debug").getAsBoolean();
            pingMods = new ArrayList<>();
            if (root.has("pingMods")) {
                for (com.google.gson.JsonElement el : root.getAsJsonArray("pingMods")) {
                    String v = el.getAsString().trim().toLowerCase(Locale.ROOT);
                    if (!v.isEmpty()) pingMods.add(v);
                }
            } else {
                pingMods.addAll(DEFAULT_PING_MODS);
            }
            nicknames.clear();
            if (root.has("nicknames")) {
                for (Map.Entry<String, com.google.gson.JsonElement> e : root.getAsJsonObject("nicknames").entrySet()) {
                    String name = e.getKey().trim();
                    String nick = e.getValue().getAsString();
                    if (!name.isEmpty() && !nick.isEmpty()) nicknames.put(name, nick);
                }
            }
            lastModified = Files.getLastModifiedTime(FILE).toMillis();
            TeamNicknames.LOGGER.info("[TeamNicknames] Loaded {} nickname(s)", nicknames.size());
        } catch (Exception e) {
            TeamNicknames.LOGGER.error("[TeamNicknames] Could not read {} - keeping previous nicknames", FILE, e);
            try { lastModified = Files.getLastModifiedTime(FILE).toMillis(); } catch (IOException ignored) {}
        }
        NickReplacer.rebuild();
    }

    public static synchronized void save() {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", enabled);
        root.addProperty("format", format);
        root.addProperty("pingsOnly", pingsOnly);
        root.addProperty("alsoInChat", alsoInChat);
        com.google.gson.JsonArray mods = new com.google.gson.JsonArray();
        pingMods.forEach(mods::add);
        root.add("pingMods", mods);
        root.addProperty("debug", debug);
        JsonObject map = new JsonObject();
        nicknames.forEach(map::addProperty);
        root.add("nicknames", map);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
            lastModified = Files.getLastModifiedTime(FILE).toMillis();
        } catch (IOException e) {
            TeamNicknames.LOGGER.error("[TeamNicknames] Could not save {}", FILE, e);
        }
        NickReplacer.rebuild();
    }

    /** Cheap; called from the render path. Re-reads the file at most once a second if it changed. */
    public static void pollForChanges() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < CHECK_INTERVAL_MS) return;
        lastCheck = now;
        try {
            if (Files.exists(FILE) && Files.getLastModifiedTime(FILE).toMillis() != lastModified) load();
        } catch (IOException ignored) {}
    }

    public static synchronized void put(String name, String nick) {
        remove(name, false);
        nicknames.put(name, nick);
        save();
    }

    public static synchronized boolean remove(String name) {
        return remove(name, true);
    }

    private static boolean remove(String name, boolean persist) {
        String lower = name.toLowerCase(Locale.ROOT);
        boolean removed = nicknames.keySet().removeIf(k -> k.toLowerCase(Locale.ROOT).equals(lower));
        if (removed && persist) save();
        return removed;
    }

    public static synchronized Map<String, String> snapshot() { return new LinkedHashMap<>(nicknames); }
    public static boolean isEnabled() { return enabled; }
    public static synchronized void setEnabled(boolean value) { enabled = value; save(); }
    public static String format() { return format; }
    public static boolean pingsOnly() { return pingsOnly; }
    public static boolean alsoInChat() { return alsoInChat; }
    public static List<String> pingMods() { return pingMods; }
    public static boolean debug() { return debug; }
    public static Path file() { return FILE; }
}
