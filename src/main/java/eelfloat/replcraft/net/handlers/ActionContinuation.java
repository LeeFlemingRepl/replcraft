package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;

public interface ActionContinuation {
    ActionContinuation execute(RequestContext ctx) throws ApiError;
}
