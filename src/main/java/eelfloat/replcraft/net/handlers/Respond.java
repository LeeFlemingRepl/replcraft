package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import eelfloat.replcraft.net.RequestContext;
import org.json.JSONObject;

import java.util.function.BiFunction;

public class Respond implements WebsocketActionHandler {
    @Override
    public String route() {
        return "respond";
    }

    @Override
    public String permission() {
        return "replcraft.api.respond";
    }

    @Override
    public double cost(RequestContext ctx) {
        return FuelCost.None.toDouble();
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        BiFunction<Client.QueryStatus, JSONObject, ApiError> cb = ctx.client.queryCallbacks.remove(ctx.request.getLong("queryNonce"));
        if (cb == null) throw new ApiError("invalid operation", "No such callback. It may have expired.");
        ApiError error = cb.apply(Client.QueryStatus.Success, ctx.request);
        if (error != null) throw error;
        return null;
    }
}
