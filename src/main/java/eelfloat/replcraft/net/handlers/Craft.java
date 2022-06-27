package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.net.RequestContext;
import eelfloat.replcraft.util.ApiUtil;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.StructureContext;

import eelfloat.replcraft.util.InventoryReference;
import eelfloat.replcraft.util.VirtualInventory;
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
        public final VirtualInventory.Slot slot;
        /** The number of items that have been consumed from this helper */
        int timesUsed = 0;
        /** The number of times this helper was included in the client's provided recipe */
        int timesReferenced = 0;

        public CraftingHelper(VirtualInventory.Slot slot) {
            this.slot = slot;
        }

        public ItemStack getItem() {
            return this.slot.get();
        }

        @Override
        public String toString() {
            return String.format(
                "%s from %s slot %d (%d/%d)",
                this.slot.get(),
                this.slot.getInventory().getLocation(),
                this.slot.getIndex(),
                timesUsed,
                timesReferenced
            );
        }
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        JSONArray ingredients = ctx.request.getJSONArray("ingredients");
        VirtualInventory output = ApiUtil.getInventory(ctx, s -> s, false);
        output.checkProtectionPlugins(ctx);

        // A list of crafting helpers. Crafting helpers pointing at the same location are duplicated
        // in the list. Some slots are null to account for spaces.
        ArrayList<CraftingHelper> items = new ArrayList<>();
        for (int i = 0; i < ingredients.length(); i++) {
            if (ingredients.isNull(i)) {
                items.add(null);
                continue;
            }

            JSONObject reference = ingredients.getJSONObject(i);
            VirtualInventory inventory = ApiUtil.getInventory(ctx.structureContext, reference, s -> s, false);
            VirtualInventory.Slot slot = inventory.getSlot(reference.getInt("index"));
            slot.checkProtectionPlugins(ctx);
            CraftingHelper new_or_existing = items.stream()
                .filter(helper -> helper != null && helper.slot.equals(slot))
                .findFirst()
                .orElseGet(() -> new CraftingHelper(slot));
            new_or_existing.timesReferenced += 1;
            items.add(new_or_existing);
        }
        ReplCraft.plugin.logger.info("[Debug] crafting result: " + items);

        Iterator<Recipe> iter = Bukkit.recipeIterator();
        while (iter.hasNext()) {
            Recipe next = iter.next();
            // Try the recipe
            if (tryRecipe(ctx.structureContext, next, items, output))
                return null;
            // Reset items since this recipe failed
            for (CraftingHelper item: items)
                if (item != null) item.timesUsed = 0;
        }

        throw new ApiError(ApiError.INVALID_OPERATION, "no matching recipe");
    }

    private boolean tryRecipe(
        StructureContext structureContext,
        Recipe recipe,
        ArrayList<CraftingHelper> items,
        VirtualInventory output
    ) throws ApiError {
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
                    if (ingredient == null || stack.getType() != ingredient.getItem().getType()) return false;

                    ingredient.timesUsed += 1;
                }
            }
        } else if (recipe instanceof ShapelessRecipe) {
            List<ItemStack> recipe_ingredients = ((ShapelessRecipe) recipe).getIngredientList();
            for (ItemStack required_item: recipe_ingredients) {
                boolean matched = items.stream().anyMatch(helper -> {
                    if (helper == null) return false;
                    if (helper.getItem().getType() != required_item.getType()) return false;
                    if (helper.timesUsed >= helper.getItem().getAmount()) return false;
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
            if (ingredient.timesUsed > ingredient.getItem().getAmount()) {
                throw new ApiError(ApiError.INVALID_OPERATION, String.format(
                    "attempted to use more %s than available",
                    ingredient.getItem().getType()
                ));
            }
        }

        // Ensure there's somewhere to put the resulting item
        ReplCraft.plugin.logger.info("Checking space");
        if (output.deposit(recipe.getResult()).length > 0) {
            throw new ApiError(ApiError.INVALID_OPERATION, "no space to store result");
        }
        if (ReplCraft.plugin.core_protect) {
            String name = structureContext.getStructure().getPlayer().getName() + " [API]";
            for (InventoryReference inventory: output.inventories) {
                inventory.container.ifPresent(container -> {
                    ReplCraft.plugin.coreProtect.logContainerTransaction(name, container.getLocation());
                });
            }
        }

        ReplCraft.plugin.logger.info("Crafting " + items + "->" + recipe.getResult());
        for (CraftingHelper ingredient: items) {
            if (ingredient == null) continue;
            ingredient.getItem().setAmount(ingredient.getItem().getAmount() - ingredient.timesUsed);
            ingredient.timesUsed = 0; // Prevent anyone else from using it to reduce stack amounts more
            if (ReplCraft.plugin.core_protect) {
                String name = structureContext.getStructure().getPlayer().getName() + " [API]";
                Location location = ingredient.slot.getInventory().getLocation();
                ReplCraft.plugin.coreProtect.logContainerTransaction(name, location);
            }
        }

        return true;
    }
}
