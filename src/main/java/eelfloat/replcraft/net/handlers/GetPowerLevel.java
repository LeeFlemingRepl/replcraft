package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

    
public class GetPowerLevel implements WebsocketActionHandler {
    @Override
    public String route() {
        return "get_power_level";
    }

    @Override
    public String permission() {
        return "replcraft.api.get_power_level";
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
        ctx.response.put("power", getBlock(ctx.structureContext, ctx.request).getBlockPower());
        return null;
    }
}
