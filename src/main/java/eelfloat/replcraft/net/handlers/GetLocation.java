package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

    
public class GetLocation implements WebsocketActionHandler {
    @Override
    public String route() {
        return "get_location";
    }

    @Override
    public String permission() {
        return "replcraft.api.get_location";
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
    public ActionContinuation execute(RequestContext ctx) {
        ctx.response.put("x", ctx.client.getStructure().inner_min_x());
        ctx.response.put("y", ctx.client.getStructure().inner_min_y());
        ctx.response.put("z", ctx.client.getStructure().inner_min_z());
        return null;
    }
}
