package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.exceptions.InvalidStructure;
import eelfloat.replcraft.net.Client;
import io.javalin.websocket.WsMessageContext;
import org.json.JSONObject;

import java.util.function.BiFunction;

public class Respond implements WebsocketActionHandler {
    @Override
    public String route() {
        return "respond";
    }

    @Override
    public String permission() {
        return "replcraft.api.respond";
    }

    @Override
    public FuelCost cost() {
        return FuelCost.None;
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(Client client, WsMessageContext ctx, JSONObject request, JSONObject response) throws InvalidStructure, ApiError {
        BiFunction<Client.QueryStatus, JSONObject, ApiError> cb = client.queryCallbacks.remove(request.getLong("queryNonce"));
        if (cb == null) throw new ApiError("invalid operation", "No such callback. It may have expired.");
        ApiError error = cb.apply(Client.QueryStatus.Success, request);
        if (error != null) throw error;
        return null;
    }
}
