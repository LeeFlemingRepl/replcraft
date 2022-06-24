package eelfloat.replcraft.net;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.handlers.*;
import eelfloat.replcraft.util.ApiUtil;
import io.javalin.Javalin;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import org.bukkit.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class WebsocketServer {
    public final Map<WsContext, Client> clients = new ConcurrentHashMap<>();

    public final Map<String, WebsocketActionHandler> apis = new HashMap<>();
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
        this.register(new Heartbeat());
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
        this.register(new Respond());
        this.register(new Tell());
        this.register(new Pay());
        this.register(new FuelInfo());

        app = Javalin.create();
        app.get("/", ctx -> ctx.result("Running ReplCraft v" + ReplCraft.plugin.getDescription().getVersion()));
        HashMap<String, Function<WsConnectContext, Client>> versions = new HashMap<>();
        versions.put("/gateway", (ctx) -> new ClientV1(ctx, this));
        versions.put("/gateway/v2", (ctx) -> new ClientV2(ctx, this));
        for (Map.Entry<String, Function<WsConnectContext, Client>> entry: versions.entrySet()) {
            app.ws(entry.getKey(), ws -> {
                ws.onConnect(ctx -> {
                    Client client = entry.getValue().apply(ctx);
                    ReplCraft.plugin.logger.info(String.format(
                        "Client %s connected [%s].",
                        ctx.session.getRemoteAddress(),
                        client.getClass().getName()
                    ));
                    this.clients.put(ctx, client);
                });
                ws.onClose(ctx -> {
                    ReplCraft.plugin.logger.info("Client " + ctx.session.getRemoteAddress() + " disconnected.");
                    Bukkit.getScheduler().runTask(ReplCraft.plugin, this.clients.remove(ctx)::dispose);
                });
                ws.onMessage(this::onMessage);
            });
        }
        app.start(ReplCraft.plugin.listen_address, ReplCraft.plugin.listen_port);
    }

    private void register(WebsocketActionHandler handler) {
        this.apis.put(handler.route(), handler);
    }

    private void onMessage(WsMessageContext ctx) {
        String nonce = null;
        try {
            JSONObject request = new JSONObject(ctx.message());
            nonce = request.getString("nonce");

            JSONObject response = new JSONObject();
            response.put("ok", true);
            response.put("nonce", nonce);

            String action = request.getString("action");
            WebsocketActionHandler handler = this.apis.get(action);
            if (handler == null) throw new ApiError(ApiError.BAD_REQUEST, "Unknown action: " + action);

            Client client = clients.get(ctx);
            StructureContext context = client instanceof ClientV2
                ? handler.authenticated()
                    ? ((ClientV2) client).getContext(request.getLong("context"))
                    : null
                : client.getDefaultContext();

            if (handler.authenticated()) {
                if (context == null) {
                    throw new ApiError(ApiError.UNAUTHENTICATED, "Invalid context");
                }

                if (context.getStructure() == null) {
                    String error = context.isInvalidated()
                            ? "Structure was invalidated"
                            : "Connection isn't authenticated yet";
                    throw new ApiError(ApiError.UNAUTHENTICATED, error);
                }

                if (!context.getStructure().material.apis.contains(action)) {
                    throw new ApiError(ApiError.BAD_REQUEST, "This structure type doesn't support this API call.");
                }

                String permission = handler.permission();
                if (permission != null) {
                    OfflinePlayer player = context.getStructure().getPlayer();
                    World world = context.getStructure().getWorld();
                    if (!ReplCraft.plugin.permissionProvider.hasPermission(player, world, permission)) {
                        throw new ApiError(ApiError.BAD_REQUEST, "You lack the permission to make this API call.");
                    }
                }
            }


            // --- Threading boundary! ---
            // Any code inside runTask() is run on the main server thread.
            // Any code above this should be non-mutative, functional-only code that should never touch the world.

            String finalNonce = nonce;
            Bukkit.getScheduler().runTask(ReplCraft.plugin, () -> {
                try {
                    boolean freeFuel = (
                        context != null &&
                        ReplCraft.plugin.world_guard &&
                        ApiUtil.checkFlagOnStructure(
                            context.getStructure(),
                            ReplCraft.plugin.worldGuard.replcraft_infinite_fuel
                        )
                    );

                    RequestContext requestContext = new RequestContext(
                        client, context, ctx, request, response, finalNonce
                    );

                    if (context != null) {
                        double fuelCost = (
                            handler.cost(requestContext) *
                            context.getStructure().material.fuelMultiplier
                        );

                        if (!freeFuel && !context.useFuel(fuelCost)) {
                            String message = String.format(
                                "out of fuel (cost: %s). available strategies: provide %s of %s.",
                                fuelCost,
                                context.getStructure().material.consumeFromAll ? "ALL" : "ANY",
                                context.getFuelSources()
                            );
                            throw new ApiError(ApiError.OUT_OF_FUEL, message);
                        }

                        context.tracker(handler).queue(fuelCost);
                    }

                    evaluateContinuation(requestContext, handler);
                } catch(Exception ex) {
                    ctx.send(toClientError(ex, finalNonce, context).toString());
                }
            });
        } catch (Exception ex) {
            ctx.send(toClientError(ex, nonce, null).toString());
        }
    }

    /**
     * Evaluates a continuation to completion
     * @param continuation the continuation to evaluate
     */
    public static void evaluateContinuation(RequestContext requestContext, ActionContinuation continuation) {
        try {
            ActionContinuation next = continuation.execute(requestContext);
            if (next != null) {
                Bukkit.getScheduler().runTaskLater(ReplCraft.plugin, () -> evaluateContinuation(requestContext, next), 1);
            } else {
                requestContext.wsContext.send(requestContext.response.toString());
            }
        } catch (Exception ex) {
            JSONObject err = WebsocketServer.toClientError(ex, requestContext.nonce, requestContext.structureContext);
            requestContext.wsContext.send(err.toString());
        }
    }

    public static JSONObject toClientError(Exception ex, String nonce, StructureContext context) {
        JSONObject json = new JSONObject();
        json.put("ok", false);
        json.put("nonce", nonce);
        if (ex instanceof JSONException) {
            json.put("error", ApiError.BAD_REQUEST);
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
