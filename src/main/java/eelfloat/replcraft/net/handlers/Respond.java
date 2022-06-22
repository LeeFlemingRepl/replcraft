package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.StructureContext;
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
        BiFunction<StructureContext.QueryStatus, JSONObject, ApiError> cb = ctx.structureContext.queryCallbacks.remove(ctx.request.getLong("queryNonce"));
        if (cb == null) throw new ApiError(ApiError.INVALID_OPERATION, "No such callback. It may have expired.");
        ApiError error = cb.apply(StructureContext.QueryStatus.Success, ctx.request);
        if (error != null) throw error;
        return null;
    }
}
