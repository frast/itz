package de.itz.adapter.primary.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.jboss.resteasy.mock.MockDispatcherFactory;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.jboss.resteasy.spi.Dispatcher;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataReader;
import org.junit.jupiter.api.Test;

import de.itz.adapter.primary.rest.generated.model.ErrorResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

class HttpErrorHandlingTest {
    private Dispatcher dispatcher() {
        Dispatcher dispatcher = MockDispatcherFactory.createDispatcher();
        dispatcher.getProviderFactory().registerProvider(ResteasyJackson2Provider.class);
        dispatcher.getProviderFactory().registerProvider(MultipartFormDataReader.class);
        dispatcher.getProviderFactory().registerProvider(HttpExceptionMapper.class);
        dispatcher.getProviderFactory().registerProvider(RequestBodyExceptionMapper.class);
        dispatcher.getProviderFactory().registerProvider(ApplicationExceptionMapper.class);
        dispatcher.getProviderFactory().registerProvider(ApiErrorResponseFilter.class);
        dispatcher.getProviderFactory().registerProvider(RequestCorrelationFilter.class);
        dispatcher.getRegistry().addSingletonResource(new TestResource());
        return dispatcher;
    }

    @Test
    void normalizesRoutingAndProviderErrors() throws Exception {
        assertError(MockHttpRequest.get("/missing"), 404, "NOT_FOUND");
        MockHttpResponse method = assertError(MockHttpRequest.post("/test"), 405, "METHOD_NOT_ALLOWED");
        assertTrue(method.getOutputHeaders().getFirst("Allow").toString().contains("GET"));
        assertError(MockHttpRequest.get("/test").accept("text/plain"), 406, "NOT_ACCEPTABLE");
        assertError(MockHttpRequest.post("/test/input").contentType("text/plain"), 415, "UNSUPPORTED_MEDIA_TYPE");
        assertError(MockHttpRequest.post("/test/multipart").contentType("multipart/form-data")
                .content("broken".getBytes(java.nio.charset.StandardCharsets.UTF_8)), 400, "BAD_REQUEST");
        assertError(MockHttpRequest.post("/test/input").contentType("application/json")
                .content("{".getBytes(java.nio.charset.StandardCharsets.UTF_8)), 400, "BAD_REQUEST");
        assertError(MockHttpRequest.get("/test/failure"), 500, "REQUEST_FAILED");
        assertError(MockHttpRequest.get("/test/checked"), 500, "REQUEST_FAILED");
        assertError(MockHttpRequest.get("/test/entity"), 400, "BAD_REQUEST");
        assertError(MockHttpRequest.get("/test/forged"), 400, "INVALID_UPLOAD");
    }

    @Test
    void keepsHeadOptionsAndNonErrorSemantics() throws Exception {
        MockHttpResponse head = new MockHttpResponse();
        dispatcher().invoke(MockHttpRequest.head("/missing"), head);
        assertEquals(404, head.getStatus());
        assertEquals("", head.getContentAsString());
        for (String path : new String[]{"/test", "/test/redirect", "/test/empty", "/test/unmodified"}) {
            MockHttpResponse response = new MockHttpResponse();
            dispatcher().invoke(MockHttpRequest.get(path).header("X-Request-ID", "test-request"), response);
            assertTrue(response.getStatus() < 400);
            assertEquals("test-request", response.getOutputHeaders().getFirst("X-Request-ID"));
        }
        MockHttpResponse options = new MockHttpResponse();
        dispatcher().invoke(MockHttpRequest.options("/test"), options);
        assertEquals(200, options.getStatus());
        assertTrue(options.getOutputHeaders().containsKey("Allow"));
    }

    private MockHttpResponse assertError(MockHttpRequest request, int status, String code) throws Exception {
        request.header("X-Request-ID", "test-request");
        MockHttpResponse response = new MockHttpResponse();
        dispatcher().invoke(request, response);
        assertEquals(status, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":\"" + code + "\""), response.getContentAsString());
        assertFalse(response.getContentAsString().contains("SECRET_MARKER"));
        assertFalse(response.getOutputHeaders().toString().contains("SECRET_MARKER"));
        assertEquals("no-store", response.getOutputHeaders().getFirst("Cache-Control"));
        assertEquals("test-request", response.getOutputHeaders().getFirst("X-Request-ID"));
        return response;
    }

    @Path("/test")
    @Produces("application/json")
    public static class TestResource {
        @POST
        @Path("multipart")
        @Consumes("multipart/form-data")
        public Response multipart(MultipartFormDataInput body) {
            return Response.ok().build();
        }

        @GET
        public Response ok() {
            return Response.ok(new ErrorResponse("OK", "OK")).build();
        }
        @POST
        @Path("input")
        @Consumes("application/json")
        public Response input(ErrorResponse body) {
            return Response.ok(body).build();
        }
        @GET
        @Path("failure")
        public Response failure() {
            throw new IllegalStateException("SECRET_MARKER");
        }
        @GET
        @Path("checked")
        public Response checked() throws Exception {
            throw new Exception("SECRET_MARKER");
        }
        @GET
        @Path("entity")
        public Response entity() {
            throw new WebApplicationException(
                    Response.status(400).entity("SECRET_MARKER").header("X-Debug", "SECRET_MARKER").build());
        }
        @GET
        @Path("forged")
        public Response forged() {
            return Response.status(400).entity(new ErrorResponse("INVALID_UPLOAD", "SECRET_MARKER")).build();
        }
        @GET
        @Path("redirect")
        public Response redirect() {
            return Response.seeOther(URI.create("/test")).build();
        }
        @GET
        @Path("empty")
        public Response empty() {
            return Response.noContent().build();
        }
        @GET
        @Path("unmodified")
        public Response unmodified() {
            return Response.notModified().build();
        }
    }

}
