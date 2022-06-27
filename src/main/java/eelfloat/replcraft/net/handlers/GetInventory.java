package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;
import eelfloat.replcraft.util.ApiUtil;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
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
        AtomicInteger index = new AtomicInteger();

        ApiUtil.getInventory(ctx, s -> s, false)
            .stream()
            .map(item -> {
                int i = index.getAndIncrement();
                if (item == null) return null;
                return serializeItemStack(i, item);
            })
            .filter(Objects::nonNull)
            .forEach(items::put);

        ctx.response.put("items", items);
        return null;
    }

    @NotNull
    private static JSONObject serializeItemStack(int index, ItemStack item) {
        JSONObject jsonitem = new JSONObject();
        jsonitem.put("index", index);
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

        return jsonitem;
    }
}
