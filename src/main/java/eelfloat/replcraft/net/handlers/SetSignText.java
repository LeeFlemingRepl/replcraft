package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
import org.json.JSONArray;

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
    public double cost(RequestContext ctx) {
        return FuelCost.BlockChange.toDouble();
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        Block block = getBlock(ctx.structureContext, ctx.request);
        BlockState state = block.getState();
        if (!(state instanceof Sign)) {
            throw new ApiError(ApiError.INVALID_OPERATION, "block is not a sign");
        }
        JSONArray lines = ctx.request.getJSONArray("lines");
        if (lines.length() != 4) {
            throw new ApiError(ApiError.BAD_REQUEST, "expected exactly 4 lines of text");
        }

        // Simulate sign change event to make chestshop and similar verify the request
        String[] line_array = new String[4];
        for (int i = 0; i < 4; i++) {
            line_array[i] = lines.getString(i);
        }

        checkProtectionPlugins(ctx.structureContext.getStructure().minecraft_uuid, block.getLocation());
        if (ReplCraft.plugin.sign_protection) {
            OfflinePlayer offlinePlayer = ctx.structureContext.getStructure().getPlayer();
            if (!(offlinePlayer instanceof Player)) {
                throw new ApiError("offline", "this API call requires you to be online");
            }
            SignChangeEvent event = new SignChangeEvent(block, (Player) offlinePlayer, line_array);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                throw new ApiError(ApiError.BAD_REQUEST, "sign change event was cancelled by another plugin");
            }
            // Use lines from fired event, since chestshop will rewrite them to be valid
            line_array = event.getLines();
        }

        for (int i = 0; i < 4; i++) {
            ((Sign) state).setLine(i, line_array[i]);
        }
        if (ReplCraft.plugin.core_protect) {
            String player = ctx.structureContext.getStructure().getPlayer().getName();
            ReplCraft.plugin.coreProtect.logPlacement(player + " [API]", block.getLocation(), block.getBlockData().getMaterial(), block.getBlockData());
        }
        state.update();
        return null;
    }
}
