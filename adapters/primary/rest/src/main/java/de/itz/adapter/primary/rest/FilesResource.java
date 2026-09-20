package de.itz.adapter.primary.rest;

import java.io.IOException;

import org.jspecify.annotations.Nullable;

import de.itz.adapter.primary.rest.generated.api.FilesApi;
import de.itz.adapter.primary.rest.generated.model.FileResponse;
import de.itz.application.file.FileContent;
import de.itz.application.file.UploadFileUseCase;
import de.itz.domain.file.UploadedFile;
import de.itz.domain.file.FileName;
import de.itz.domain.file.ContentType;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.Response;

@RequestScoped
@Path("/files")
public class FilesResource implements FilesApi {
    @Inject
    private UploadFileUseCase uploadFileUseCase;

    @Override
    @POST
    @Consumes("multipart/form-data")
    @Produces("application/json")
    public Response uploadFile(@FormParam("file") @Nullable EntityPart file) {
        if (file == null) {
            throw new InvalidUploadException("The file part is required");
        }
        FileName filename = new FileName(file.getFileName().orElse("upload.bin"));
        ContentType contentType = new ContentType(
                file.getMediaType() == null ? "application/octet-stream" : file.getMediaType().toString());
        try {
            UploadedFile uploaded;
            try (var content = file.getContent()) {
                uploaded = uploadFileUseCase.execute(
                        new FileContent(filename, contentType, content));
            }
            FileResponse response = new FileResponse(uploaded.id(), uploaded.filename().value(),
                    uploaded.contentType().value(),
                    uploaded.size(), FileResponse.StatusEnum.STORED);
            return Response.status(Response.Status.CREATED).entity(response).build();
        } catch (IOException exception) {
            throw new InvalidUploadException("The upload could not be read");
        }
    }
}
