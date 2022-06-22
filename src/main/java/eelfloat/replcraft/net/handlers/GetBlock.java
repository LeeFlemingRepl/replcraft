package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

    
public class GetBlock implements WebsocketActionHandler {
    @Override
    public String route() {
        return "get_block";
    }

    @Override
    public String permission() {
        return "replcraft.api.get_block";
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
        ctx.response.put("block", getBlock(ctx.structureContext, ctx.request).getBlockData().getAsString());
        return null;
    }
}
