package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import io.javalin.websocket.WsMessageContext;
import org.json.JSONObject;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

    
public class Poll implements WebsocketActionHandler {
    @Override
    public String route() {
        return "poll";
    }

    @Override
    public String permission() {
        return "replcraft.api.poll";
    }

    @Override
    public FuelCost cost() {
        return FuelCost.Regular;
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(Client client, WsMessageContext ctx, JSONObject request, JSONObject response) throws ApiError {
        client.poll(getBlock(client, request));
        return null;
    }
}
