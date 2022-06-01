package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

    
public class Unwatch implements WebsocketActionHandler {
    @Override
    public String route() {
        return "unwatch";
    }

    @Override
    public String permission() {
        return "replcraft.api.unwatch";
    }

    @Override
    public FuelCost cost() {
        return FuelCost.Regular;
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        ctx.client.unwatch(getBlock(ctx.client, ctx.request));
        return null;
    }
}
