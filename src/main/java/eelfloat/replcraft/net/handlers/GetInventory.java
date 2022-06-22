package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.stream.Collectors;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

    
public class GetInventory implements WebsocketActionHandler {

    @Override
    public String route() {
        return "get_inventory";
    }

    @Override
    public String permission() {
        return "replcraft.api.get_inventory";
    }

    @Override
    public double cost(RequestContext ctx) {
        return FuelCost.Expensive.toDouble();
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        JSONArray items = new JSONArray();
        BlockState state = getBlock(ctx.structureContext, ctx.request).getState();
        if (!(state instanceof Container)) {
            throw new ApiError(ApiError.INVALID_OPERATION, "block isn't a container");
        }
        ItemStack[] contents = ((Container) state).getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;
            JSONObject jsonitem = new JSONObject();
            jsonitem.put("index", i);
            jsonitem.put("type", item.getType().getKey());
            jsonitem.put("amount", item.getAmount());

            jsonitem.put("enchantments", item.getEnchantments().entrySet().stream().map(entry -> {
                JSONObject enchantment = new JSONObject();
                enchantment.put("id", entry.getKey().getKey());
                enchantment.put("lvl", entry.getValue().intValue());
                return enchantment;
            }).collect(Collectors.toList()));

            ItemMeta itemMeta = item.getItemMeta();
            if (itemMeta != null) {
                jsonitem.put("meta", itemMeta.serialize());

                if (itemMeta instanceof Damageable) {
                    short maxDurability = item.getType().getMaxDurability();
                    int damage = ((Damageable) itemMeta).getDamage();
                    jsonitem.put("maxDurability", maxDurability);
                    jsonitem.put("durability", maxDurability - damage);
                }

                if (itemMeta instanceof BookMeta) {
                    jsonitem.put("pages", ((BookMeta) itemMeta).getPages());
                    jsonitem.put("author", ((BookMeta) itemMeta).getAuthor());
                    jsonitem.put("title", ((BookMeta) itemMeta).getTitle());
                }
            }

            items.put(jsonitem);
        }
        ctx.response.put("items", items);
        return null;
    }
}
