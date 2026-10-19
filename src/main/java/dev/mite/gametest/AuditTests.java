package dev.mite.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mite.MiteMod;
import dev.mite.enchanting.EnchantingContent;
import dev.mite.material.MaterialItems;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * Registry audit: every mite item has a client item definition, its mite models and textures, and an EN and FR name; every mite
 * block drops through a loot table (or has none on purpose); every recipe result exists; no recipe makes an item of
 * {@code hardscrabble:progression_blacklist} (EF-PRG-04 CA-1). Assets are read from the mod's classpath (the server does not load them).
 */
public final class AuditTests {
    private AuditTests() {}

    public static @Nullable JsonElement json(String path) {
        InputStream in = AuditTests.class.getResourceAsStream("/" + path);
        if (in == null) return null;
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r);
        } catch (IOException e) {
            throw new IllegalStateException(path, e);
        }
    }

    private static boolean exists(String path) {
        return AuditTests.class.getResource("/" + path) != null;
    }

    /** Missing mite models and textures reachable from {@code model} (item definitions nest models under "model" keys). */
    private static void checkModels(JsonElement e, String owner, List<String> problems) {
        if (e instanceof JsonObject o) {
            for (var entry : o.entrySet()) {
                if (entry.getKey().equals("model") && entry.getValue().isJsonPrimitive()) checkModel(entry.getValue().getAsString(), owner, problems);
                else checkModels(entry.getValue(), owner, problems);
            }
        } else if (e != null && e.isJsonArray()) {
            e.getAsJsonArray().forEach(c -> checkModels(c, owner, problems));
        }
    }

    private static void checkModel(String model, String owner, List<String> problems) {
        Identifier id = Identifier.parse(model);
        if (!id.getNamespace().equals(MiteMod.MODID)) return;
        JsonElement m = json("assets/hardscrabble/models/" + id.getPath() + ".json");
        if (m == null) {
            problems.add(owner + ": model " + model);
            return;
        }
        JsonObject o = m.getAsJsonObject();
        if (o.has("parent")) checkModel(o.get("parent").getAsString(), owner, problems);
        if (!o.has("textures")) return;
        for (var t : o.getAsJsonObject("textures").entrySet()) {
            String value = t.getValue().getAsString();
            if (value.startsWith("#")) continue;
            Identifier tex = Identifier.parse(value);
            if (tex.getNamespace().equals(MiteMod.MODID) && !exists("assets/hardscrabble/textures/" + tex.getPath() + ".png")) problems.add(owner + ": texture " + value);
        }
    }

    public static void register() {
        MiteGameTests.add("audit_items_assets", 20, h -> {
            JsonObject en = json("assets/hardscrabble/lang/en_us.json").getAsJsonObject(), fr = json("assets/hardscrabble/lang/fr_fr.json").getAsJsonObject();
            List<String> problems = new ArrayList<>();
            for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
                if (!id.getNamespace().equals(MiteMod.MODID)) continue;
                Item item = BuiltInRegistries.ITEM.getValue(id);
                JsonElement def = json("assets/hardscrabble/items/" + id.getPath() + ".json");
                if (def == null) problems.add(id + ": item definition");
                else checkModels(def, id.toString(), problems);
                String key = item.getDescriptionId();
                if (!en.has(key)) problems.add(id + ": en_us " + key);
                if (!fr.has(key)) problems.add(id + ": fr_fr " + key);
            }
            h.assertTrue(problems.isEmpty(), problems.size() + " item asset problems: " + problems);
            h.succeed();
        });

        MiteGameTests.add("audit_block_loot_tables", 20, h -> {
            MinecraftServer server = h.getLevel().getServer();
            List<String> missing = new ArrayList<>();
            for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
                if (!id.getNamespace().equals(MiteMod.MODID)) continue;
                Block block = BuiltInRegistries.BLOCK.getValue(id);
                block.getLootTable().filter(k -> server.reloadableRegistries().getLootTable(k) == LootTable.EMPTY).ifPresent(k -> missing.add(id.toString()));
            }
            h.assertTrue(missing.isEmpty(), "blocks without a loot table: " + missing);
            h.succeed();
        });

        MiteGameTests.add("audit_recipes", 20, h -> {
            MinecraftServer server = h.getLevel().getServer();
            List<String> problems = new ArrayList<>();
            for (Map.Entry<Identifier, Resource> file : server.getResourceManager().listResources("recipe", p -> p.getPath().endsWith(".json")).entrySet()) {
                try (Reader r = file.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                    if (o.get("result") instanceof JsonObject result && result.has("id")
                            && !BuiltInRegistries.ITEM.containsKey(Identifier.parse(result.get("id").getAsString()))) {
                        problems.add(file.getKey() + ": unknown result " + result.get("id"));
                    }
                } catch (IOException e) {
                    problems.add(file.getKey() + ": " + e.getMessage());
                }
            }
            var context = SlotDisplayContext.fromLevel(h.getLevel());
            for (var recipe : server.getRecipeManager().getRecipes()) {
                if (recipe.value() instanceof SmithingTrimRecipe) continue; // trims the input armor, makes nothing new
                for (var display : recipe.value().display()) {
                    for (ItemStack stack : display.result().resolveForStacks(context)) {
                        // The ingots are blacklisted for loot only (EF-PRG-04: none in Overworld loot); smelting makes them.
                        if (stack.is(EnchantingContent.PROGRESSION_BLACKLIST) && !stack.is(MaterialItems.MITHRIL_INGOT) && !stack.is(MaterialItems.ADAMANTIUM_INGOT)) {
                            problems.add(recipe.id().identifier() + " makes blacklisted " + stack.getItem());
                        }
                    }
                }
            }
            h.assertTrue(problems.isEmpty(), problems.size() + " recipe problems: " + problems);
            h.succeed();
        });
    }
}
