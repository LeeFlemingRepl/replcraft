package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.exceptions.InvalidStructure;
import eelfloat.replcraft.net.Client;
import eelfloat.replcraft.util.ApiUtil;
import io.javalin.websocket.WsMessageContext;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.json.JSONObject;

public class Pay implements WebsocketActionHandler {
    @Override
    public String route() {
        return "pay";
    }

    @Override
    public String permission() {
        return "replcraft.api.pay";
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
    public ActionContinuation execute(Client client, WsMessageContext ctx, JSONObject request, JSONObject response) throws InvalidStructure, ApiError {
        if (ReplCraft.plugin.economy == null) {
            throw new ApiError("bad request", "This command requires Vault to be installed on the server.");
        }

        String world = client.getStructure().sign.getWorld().getName();
        OfflinePlayer sender = client.getStructure().getPlayer();
        OfflinePlayer target = ApiUtil.getTargetPlayer(client, request);
        double amount = request.getDouble("amount");

        EconomyResponse econResponse = ReplCraft.plugin.economy.withdrawPlayer(sender, world, amount);
        if (!econResponse.transactionSuccess()) {
            throw new ApiError("invalid operation", "transaction failed: " + econResponse.errorMessage);
        }
        ReplCraft.plugin.economy.depositPlayer(target, world, amount);
        return null;
    }
}
