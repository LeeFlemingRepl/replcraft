package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.net.RequestContext;

public interface WebsocketActionHandler extends ActionContinuation {
    /**
     * The unique name of the route
     * @return the name of the route as called by the client
     */
    String route();

    /**
     * Each route has a bukkit api permission that must be granted before the route handler is called.
     * @return The permission required for this route, or null no permission required
     */
    String permission();

    /**
     * Each route has a fuel cost that must be fulfilled before the route handler is called.
     * @return The fuel cost to call this route
     */
    double cost(RequestContext ctx);

    /**
     * Authenticated routes require a valid structure context. Unauthenticated routes are accessible without a token,
     * can't require fuel or permissions, lack a structureContext, and are primarily reserved for actually getting
     * authenticated.
     * @return Whether this route requires authentication
     */
    boolean authenticated();
}

