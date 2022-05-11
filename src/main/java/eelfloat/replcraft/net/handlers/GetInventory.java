package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import io.javalin.websocket.WsMessageContext;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;
import org.json.JSONArray;
import org.json.JSONObject;

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
    public FuelCost cost() {
        return FuelCost.Expensive;
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public void execute(Client client, WsMessageContext ctx, JSONObject request, JSONObject response) throws ApiError {
        JSONArray items = new JSONArray();
        BlockState state = getBlock(client, request).getState();
        if (!(state instanceof Container)) {
            throw new ApiError("invalid operation", "block isn't a container");
        }
        ItemStack[] contents = ((Container) state).getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;
            JSONObject jsonitem = new JSONObject();
            jsonitem.put("index", i);
            jsonitem.put("type", item.getType().getKey());
            jsonitem.put("amount", item.getAmount());
            items.put(jsonitem);
        }
        response.put("items", items);
    }
}
