package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import io.javalin.websocket.WsMessageContext;
import org.json.JSONObject;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

@WebsocketAction(route = "unpoll", permission = "replcraft.api.unpoll", cost = FuelCost.Regular)
public class Unpoll implements WebsocketActionHandler {
    @Override
    public void execute(Client client, WsMessageContext ctx, JSONObject request, JSONObject response) throws ApiError {
        client.unpoll(getBlock(client, request));
    }
}
