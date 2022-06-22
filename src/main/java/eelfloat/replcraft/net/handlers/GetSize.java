package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.net.RequestContext;


public class GetSize implements WebsocketActionHandler {
    @Override
    public String route() {
        return "get_size";
    }

    @Override
    public String permission() {
        return "replcraft.api.get_size";
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
        ctx.response.put("x", ctx.structureContext.getStructure().inner_size_x());
        ctx.response.put("y", ctx.structureContext.getStructure().inner_size_y());
        ctx.response.put("z", ctx.structureContext.getStructure().inner_size_z());
        return null;
    }
}
