package dev.modlock.core;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.mojang.logging.LogUtils;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;

/**
 * Mod bundles ("pacotes"): a named group of mods unlocked together, e.g. Create + every Create addon
 * installed on the server. Defined in config/modlock/bundles/&lt;id&gt;.json:
 *
 * <pre>
 * {
 *   "name": "Create",                  // display name (REQUIRED)
 *   "icon": "create:cogwheel",         // item shown as icon (optional; default = no icon)
 *   "image": "create.png",             // picture for the selection screen: a PNG next to this file,
 *                                      // or a texture id like "mypack:textures/gui/create.png" (optional)
 *   "description": "Engineering!",     // optional
 *   "mods": ["create"],                // base mods
 *   "include_addons": true,            // + every installed mod with a REQUIRED dependency on a member (recursive)
 *   "include": ["createaddition"],     // extra mods
 *   "exclude": ["create_sa"],          // never members
 *   "classes": ["engineer"],           // only players of one of these classes can use it (empty = everyone)
 *   "closed": true                     // members are denied to anyone outside "classes", whatever else they unlocked
 * }
 * </pre>
 */
public final class ModBundles {
    private static final Logger LOG = LogUtils.getLogger();

    public record Bundle(String id, String name, @Nullable String icon, @Nullable String image,
                         @Nullable Path imageFile, String description, Set<String> members,
                         Set<String> baseMods, Set<String> classes, boolean closed) {
        public boolean restricted() {
            return !classes.isEmpty();
        }
    }

    /** Raw definition, before resolving addons against the installed mods. */
    public record Definition(String name, @Nullable String icon, @Nullable String image, String description,
                             List<String> mods, boolean includeAddons, List<String> include,
                             List<String> exclude, List<String> classes, boolean closed) {}

    private static volatile Map<String, Bundle> bundles = Map.of();
    private static volatile Map<String, List<Bundle>> byMember = Map.of();

