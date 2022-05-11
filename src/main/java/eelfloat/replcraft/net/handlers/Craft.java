package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.util.ApiUtil;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import io.javalin.websocket.WsMessageContext;
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

@WebsocketAction(route = "craft", permission = "replcraft.api.craft", cost = FuelCost.Expensive)
public class Craft implements WebsocketActionHandler {
    @Override
    public void execute(Client client, WsMessageContext ctx, JSONObject request, JSONObject response) throws ApiError {
        JSONArray ingredients = request.getJSONArray("ingredients");
        Inventory output = ApiUtil.getContainer(ApiUtil.getBlock(client, request), "output container");
        ApiUtil.checkProtectionPlugins(client.getStructure().minecraft_uuid, output.getLocation());

        class CraftingHelper {
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
        }

        // A list of crafting helpers. Crafting helpers pointing at the same location are duplicated
        // in the list. Some slots are null to account for spaces.
        ArrayList<CraftingHelper> items = new ArrayList<>();
        for (int i = 0; i < ingredients.length(); i++) {
            if (ingredients.isNull(i)) {
                items.add(null);
                continue;
            }

            JSONObject reference = ingredients.getJSONObject(i);
            Block block = ApiUtil.getBlock(client, reference);
            int index = reference.getInt("index");
            ItemStack item = ApiUtil.getItem(block, String.format("ingredient %d block", i), index);
            Location location = block.getLocation();
            ApiUtil.checkProtectionPlugins(client.getStructure().minecraft_uuid, location);
            CraftingHelper new_or_existing = items.stream()
                    .filter(helper -> helper != null && helper.location.equals(location) && helper.index == index)
                    .findFirst()
                    .orElseGet(() -> new CraftingHelper(item, location, index));
            new_or_existing.timesReferenced += 1;
            items.add(new_or_existing);
        }

        boolean crafted = false;
        Iterator<Recipe> iter = Bukkit.recipeIterator();
        recipes: while (!crafted && iter.hasNext()) {
            Recipe next = iter.next();
            if (next instanceof ShapedRecipe) {
                Map<Character, ItemStack> ingredientMap = ((ShapedRecipe) next).getIngredientMap();
                String[] rows = ((ShapedRecipe) next).getShape();
                for (int row = 0; row < rows.length; row++) {
                    for (int col = 0; col < rows[row].length(); col++) {
                        ItemStack stack = ingredientMap.get(rows[row].charAt(col));

                        int i = row * rows.length + col;
                        if (i >= items.size()) continue recipes;
                        CraftingHelper ingredient = items.get(i);

                        if (stack == null && ingredient != null) {
                            // Item provided but no item required here
                            // Reset used items and try next recipe
                            for (CraftingHelper item : items) if (item != null) item.timesUsed = 0;
                            continue recipes;
                        }
                        if (stack == null) {
                            // No item to check here and no item provided, move on to next slot
                            continue;
                        }
                        if (ingredient == null || stack.getType() != ingredient.item.getType()) {
                            // Incorrect item provided for slot
                            // Reset used items and try next recipe
                            for (CraftingHelper item : items) if (item != null) item.timesUsed = 0;
                            continue recipes;
                        }

                        ingredient.timesUsed += 1;
                    }
                }
            } else if (next instanceof ShapelessRecipe) {
                List<ItemStack> recipe_ingredients = ((ShapelessRecipe) next).getIngredientList();
                for (ItemStack required_item: recipe_ingredients) {
                    boolean matched = items.stream().anyMatch(helper -> {
                        if (helper == null) return false;
                        if (helper.item.getType() != required_item.getType()) return false;
                        if (helper.timesUsed >= helper.item.getAmount()) return false;
                        helper.timesUsed++;
                        return true;
                    });
                    if (!matched) {
                        for (CraftingHelper item: items) if (item != null) item.timesUsed = 0;
                        continue recipes;
                    }
                }
                crafted = true;
            } else {
                continue recipes;
            }

            // Ensure all items have actually been used as many times as they claim
            if (!items.stream().allMatch(helper -> helper == null || helper.timesUsed == helper.timesReferenced)) {
                for (CraftingHelper item: items) if (item != null) item.timesUsed = 0;
                continue recipes;
            }

            // Ensure no item underflowed
            for (CraftingHelper ingredient: items) {
                if (ingredient == null) continue;
                if (ingredient.timesUsed > ingredient.item.getAmount()) {
                    throw new ApiError("invalid operation", String.format(
                            "attempted to use more %s than available",
                            ingredient.item.getType()
                    ));
                }
            }

            // Ensure there's somewhere to put the resulting item
            if (!output.addItem(next.getResult()).isEmpty()) {
                throw new ApiError("invalid operation", "no space to store result");
            }
            if (ReplCraft.plugin.core_protect) {
                String player = client.getStructure().getPlayer().getName();
                ReplCraft.plugin.coreProtect.logContainerTransaction(player + " [API]", output.getLocation());
            }

            for (CraftingHelper ingredient: items) {
                if (ingredient == null) continue;
                ingredient.item.setAmount(ingredient.item.getAmount() - ingredient.timesUsed);
                if (ReplCraft.plugin.core_protect) {
                    String player = client.getStructure().getPlayer().getName();
                    ReplCraft.plugin.coreProtect.logContainerTransaction(player + " [API]", ingredient.location);
                }
            }
            crafted = true;
        }

        if (!crafted) throw new ApiError("invalid operation", "no matching recipe");
    }
}
