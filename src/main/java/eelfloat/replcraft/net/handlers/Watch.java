package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import io.javalin.websocket.WsMessageContext;
import org.json.JSONObject;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

    
public class Watch implements WebsocketActionHandler {
    @Override
    public String route() {
        return "watch";
    }

    @Override
    public String permission() {
        return "replcraft.api.watch";
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
    public void execute(Client client, WsMessageContext ctx, JSONObject request, JSONObject response) throws ApiError {
        client.watch(getBlock(client, request));
    }
}
