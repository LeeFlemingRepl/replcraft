package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import io.javalin.websocket.WsMessageContext;
import org.json.JSONObject;

public interface ActionContinuation {
    ActionContinuation execute(Client client, WsMessageContext ctx, JSONObject request, JSONObject response) throws ApiError;
}
