package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.Structure;
import eelfloat.replcraft.net.ClientV1;
import eelfloat.replcraft.net.ClientV2;
import eelfloat.replcraft.net.RequestContext;
import eelfloat.replcraft.net.StructureContext;
import eelfloat.replcraft.util.StructureUtil;
import eelfloat.replcraft.exceptions.ApiError;
import io.jsonwebtoken.Claims;

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
            Claims claims = StructureUtil.parseToken(token);
            String scope = claims.get("scope", String.class);
            ReplCraft.plugin.logger.info("Performing async V2 authentication for scope " + scope + "...");
            switch (scope) {
                case "structure":
                    StructureUtil.verifyTokenAsync(token, structure -> {
                        ClientV2 client = (ClientV2) ctx.client;
                        StructureContext structureContext = client.createContext(structure, token, CONTEXT_CAUSE_LOGIN);
                        ctx.response.put("context", structureContext.id);
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

                default: throw new ApiError(ApiError.AUTHENTICATION_FAILED, "invalid token: unknown claim type");
            }
        }

        return new ActionContinuation() {
            @Override
            public ActionContinuation execute(RequestContext ctx) throws ApiError {
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
