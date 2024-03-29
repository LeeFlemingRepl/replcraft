package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.net.RequestContext;


public class UnwatchAll implements WebsocketActionHandler {
    @Override
    public String route() {
        return "unwatch_all";
    }

    @Override
    public String permission() {
        return "replcraft.api.unwatch_all";
    }

    @Override
    public double cost(RequestContext ctx) {
        return FuelCost.Expensive.toDouble();
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) {
        ctx.structureContext.setWatchAll(false);
        return null;
    }
}
