package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;

public class SetFuelLimit implements WebsocketActionHandler {
    @Override
    public String route() {
        return "set_fuel_limit";
    }

    @Override
    public String permission() {
        return "replcraft.api.set_fuel_limit";
    }

    @Override
    public double cost(RequestContext ctx) {
        return 0;
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        String strategy = ctx.request.getString("strategy");
        double limit = ctx.request.getDouble("limit");
        if (!ctx.structureContext.setMaxFuel(strategy, limit))
            throw new ApiError(ApiError.BAD_REQUEST, "No such fuel strategy");
        return null;
    }
}
