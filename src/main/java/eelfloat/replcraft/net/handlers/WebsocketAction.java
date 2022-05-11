package eelfloat.replcraft.net.handlers;

public @interface WebsocketAction {
    /** @return the name of the route as called by the client */
    String route();
    /** @return The permission required for this route, or an empty string for no permission required */
    String permission();
    /** @return The fuel cost to call this route */
    FuelCost cost();
    /** @return Whether this route requires authentication */
    boolean authenticated() default true;
}
