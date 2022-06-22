package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

    
public class Poll implements WebsocketActionHandler {
    @Override
    public String route() {
        return "poll";
    }

    @Override
    public String permission() {
        return "replcraft.api.poll";
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
        ctx.structureContext.poll(getBlock(ctx.structureContext, ctx.request));
        return null;
    }
}
