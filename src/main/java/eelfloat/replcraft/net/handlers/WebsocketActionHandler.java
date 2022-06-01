package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import io.javalin.websocket.WsMessageContext;
import org.json.JSONObject;

public interface WebsocketActionHandler extends ActionContinuation {
    /** @return the name of the route as called by the client */
    String route();
    /** @return The permission required for this route, or null no permission required */
    String permission();
    /** @return The fuel cost to call this route */
    FuelCost cost();
    /** @return Whether this route requires authentication */
    boolean authenticated();
}

