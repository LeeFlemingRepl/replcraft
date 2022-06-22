package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.net.RequestContext;


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
    public double cost(RequestContext ctx) {
        return FuelCost.Regular.toDouble();
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) {
        ctx.response.put("x", ctx.structureContext.getStructure().inner_min_x());
        ctx.response.put("y", ctx.structureContext.getStructure().inner_min_y());
        ctx.response.put("z", ctx.structureContext.getStructure().inner_min_z());
        return null;
    }
}