    private ModBundles() {}

    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve("modlock").resolve("bundles");
    }

    // ---------------- queries ----------------

    public static Collection<Bundle> all() {
        return bundles.values();
    }

    @Nullable
    public static Bundle get(String id) {
        return bundles.get(id);
    }

    public static List<Bundle> bundlesOf(String modId) {
        return byMember.getOrDefault(modId, List.of());
    }

    /** True if the mod belongs to a closed bundle. */
    public static boolean isClosedMember(String modId) {
        for (Bundle b : bundlesOf(modId)) if (b.closed()) return true;
        return false;
    }

    /** True if the mod belongs to a bundle whose base mods include a locked mod. */
    public static boolean isMemberOfLockedBundle(String modId, Function<String, Boolean> baseLocked) {
        for (Bundle b : bundlesOf(modId)) {
            for (String base : b.baseMods()) {
                if (!base.equals(modId) && baseLocked.apply(base)) return true;
            }
        }
        return false;
    }

    // ---------------- loading ----------------

    /** (Re)loads every bundle file. Safe to call at any time; keeps the old set on fatal errors. */
    public static synchronized void reload() {
        Path dir = directory();
        Map<String, Definition> defs = new LinkedHashMap<>();
        Map<String, Path> files = new HashMap<>();
        try {
            Files.createDirectories(dir);
            writeExample(dir);
            try (Stream<Path> s = Files.list(dir)) {
                for (Path p : s.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList()) {
                    String id = p.getFileName().toString();
                    id = id.substring(0, id.length() - 5).toLowerCase(Locale.ROOT);
                    try (Reader r = Files.newBufferedReader(p)) {
                        defs.put(id, parse(r));
                        files.put(id, p);
                    } catch (Exception e) {
                        LOG.error("[ModLock] Invalid bundle file {}: {}", p, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            LOG.error("[ModLock] Could not read bundles from {}", dir, e);
            return;
        }
        apply(defs, files);
    }

    /** Replaces all bundles (used by reload and by GameTests). */
    public static synchronized void apply(Map<String, Definition> defs, Map<String, Path> files) {
        Map<String, Bundle> out = new LinkedHashMap<>();
        Map<String, List<Bundle>> rev = new HashMap<>();
        Set<String> installed = installedMods();
        for (var e : defs.entrySet()) {
            String id = e.getKey();
            Definition d = e.getValue();
            Set<String> members = resolve(d, installed);
            if (members.isEmpty()) {
                LOG.warn("[ModLock] Bundle '{}' has no installed mods; it will be empty", id);
            }
            Path imageFile = null;
            if (d.image() != null && !d.image().contains(":") && files.containsKey(id)) {
                Path img = files.get(id).resolveSibling(d.image()).normalize();
                if (img.startsWith(directory().normalize()) && Files.isRegularFile(img)) {
                    imageFile = img;
                } else {
                    LOG.warn("[ModLock] Bundle '{}': image '{}' not found next to the bundle file", id, d.image());
                }
            }
            Set<String> bases = new LinkedHashSet<>(d.mods());
            String name = d.name() != null && !d.name().isBlank() ? d.name() : id;
            if (d.closed() && d.classes().isEmpty()) {
                LOG.warn("[ModLock] Bundle '{}' is closed but has no classes: nobody can use its mods", id);
            }
            Bundle b = new Bundle(id, name, d.icon(), d.image(), imageFile, d.description(),
                    Set.copyOf(members), Set.copyOf(bases),
                    Set.copyOf(d.classes().stream().map(c -> c.toLowerCase(Locale.ROOT)).toList()), d.closed());
            out.put(id, b);
            for (String m : members) rev.computeIfAbsent(m, k -> new ArrayList<>()).add(b);
            LOG.info("[ModLock] Bundle '{}' -> {}", id, members);
        }
        bundles = Map.copyOf(out);
        byMember = Map.copyOf(rev);
    }

    /** Base mods + addons (recursive, REQUIRED dependencies only) + include - exclude, installed mods only. */
    static Set<String> resolve(Definition d, Set<String> installed) {
        Set<String> exclude = new HashSet<>(d.exclude());
        Set<String> members = new LinkedHashSet<>();
        for (String m : d.mods()) if (installed.contains(m) && !exclude.contains(m)) members.add(m);
        for (String m : d.include()) if (installed.contains(m) && !exclude.contains(m)) members.add(m);
        if (d.includeAddons()) {
            Map<String, Set<String>> requires = requiredDependencies();
            boolean changed = true;
            int guard = 0;
            while (changed && guard++ < 64) {
                changed = false;
                for (var e : requires.entrySet()) {
                    String mod = e.getKey();
                    if (members.contains(mod) || exclude.contains(mod)) continue;
                    for (String dep : e.getValue()) {
                        // everything depends on minecraft/neoforge: only follow them when they are a base mod
                        if ((dep.equals("minecraft") || dep.equals("neoforge")) && !d.mods().contains(dep)) continue;
                        if (members.contains(dep)) {
                            members.add(mod);
                            changed = true;
                            break;
                        }
                    }
                }
            }
        }
        return members;
    }

    private static Set<String> installedMods() {
        Set<String> set = new HashSet<>();
        for (IModInfo info : ModList.get().getMods()) set.add(info.getModId());
        return set;
    }

    /** mod id -> its REQUIRED dependencies. */
    private static Map<String, Set<String>> requiredDependencies() {
        Map<String, Set<String>> map = new HashMap<>();
        for (IModInfo info : ModList.get().getMods()) {
            Set<String> deps = new HashSet<>();
            for (IModInfo.ModVersion dep : info.getDependencies()) {
                if (dep.getType() != IModInfo.DependencyType.REQUIRED) continue;
                String id = dep.getModId();
                if (id.equals(info.getModId())) continue;
                deps.add(id);
            }
            map.put(info.getModId(), deps);
        }
        return map;
    }

    // ---------------- parsing ----------------

    public static Definition parse(String json) throws IOException {
        return parse(new StringReader(json));
    }

    private static Definition parse(Reader r) throws IOException {
        JsonReader jr = new JsonReader(r);
        jr.setLenient(true); // allows // comments
        JsonObject o = JsonParser.parseReader(jr).getAsJsonObject();
        String name = str(o, "name");
        if (name == null || name.isBlank()) throw new IOException("\"name\" is required");
        return new Definition(
                str(o, "name"), str(o, "icon"), str(o, "image"),
                str(o, "description") == null ? "" : str(o, "description"),
                list(o, "mods"),
                !o.has("include_addons") || o.get("include_addons").getAsBoolean(),
                list(o, "include"), list(o, "exclude"), list(o, "classes"),
                o.has("closed") && o.get("closed").getAsBoolean());
    }

    @Nullable
    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static List<String> list(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return List.of();
        if (e.isJsonPrimitive()) return List.of(e.getAsString());
        List<String> out = new ArrayList<>();
        for (JsonElement x : (JsonArray) e) out.add(x.getAsString());
        return out;
    }

    private static void writeExample(Path dir) throws IOException {
        Path example = dir.resolve("_example.json.txt");
        if (Files.exists(example)) return;
        Files.writeString(example, """
                // Copy this file to <id>.json (e.g. create.json) to create a bundle with id "create".
                // Grant it with: /modlock grant <players> bundle create
                {
                  // required
                  "name": "Create",
                  // optional: item used as icon, picture shown on the selection screen
                  "icon": "create:cogwheel",
                  "image": "create.png",
                  "description": "Create and all of its addons installed on this server",
                  "mods": ["create"],
                  "include_addons": true,
                  "include": [],
                  "exclude": [],
                  // RP classes: only players with one of these classes can use the bundle.
                  // Give classes with /modlock class add <players> <class> (or the tag modlock.class.<class>).
                  "classes": [],
                  // closed = members are denied to everyone outside "classes", even if unlocked another way.
                  "closed": false
                }
                """);
    }
}
