package dev.modlock.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerPlayer;

/**
 * Index of single-ingredient, non-crafting-table recipes: input item -> possible results.
 * Used to decide whether a player may put an item by hand into a machine of another mod.
 * Rebuilt lazily whenever the server's RecipeManager instance changes (/reload).
 */
public final class RecipeIndex {
    public record Entry(ResourceLocation recipeId, ItemStack result) {}

    private static Map<RecipeManager, Map<Item, List<Entry>>> cache = new IdentityHashMap<>();

    private RecipeIndex() {}

    public static synchronized void invalidate() {
        cache = new IdentityHashMap<>();
    }

    private static synchronized Map<Item, List<Entry>> index(MinecraftServer server) {
        RecipeManager rm = server.getRecipeManager();
        Map<Item, List<Entry>> idx = cache.get(rm);
        if (idx != null) return idx;
        cache.clear();
        idx = new HashMap<>();
        for (RecipeHolder<?> holder : rm.getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (recipe.getType() == RecipeType.CRAFTING || recipe.isSpecial()) continue;
            Ingredient single = null;
            int count = 0;
            for (Ingredient ing : recipe.getIngredients()) {
                if (ing.isEmpty()) continue;
                single = ing;
                count++;
            }
            if (count != 1) continue;
            ItemStack result;
            try {
                result = recipe.getResultItem(server.registryAccess());
            } catch (RuntimeException e) {
                continue; // some mod recipes cannot compute a static result
            }
            if (result == null || result.isEmpty()) continue;
            Entry entry = new Entry(holder.id(), result.copy());
            for (ItemStack in : single.getItems()) {
                idx.computeIfAbsent(in.getItem(), k -> new ArrayList<>()).add(entry);
            }
        }
        cache.put(rm, idx);
        return idx;
    }

    /**
     * True when there is at least one known recipe for this item and ALL of them produce
     * something the player cannot craft. Unknown items (no recipe) are never blocked.
     */
    public static boolean manualInsertBlocked(ServerPlayer player, Block machine, ItemStack stack) {
        if (stack.isEmpty()) return false;
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        if (AccessRules.hasMachineLicense(player, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(machine))) {
            return false;
        }
        List<Entry> entries = index(server).get(stack.getItem());
        if (entries == null || entries.isEmpty()) return false;
        for (Entry e : entries) {
            if (AccessRules.canCraft(player, e.recipeId(), e.result())) return false;
        }
        return true;
    }
}
