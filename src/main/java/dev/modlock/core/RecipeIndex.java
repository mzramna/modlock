package dev.modlock.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.Block;

/**
 * Index of single-ingredient, non-crafting-table recipes: input item -> possible results.
 * Used to decide whether a player may put an item by hand into a machine of another mod.
 * Rebuilt lazily whenever the server's RecipeManager instance changes (/reload).
 */
public final class RecipeIndex {
    public record Entry(Identifier recipeId, ItemStack result) {}

    private static Map<RecipeManager, Map<Item, List<Entry>>> cache = new IdentityHashMap<>();

    private RecipeIndex() {}

    public static synchronized void invalidate() {
        cache = new IdentityHashMap<>();
    }

    @SuppressWarnings("deprecation") // Ingredient.items(): the only way to list the items of any ingredient, custom ones included
    private static synchronized Map<Item, List<Entry>> index(MinecraftServer server) {
        RecipeManager rm = server.getRecipeManager();
        Map<Item, List<Entry>> cached = cache.get(rm);
        if (cached != null) return cached;
        cache.clear();
        Map<Item, List<Entry>> idx = new HashMap<>();
        ContextMap displayContext = SlotDisplayContext.fromLevel(server.overworld());
        for (RecipeHolder<?> holder : rm.getRecipes()) {
            Recipe<?> recipe = holder.value();
            try {
                if (recipe.getType() == RecipeType.CRAFTING || recipe.isSpecial()) continue;
                Ingredient single;
                ItemStack result;
                if (recipe instanceof SingleItemRecipe sir) {
                    // smelting, blasting, smoking, campfire cooking, stonecutting (and mod subclasses):
                    // the result does not depend on the input stack
                    single = sir.input();
                    result = firstInputResult(sir, single);
                } else {
                    single = singleIngredient(recipe.placementInfo());
                    if (single == null) continue;
                    result = displayResult(recipe, displayContext);
                }
                if (result == null || result.isEmpty()) continue;
                Entry entry = new Entry(holder.id().identifier(), result.copy());
                single.items().forEach(h -> idx.computeIfAbsent(h.value(), k -> new ArrayList<>()).add(entry));
            } catch (RuntimeException e) {
                // some mod recipes cannot compute a static result: unknown is never blocked
            }
        }
        cache.put(rm, idx);
        return idx;
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static ItemStack firstInputResult(SingleItemRecipe recipe, Ingredient input) {
        return input.items().findFirst()
                .map(h -> recipe.assemble(new SingleRecipeInput(new ItemStack(h))))
                .orElse(null);
    }

    /** The only ingredient of a recipe that can be placed in a grid, or null if it has zero or several. */
    @Nullable
    private static Ingredient singleIngredient(PlacementInfo placement) {
        if (placement.isImpossibleToPlace()) return null;
        List<Ingredient> ingredients = placement.ingredients();
        if (ingredients.size() != 1) return null;
        Ingredient ing = ingredients.get(0);
        return ing.isEmpty() ? null : ing;
    }

    /** Result shown by the recipe book/JEI display of the recipe, when every display agrees on it. */
    @Nullable
    private static ItemStack displayResult(Recipe<?> recipe, ContextMap context) {
        ItemStack found = null;
        for (RecipeDisplay display : recipe.display()) {
            ItemStack stack = display.result().resolveForFirstStack(context);
            if (stack.isEmpty()) return null;
            if (found == null) found = stack;
            else if (!ItemStack.isSameItemSameComponents(found, stack)) return null;
        }
        return found;
    }

    /**
     * True when there is at least one known recipe for this item and ALL of them produce
     * something the player cannot craft. Unknown items (no recipe) are never blocked.
     */
    public static boolean manualInsertBlocked(ServerPlayer player, Block machine, ItemStack stack) {
        if (stack.isEmpty()) return false;
        MinecraftServer server = player.level().getServer();
        if (server == null) return false;
        if (AccessRules.hasMachineLicense(player, BuiltInRegistries.BLOCK.getKey(machine))) {
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
