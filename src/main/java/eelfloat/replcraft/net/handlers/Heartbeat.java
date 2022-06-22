package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;

public class Heartbeat implements WebsocketActionHandler {
    @Override
    public String route() {
        return "heartbeat";
    }

    @Override
    public String permission() {
        return null;
    }

    @Override
    public double cost(RequestContext ctx) {
        return 0;
    }

    @Override
    public boolean authenticated() {
        return false;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        // this space intentionally left blank
        return null;
    }
}
