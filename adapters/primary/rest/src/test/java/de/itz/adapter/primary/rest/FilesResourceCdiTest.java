package de.itz.adapter.primary.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;

import org.jboss.resteasy.spi.util.PickConstructor;
import org.jboss.weld.junit5.auto.ActivateScopes;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;

import de.itz.application.file.FileContent;
import de.itz.application.file.UploadFileUseCase;
import de.itz.domain.file.UploadedFile;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@EnableAutoWeld
@ActivateScopes(RequestScoped.class)
@AddBeanClasses({FilesResource.class, FilesResourceCdiTest.AcceptingUpload.class})
class FilesResourceCdiTest {
    @Inject
    private FilesResource resource;

    @Test
    void invokesInjectedUseCaseThroughClientProxy() {
        assertNotNull(PickConstructor.pickPerRequestConstructor(FilesResource.class));
        EntityPart part = (EntityPart) Proxy.newProxyInstance(EntityPart.class.getClassLoader(),
                new Class<?>[]{EntityPart.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getFileName" -> Optional.of("test.txt");
                    case "getMediaType" -> MediaType.TEXT_PLAIN_TYPE;
                    case "getContent" -> new ByteArrayInputStream(new byte[0]);
                    default -> throw new AssertionError("Unexpected multipart operation");
                });

        try (Response response = resource.uploadFile(part)) {
            assertEquals(201, response.getStatus());
        }
    }

    @Dependent
    public static class AcceptingUpload implements UploadFileUseCase {
        @Override
        public UploadedFile execute(FileContent content) {
            return new UploadedFile(UUID.randomUUID(), content.filename(), content.contentType(), 0);
        }
    }
}
