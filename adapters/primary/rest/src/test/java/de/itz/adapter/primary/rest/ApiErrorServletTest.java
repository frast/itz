package de.itz.adapter.primary.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import de.itz.adapter.primary.rest.generated.model.ErrorResponse;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

class ApiErrorServletTest {
    @Test
    void doesNotAttributeGenericUnprocessableEntityToVirusScanner() {
        assertEquals("REQUEST_REJECTED", ApiErrorResponses.generic(422).getCode());
    }

    @Test
    void sanitizesKnownCodeWithoutTrustingSuppliedMessage() {
        ErrorResponse sanitized = ApiErrorResponses.sanitize(400,
                new ErrorResponse("INVALID_UPLOAD", "Untrusted message"));
        ErrorResponse invalidFileName = ApiErrorResponses.sanitize(400,
                new ErrorResponse("INVALID_FILE_NAME", "Untrusted message"));
        ErrorResponse invalidContentType = ApiErrorResponses.sanitize(400,
                new ErrorResponse("INVALID_CONTENT_TYPE", "Untrusted message"));

        assertEquals("INVALID_UPLOAD", sanitized.getCode());
        assertEquals("The upload request is invalid", sanitized.getMessage());
        assertEquals("INVALID_FILE_NAME", invalidFileName.getCode());
        assertEquals("The file name must be valid Unicode, nonblank and contain at most 255 characters",
                invalidFileName.getMessage());
        assertEquals("INVALID_CONTENT_TYPE", invalidContentType.getCode());
        assertEquals("The content type must be valid Unicode, nonblank and contain at most 512 characters",
                invalidContentType.getMessage());
    }

    @Test
    void sanitizesContainerErrorsAndPreservesAuthenticationChallenge() throws Exception {
        Result result = invoke(DispatcherType.ERROR, "GET", false);
        assertEquals(401, result.status);
        assertEquals("Bearer", result.headers.get("WWW-Authenticate"));
        assertEquals("no-store", result.headers.get("Cache-Control"));
        assertEquals("safe-id", result.headers.get("X-Request-ID"));
        assertEquals("{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication is required\"}", result.body.toString());
        assertFalse(result.headers.toString().contains("SECRET_MARKER"));
    }

    @Test
    void directAccessCannotExposeErrorAttributes() throws Exception {
        Result result = invoke(DispatcherType.REQUEST, "GET", false);
        assertEquals(404, result.status);
        assertFalse(result.body.toString().contains("SECRET_MARKER"));
    }

    @Test
    void keepsHeadBodylessAndCommittedResponseUntouched() throws Exception {
        assertEquals("", invoke(DispatcherType.ERROR, "HEAD", false).body.toString());
        Result committed = invoke(DispatcherType.ERROR, "GET", true);
        assertEquals(0, committed.status);
        assertEquals("", committed.body.toString());
    }

    private Result invoke(DispatcherType dispatch, String method, boolean committed) throws Exception {
        Result result = new Result();
        result.headers.put("WWW-Authenticate", "Bearer error_description=SECRET_MARKER");
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, operation, args) -> switch (operation.getName()) {
                    case "getDispatcherType" -> dispatch;
                    case "getMethod" -> method;
                    case "getHeader" -> "safe-id";
                    case "getAttribute" -> RequestDispatcher.ERROR_STATUS_CODE.equals(args[0]) ? 401 : "SECRET_MARKER";
                    default -> throw new AssertionError(operation.getName());
                });
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (proxy, operation, args) -> switch (operation.getName()) {
                    case "isCommitted" -> committed;
                    case "getHeader" -> result.headers.get(args[0]);
                    case "reset" -> {
                        result.headers.clear();
                        yield null;
                    }
                    case "setStatus" -> {
                        result.status = (Integer) args[0];
                        yield null;
                    }
                    case "setHeader", "addHeader" -> {
                        result.headers.put((String) args[0], (String) args[1]);
                        yield null;
                    }
                    case "setContentType", "setCharacterEncoding" -> null;
                    case "getWriter" -> new PrintWriter(result.body);
                    default -> throw new AssertionError(operation.getName());
                });
        new ApiErrorServlet().service(request, response);
        return result;
    }

    private static class Result {
        int status;
        final Map<String, String> headers = new HashMap<>();
        final StringWriter body = new StringWriter();
    }
}
