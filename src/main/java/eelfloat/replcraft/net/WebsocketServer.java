package eelfloat.replcraft.net;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.exceptions.InvalidStructure;
import eelfloat.replcraft.net.handlers.*;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import org.bukkit.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WebsocketServer {
    public final Map<WsContext, Client> clients = new ConcurrentHashMap<>();
    public final Map<String, WebsocketActionHandler> routes = new ConcurrentHashMap<>();
    private final Javalin app;

    public WebsocketServer() {
        this.register(new Authenticate());
        this.register(new Craft());
        this.register(new GetBlock());
        this.register(new GetEntities());
        this.register(new GetInventory());
        this.register(new GetLocation());
        this.register(new GetPowerLevel());
        this.register(new GetSignText());
        this.register(new GetSize());
        this.register(new MoveItem());
        this.register(new Poll());
        this.register(new PollAll());
        this.register(new SetBlock());
        this.register(new SetSignText());
        this.register(new Unpoll());
        this.register(new UnpollAll());
        this.register(new Unwatch());
        this.register(new UnwatchAll());
        this.register(new Watch());
        this.register(new WatchAll());

        app = Javalin.create();
        app.get("/", ctx -> ctx.result("Running ReplCraft v" + ReplCraft.plugin.getDescription().getVersion()));
        app.ws("/gateway", ws -> {
            ws.onConnect(ctx -> {
                ReplCraft.plugin.logger.info("Client " + ctx.session.getRemoteAddress() + " connected.");
                clients.put(ctx, new Client(ctx));
            });
            ws.onClose(ctx -> {
                ReplCraft.plugin.logger.info("Client " + ctx.session.getRemoteAddress() + " disconnected.");
                Bukkit.getScheduler().runTask(ReplCraft.plugin, clients.remove(ctx)::dispose);
            });
            ws.onMessage(this::onMessage);
        });
        app.start(ReplCraft.plugin.listen_address, ReplCraft.plugin.listen_port);
    }

    private void register(WebsocketActionHandler handler) {
        // I thought I was writing Rust and then realized I can't do
        // `WebsocketAction + WebsocketActionHandler` as a type...
        this.routes.put(((WebsocketAction) handler).route(), handler);
    }

    public void onMessage(WsMessageContext ctx) {
        String nonce = null;
        try {
            JSONObject request = new JSONObject(ctx.message());
            nonce = request.getString("nonce");

            JSONObject response = new JSONObject();
            response.put("ok", true);
            response.put("nonce", nonce);
            Client client = clients.get(ctx);
            String action = request.getString("action");

            WebsocketActionHandler handler = this.routes.get(action);
            if (handler == null) throw new ApiError("bad request", "Unknown action");

            if (((WebsocketAction) handler).authenticated() && client.getStructure() == null) {
                String error = client.isInvalidated()
                    ? "Structure was invalidated"
                    : "Connection isn't authenticated yet";
                throw new ApiError("unauthenticated", error);
            }

            String permission = ((WebsocketAction) handler).permission();
            if (!permission.isEmpty()) {
                OfflinePlayer player = client.getStructure().getPlayer();
                World world = client.getStructure().getWorld();
                if (!ReplCraft.plugin.permissionProvider.hasPermission(player, world, permission))
                    throw new ApiError("bad request", "You lack the permission to make this API call.");
            }

            // --- Threading boundary! ---
            // Any code inside runTask() is run on the main server thread.
            // Any code above this should be non-mutative, functional-only code that should never touch the world.

            String finalNonce = nonce;
            Bukkit.getScheduler().runTask(ReplCraft.plugin, () -> {
                try {
                    double fuelCost = ((WebsocketAction) handler).cost().toDouble();
                    if (!client.useFuel(fuelCost)) {
                        String message = String.format(
                            "out of fuel (cost: %s). available strategies: provide %s of %s.",
                            fuelCost, ReplCraft.plugin.consume_from_all ? "ALL" : "ANY", client.getFuelSources()
                        );
                        throw new ApiError("out of fuel", message);
                    }

                    handler.execute(client, ctx, request, response);
                    ctx.send(response.toString());
                } catch(Exception ex) {
                    ctx.send(toClientError(ex, finalNonce).toString());
                }
            });
        } catch (Exception ex) {
            ctx.send(toClientError(ex, nonce).toString());
        }
    }

    private JSONObject toClientError(Exception ex, String nonce) {
        JSONObject json = new JSONObject();
        json.put("ok", false);
        json.put("nonce", nonce);
        if (ex instanceof JSONException) {
            json.put("error", "bad request");
            json.put("message", ex.getMessage());
        } else if (ex instanceof InvalidStructure) {
            json.put("error", "invalid structure");
            json.put("message", ex.getMessage());
        } else if (ex instanceof ApiError) {
            json.put("error", ((ApiError) ex).type);
            json.put("message", ((ApiError) ex).message);
        } else {
            json.put("error", "internal error");
            json.put("message", "an internal server error occurred (this is a bug)");
            ex.printStackTrace();
        }
        return json;
    }

    public void shutdown() {
        this.app.stop();
    }
}
