package de.itz.application.file;

import java.util.Objects;

import de.itz.application.context.ApplicationService;
import de.itz.application.security.CurrentUserContext;
import de.itz.domain.file.UploadedFile;
import jakarta.inject.Inject;

@ApplicationService
public class FileUploadApplicationService implements UploadFileUseCase {
    private final CurrentUserContext currentUserContext;
    private final FileStorage fileStorage;

    @Inject
    public FileUploadApplicationService(CurrentUserContext currentUserContext, FileStorage fileStorage) {
        this.currentUserContext = currentUserContext;
        this.fileStorage = fileStorage;
    }

    @Override
    public UploadedFile execute(FileContent content) {
        Objects.requireNonNull(content);
        currentUserContext.currentUser();
        return fileStorage.store(content);
    }
}
