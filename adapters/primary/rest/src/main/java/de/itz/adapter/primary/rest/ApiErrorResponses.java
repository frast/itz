package de.itz.adapter.primary.rest;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.jspecify.annotations.Nullable;

import de.itz.adapter.primary.rest.generated.model.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

/** Fixed public errors shared by JAX-RS and servlet error dispatch. */
final class ApiErrorResponses {
    private record PublicError(int status, ApiErrorCode code, String message) {
        ErrorResponse body() {
            return new ErrorResponse(code.wireValue(), message);
        }
    }

    @SuppressWarnings("null")
    private static final List<PublicError> ERRORS = List.of(
            new PublicError(400, ApiErrorCode.BAD_REQUEST, "The request is invalid"),
            new PublicError(401, ApiErrorCode.UNAUTHORIZED, "Authentication is required"),
            new PublicError(403, ApiErrorCode.FORBIDDEN,
                    "The current user is not allowed to perform this operation"),
            new PublicError(404, ApiErrorCode.NOT_FOUND, "The requested resource was not found"),
            new PublicError(405, ApiErrorCode.METHOD_NOT_ALLOWED,
                    "The HTTP method is not allowed for this resource"),
            new PublicError(406, ApiErrorCode.NOT_ACCEPTABLE, "The requested response format is not supported"),
            new PublicError(413, ApiErrorCode.PAYLOAD_TOO_LARGE, "The request payload is too large"),
            new PublicError(415, ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, "The request content type is not supported"),
            new PublicError(429, ApiErrorCode.TOO_MANY_REQUESTS, "Too many requests"),
            new PublicError(503, ApiErrorCode.SERVICE_UNAVAILABLE, "The service is temporarily unavailable"),
            new PublicError(400, ApiErrorCode.INVALID_UPLOAD, "The upload request is invalid"),
            new PublicError(400, ApiErrorCode.INVALID_FILE_NAME,
                    "The file name must be valid Unicode, nonblank and contain at most 255 characters"),
            new PublicError(400, ApiErrorCode.INVALID_CONTENT_TYPE,
                    "The content type must be valid Unicode, nonblank and contain at most 512 characters"),
            new PublicError(413, ApiErrorCode.FILE_TOO_LARGE, "The uploaded file is too large"),
            new PublicError(422, ApiErrorCode.FILE_INFECTED,
                    "The uploaded file was rejected by the virus scanner"),
            new PublicError(500, ApiErrorCode.UPLOAD_FAILED, "The file could not be stored"));

    private ApiErrorResponses() {
    }

    static Response create(int status, ApiErrorCode code) {
        ErrorResponse body = ERRORS.stream().filter(error -> error.status() == status && error.code() == code)
                .findFirst().map(PublicError::body).orElseGet(() -> generic(status));
        return response(status, body);
    }

    @SuppressWarnings("null")
    static ErrorResponse generic(int status) {
        return ERRORS.stream().filter(error -> error.status() == status && isGeneric(error))
                .findFirst().map(PublicError::body).orElseGet(() -> new ErrorResponse(
                        (status >= 500 ? ApiErrorCode.REQUEST_FAILED : ApiErrorCode.REQUEST_REJECTED).wireValue(),
                        status >= 500 ? "The request could not be completed" : "The request was rejected"));
    }

    private static boolean isGeneric(PublicError error) {
        return switch (error.code()) {
            case INVALID_UPLOAD, INVALID_FILE_NAME, INVALID_CONTENT_TYPE, FILE_TOO_LARGE, FILE_INFECTED,
                    UPLOAD_FAILED ->
                false;
            default -> true;
        };
    }

    static Response response(int status, ErrorResponse body) {
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE)
                .header("Cache-Control", "no-store").entity(body).build();
    }

    @SuppressWarnings("null")
    static ErrorResponse sanitize(int status, @Nullable Object entity) {
        if (entity instanceof ErrorResponse supplied) {
            return ERRORS.stream().filter(error -> error.status() == status
                    && error.code().wireValue().equals(supplied.getCode())).findFirst().map(PublicError::body)
                    .orElseGet(() -> generic(status));
        }
        return generic(status);
    }

    static void protocolHeaders(int status, MultivaluedMap<String, Object> source,
            MultivaluedMap<String, Object> target) {
        if (status == 401) {
            target.putSingle("WWW-Authenticate", "Bearer");
        }
        source.forEach((name, values) -> {
            if (status == 405 && name.equalsIgnoreCase("Allow")) {
                for (Object value : values) {
                    String text = value.toString();
                    if (text.matches("[A-Za-z]+(?:[ ,]+[A-Za-z]+)*")) {
                        target.add("Allow", text);
                    }
                }
            }
            if ((status == 429 || status == 503) && name.equalsIgnoreCase("Retry-After") && values.size() == 1) {
                String value = values.get(0).toString();
                if (validRetryAfter(value)) {
                    target.putSingle("Retry-After", value);
                }
            }
        });
    }

    private static boolean validRetryAfter(String value) {
        if (value.matches("[0-9]{1,10}")) {
            return true;
        }
        if (value.length() != 29 || !value.endsWith(" GMT")) {
            return false;
        }
        try {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
            return true;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }
}
