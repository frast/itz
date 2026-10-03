package de.itz.adapter.primary.rest;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Container error dispatch only: never serialize exception attributes or request values. */
public class ApiErrorServlet extends HttpServlet {
    @Override
    protected void service(@SuppressWarnings("null") HttpServletRequest request,
            @SuppressWarnings("null") HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = request.getDispatcherType() == DispatcherType.ERROR && attribute instanceof Integer value
                && value >= 400 && value <= 599 ? value : 404;
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{1,128}")) {
            requestId = UUID.randomUUID().toString();
        }
        String allow = response.getHeader("Allow");
        String retry = response.getHeader("Retry-After");
        var original = new jakarta.ws.rs.core.MultivaluedHashMap<String, Object>();
        if (allow != null) {
            original.add("Allow", allow);
        }
        if (retry != null) {
            original.add("Retry-After", retry);
        }
        var headers = new jakarta.ws.rs.core.MultivaluedHashMap<String, Object>();
        ApiErrorResponses.protocolHeaders(status, original, headers);
        response.reset();
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Request-ID", requestId);
        headers.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value.toString())));
        if (!request.getMethod().equals("HEAD")) {
            var body = ApiErrorResponses.generic(status);
            // Both strings are fixed ASCII catalog values, never exception or request data.
            response.getWriter()
                    .write("{\"code\":\"" + body.getCode() + "\",\"message\":\"" + body.getMessage() + "\"}");
        }
    }
}
