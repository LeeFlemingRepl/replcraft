package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.net.RequestContext;
import eelfloat.replcraft.util.ApiUtil;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.StructureContext;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class Craft implements WebsocketActionHandler {
    @Override
    public String route() {
        return "craft";
    }

    @Override
    public String permission() {
        return "replcraft.api.craft";
    }

    @Override
    public double cost(RequestContext ctx) {
        return FuelCost.Expensive.toDouble();
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    private static class CraftingHelper {
        final ItemStack item;
        final Location location;
        final int index;
        /** The number of items that have been consumed from this helper */
        int timesUsed = 0;
        /** The number of times this helper was included in the client's provided recipe */
        int timesReferenced = 0;

        CraftingHelper(ItemStack item, Location location, int index) {
            this.item = item;
            this.location = location;
            this.index = index;
        }

        @Override
        public String toString() {
            return String.format("%s from %s slot %d (%d/%d)", item, location, index, timesUsed, timesReferenced);
        }
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        JSONArray ingredients = ctx.request.getJSONArray("ingredients");
        Inventory output = ApiUtil.getContainer(ApiUtil.getBlock(ctx.structureContext, ctx.request), "output container");
        ApiUtil.checkProtectionPlugins(ctx.structureContext.getStructure().minecraft_uuid, output.getLocation());

        // A list of crafting helpers. Crafting helpers pointing at the same location are duplicated
        // in the list. Some slots are null to account for spaces.
        ArrayList<CraftingHelper> items = new ArrayList<>();
        for (int i = 0; i < ingredients.length(); i++) {
            if (ingredients.isNull(i)) {
                items.add(null);
                continue;
            }

            JSONObject reference = ingredients.getJSONObject(i);
            Block block = ApiUtil.getBlock(ctx.structureContext, reference);
            int index = reference.getInt("index");
            ItemStack item = ApiUtil.getItem(block, String.format("ingredient %d block", i), index);
            Location location = block.getLocation();
            ApiUtil.checkProtectionPlugins(ctx.structureContext.getStructure().minecraft_uuid, location);
            CraftingHelper new_or_existing = items.stream()
                    .filter(helper -> helper != null && helper.location.equals(location) && helper.index == index)
                    .findFirst()
                    .orElseGet(() -> new CraftingHelper(item, location, index));
            new_or_existing.timesReferenced += 1;
            items.add(new_or_existing);
        }
        ReplCraft.plugin.logger.info("[Debug] crafting result: " + items);

        Iterator<Recipe> iter = Bukkit.recipeIterator();
        while (iter.hasNext()) {
            Recipe next = iter.next();
            // Try the recipe
            if (tryRecipe(ctx.structureContext, next, items, output)) return null;
            // Reset items since this recipe failed
            for (CraftingHelper item: items)
                if (item != null) item.timesUsed = 0;
        }

        throw new ApiError(ApiError.INVALID_OPERATION, "no matching recipe");
    }

    private boolean tryRecipe(StructureContext structureContext, Recipe recipe, ArrayList<CraftingHelper> items, Inventory output) throws ApiError {
        if (recipe instanceof ShapedRecipe) {
            Map<Character, ItemStack> ingredientMap = ((ShapedRecipe) recipe).getIngredientMap();
            String[] rows = ((ShapedRecipe) recipe).getShape();
            for (int row = 0; row < rows.length; row++) {
                for (int col = 0; col < rows[row].length(); col++) {
                    ItemStack stack = ingredientMap.get(rows[row].charAt(col));

                    int i = row * 3 + col;
                    if (i >= items.size()) return false;
                    CraftingHelper ingredient = items.get(i);

                    // Item provided but no item required here
                    if (stack == null && ingredient != null) return false;
                    // No item to check here and no item provided, move on to next slot
                    if (stack == null) continue;
                    // Incorrect item provided for slot
                    if (ingredient == null || stack.getType() != ingredient.item.getType()) return false;

                    ingredient.timesUsed += 1;
                }
            }
        } else if (recipe instanceof ShapelessRecipe) {
            List<ItemStack> recipe_ingredients = ((ShapelessRecipe) recipe).getIngredientList();
            for (ItemStack required_item: recipe_ingredients) {
                boolean matched = items.stream().anyMatch(helper -> {
                    if (helper == null) return false;
                    if (helper.item.getType() != required_item.getType()) return false;
                    if (helper.timesUsed >= helper.item.getAmount()) return false;
                    helper.timesUsed++;
                    return true;
                });
                if (!matched) return false;
            }
        } else {
            return false;
        }

        // Ensure all items have actually been used as many times as they claim
        ReplCraft.plugin.logger.info("Checking item use");
        if (!items.stream().allMatch(helper -> helper == null || helper.timesUsed == helper.timesReferenced)) {
            for (CraftingHelper item: items) if (item != null) item.timesUsed = 0;
            return false;
        }

        // Ensure no item underflowed
        ReplCraft.plugin.logger.info("Checking item underflow");
        for (CraftingHelper ingredient: items) {
            if (ingredient == null) continue;
            if (ingredient.timesUsed > ingredient.item.getAmount()) {
                throw new ApiError(ApiError.INVALID_OPERATION, String.format(
                    "attempted to use more %s than available",
                    ingredient.item.getType()
                ));
            }
        }

        // Ensure there's somewhere to put the resulting item
        ReplCraft.plugin.logger.info("Checking space");
        if (!output.addItem(recipe.getResult()).isEmpty()) {
            throw new ApiError(ApiError.INVALID_OPERATION, "no space to store result");
        }
        if (ReplCraft.plugin.core_protect) {
            String player = structureContext.getStructure().getPlayer().getName();
            ReplCraft.plugin.coreProtect.logContainerTransaction(player + " [API]", output.getLocation());
        }

        ReplCraft.plugin.logger.info("Crafting " + items + "->" + recipe.getResult());
        for (CraftingHelper ingredient: items) {
            if (ingredient == null) continue;
            ingredient.item.setAmount(ingredient.item.getAmount() - ingredient.timesUsed);
            ingredient.timesUsed = 0; // Prevent anyone else from using it more
            if (ReplCraft.plugin.core_protect) {
                String player = structureContext.getStructure().getPlayer().getName();
                ReplCraft.plugin.coreProtect.logContainerTransaction(player + " [API]", ingredient.location);
            }
        }

        return true;
    }
}
