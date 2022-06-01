package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

    
public class Watch implements WebsocketActionHandler {
    @Override
    public String route() {
        return "watch";
    }

    @Override
    public String permission() {
        return "replcraft.api.watch";
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
        ctx.client.watch(getBlock(ctx.client, ctx.request));
        return null;
    }
}
