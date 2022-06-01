package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

    
public class WatchAll implements WebsocketActionHandler {
    @Override
    public String route() {
        return "watch_all";
    }

    @Override
    public String permission() {
        return "replcraft.api.watch_all";
    }

    @Override
    public FuelCost cost() {
        return FuelCost.Expensive;
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        ctx.client.setWatchAll(true);
        return null;
    }
}
