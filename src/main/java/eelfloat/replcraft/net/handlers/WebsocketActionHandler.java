package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.net.RequestContext;

public interface WebsocketActionHandler extends ActionContinuation {
    /** @return the name of the route as called by the client */
    String route();
    /** @return The permission required for this route, or null no permission required */
    String permission();
    /**
     * @return The fuel cost to call this route
     */
    double cost(RequestContext ctx);
    /** @return Whether this route requires authentication */
    boolean authenticated();
}

