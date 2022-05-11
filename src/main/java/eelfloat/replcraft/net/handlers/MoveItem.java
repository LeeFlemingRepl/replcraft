package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.util.ApiUtil;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import io.javalin.websocket.WsMessageContext;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.json.JSONObject;

import java.util.HashMap;

@WebsocketAction(route = "move_item", permission = "replcraft.api.move_item", cost = FuelCost.Expensive)
public class MoveItem implements WebsocketActionHandler {
    @Override
    public void execute(Client client, WsMessageContext ctx, JSONObject request, JSONObject response) throws ApiError {
        Block source = ApiUtil.getBlock(client, request, "source_x", "source_y", "source_z");
        Block target = ApiUtil.getBlock(client, request, "target_x", "target_y", "target_z");
        ApiUtil.checkProtectionPlugins(client.getStructure().minecraft_uuid, source.getLocation());
        ApiUtil.checkProtectionPlugins(client.getStructure().minecraft_uuid, target.getLocation());
        int index = request.getInt("index");
        int amount = request.isNull("amount") ? 0 : request.getInt("amount");

        Inventory source_inventory = ApiUtil.getContainer(source, "source block");
        Inventory target_inventory = ApiUtil.getContainer(target, "target block");
        ItemStack item = ApiUtil.getItem(source, "source block", index);

        if (amount == 0) amount = item.getAmount();
        if (amount > item.getAmount()) {
            throw new ApiError("invalid operation", "tried to move more items than there are");
        }
        ItemStack moved = item.clone();
        item.setAmount(item.getAmount() - amount);
        moved.setAmount(amount);
        if (ReplCraft.plugin.core_protect) {
            String player = client.getStructure().getPlayer().getName();
            ReplCraft.plugin.coreProtect.logContainerTransaction(player + " [API]", source.getLocation());
            ReplCraft.plugin.coreProtect.logContainerTransaction(player + " [API]", target.getLocation());
        }

        HashMap<Integer, ItemStack> leftover = target_inventory.addItem(moved);
        if (!leftover.values().isEmpty()) {
            for (ItemStack value: leftover.values()) {
                source_inventory.addItem(value);
            }
            throw new ApiError("invalid operation", "failed to move all items");
        }
    }
}
