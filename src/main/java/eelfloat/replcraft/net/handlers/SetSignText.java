package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import io.javalin.websocket.WsMessageContext;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import static eelfloat.replcraft.util.ApiUtil.checkProtectionPlugins;
import static eelfloat.replcraft.util.ApiUtil.getBlock;

    
public class SetSignText implements WebsocketActionHandler {
    @Override
    public String route() {
        return "set_sign_text";
    }

    @Override
    public String permission() {
        return "replcraft.api.set_sign_text";
    }

    @Override
    public FuelCost cost() {
        return FuelCost.BlockChange;
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public void execute(Client client, WsMessageContext ctx, JSONObject request, JSONObject response) throws ApiError {
        Block block = getBlock(client, request);
        BlockState state = block.getState();
        if (!(state instanceof Sign)) {
            throw new ApiError("invalid operation", "block is not a sign");
        }
        JSONArray lines = request.getJSONArray("lines");
        if (lines.length() != 4) {
            throw new ApiError("bad request", "expected exactly 4 lines of text");
        }

        // Simulate sign change event to make chestshop and similar verify the request
        String[] line_array = new String[4];
        for (int i = 0; i < 4; i++) {
            line_array[i] = lines.getString(i);
        }

        checkProtectionPlugins(client.getStructure().minecraft_uuid, block.getLocation());
        if (ReplCraft.plugin.sign_protection) {
            OfflinePlayer offlinePlayer = client.getStructure().getPlayer();
            if (!(offlinePlayer instanceof Player)) {
                throw new ApiError("offline", "this API call requires you to be online");
            }
            SignChangeEvent event = new SignChangeEvent(block, (Player) offlinePlayer, line_array);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                throw new ApiError("bad request", "sign change event was cancelled by another plugin");
            }
            // Use lines from fired event, since chestshop will rewrite them to be valid
            line_array = event.getLines();
        }

        for (int i = 0; i < 4; i++) {
            ((Sign) state).setLine(i, line_array[i]);
        }
        if (ReplCraft.plugin.core_protect) {
            String player = client.getStructure().getPlayer().getName();
            ReplCraft.plugin.coreProtect.logPlacement(player + " [API]", block.getLocation(), block.getBlockData().getMaterial(), block.getBlockData());
        }
        state.update();
    }
}
