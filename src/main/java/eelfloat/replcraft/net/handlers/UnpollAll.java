package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.net.RequestContext;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

    
public class UnpollAll implements WebsocketActionHandler {
    @Override
    public String route() {
        return "unpoll_all";
    }

    @Override
    public String permission() {
        return "replcraft.api.unpoll_all";
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
        ctx.client.setPollAll(false);
        return null;
    }
}
