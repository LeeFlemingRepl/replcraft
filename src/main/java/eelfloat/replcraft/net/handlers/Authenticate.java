package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.Structure;
import eelfloat.replcraft.net.*;
import eelfloat.replcraft.util.StructureUtil;
import eelfloat.replcraft.exceptions.ApiError;
import io.jsonwebtoken.Claims;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static eelfloat.replcraft.net.ClientV2.CONTEXT_CAUSE_LOGIN;

public class Authenticate implements WebsocketActionHandler {
    @Override
    public String route() {
        return "authenticate";
    }

    @Override
    public String permission() {
        return null;
    }

    @Override
    public double cost(RequestContext ctx) {
        return FuelCost.None.toDouble();
    }

    @Override
    public boolean authenticated() {
        return false;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        AtomicReference<ApiError> error = new AtomicReference<>(null);
        AtomicReference<StructureContext> success = new AtomicReference<>(null);
        AtomicBoolean otherSuccess = new AtomicBoolean(false);

        String token = ctx.request.getString("token");
        if (ctx.client instanceof ClientV1) {
            ReplCraft.plugin.logger.info("Performing async V1 authentication...");
            StructureUtil.verifyTokenAsync(token, structure -> {
                ClientV1 client = (ClientV1) ctx.client;
                StructureContext structureContext = client.setContext(structure, token);
                success.set(structureContext);
            }, err -> {
                error.set(err);
                ReplCraft.plugin.logger.info(String.format(
                    "Authentication failed for %s: %s",
                    ctx.wsContext.session.getRemoteAddress(),
                    err
                ));
            });
        } else if (ctx.client instanceof ClientV2) {
            ClientV2 client = (ClientV2) ctx.client;
            Claims claims = StructureUtil.parseToken(token);
            String scope = claims.get("scope", String.class);
            ReplCraft.plugin.logger.info("Performing async V2 authentication for scope " + scope + "...");
            switch (scope) {
                case "structure":
                    StructureUtil.verifyTokenAsync(token, structure -> {
                        StructureContext structureContext = client.createContext(structure, token, CONTEXT_CAUSE_LOGIN);
                        ctx.response.put("context", structureContext.id);
                        ctx.response.put("scope", "structure");
                        success.set(structureContext);
                    }, err -> {
                        error.set(err);
                        ReplCraft.plugin.logger.info(String.format(
                            "Authentication failed for %s: %s",
                            ctx.wsContext.session.getRemoteAddress(),
                            err
                        ));
                    });
                    break;

                case "item":
                    client.items.add(new ItemContext(client, claims));
                    ctx.response.put("scope", "item");
                    otherSuccess.set(true);
                    break;

                default: throw new ApiError(ApiError.AUTHENTICATION_FAILED, "invalid token: unknown scope \"" + scope + "\"");
            }
        }

        return new ActionContinuation() {
            @Override
            public ActionContinuation execute(RequestContext ctx) throws ApiError {
                if (otherSuccess.get()) {
                    ReplCraft.plugin.logger.info(String.format(
                        "Client %s authenticated with a non-structure context",
                        ctx.wsContext.session.getRemoteAddress()
                    ));
                    return null;
                }

                StructureContext structureContext = success.get();
                if (structureContext != null) {
                    ReplCraft.plugin.logger.info(String.format(
                        "Client %s authenticated: %s",
                        ctx.wsContext.session.getRemoteAddress(),
                        structureContext.getStructure()
                    ));
                    return null;
                }

                ApiError apiError = error.get();
                if (apiError != null) throw apiError;

                ReplCraft.plugin.logger.info("Deferring authentication...");
                return this;
            }

            @Override
            public int getMinimumDelay() {
                return 5;
            }
        };
    }
}
