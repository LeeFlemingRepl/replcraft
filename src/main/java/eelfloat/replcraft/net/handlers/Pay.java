package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;
import eelfloat.replcraft.util.ApiUtil;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

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
    public double cost(RequestContext ctx) {
        return FuelCost.Expensive.toDouble();
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        if (ReplCraft.plugin.economy == null) {
            throw new ApiError(ApiError.BAD_REQUEST, "This command requires Vault to be installed on the server.");
        }

        String world = ctx.structureContext.getStructure().getWorld().getName();
        OfflinePlayer sender = ctx.structureContext.getStructure().getPlayer();
        OfflinePlayer target = ApiUtil.getTargetPlayer(ctx.structureContext, ctx.request);
        double amount = ctx.request.getDouble("amount");

        EconomyResponse econResponse = ReplCraft.plugin.economy.withdrawPlayer(sender, world, amount);
        if (!econResponse.transactionSuccess()) {
            throw new ApiError(ApiError.INVALID_OPERATION, "transaction failed: " + econResponse.errorMessage);
        }
        ReplCraft.plugin.economy.depositPlayer(target, world, amount);
        return null;
    }
}
