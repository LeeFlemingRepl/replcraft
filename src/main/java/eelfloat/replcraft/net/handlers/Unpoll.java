package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

    
public class Unpoll implements WebsocketActionHandler {
    @Override
    public String route() {
        return "unpoll";
    }

    @Override
    public String permission() {
        return "replcraft.api.unpoll";
    }

    @Override
    public double cost(RequestContext ctx) {
        return FuelCost.Regular.toDouble();
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        ctx.structureContext.unpoll(getBlock(ctx.structureContext, ctx.request));
        return null;
    }
}
