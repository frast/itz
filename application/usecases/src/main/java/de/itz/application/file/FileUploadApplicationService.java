package de.itz.application.file;

import java.util.Objects;

import de.itz.application.context.ApplicationService;
import de.itz.application.security.CurrentUser;
import de.itz.application.security.CurrentUserContext;
import de.itz.application.security.ForbiddenException;
import de.itz.application.security.Role;
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
        CurrentUser user = currentUserContext.currentUser();
        if (!user.hasRole(Role.USER)) {
            throw new ForbiddenException("The current user is not allowed to upload files");
        }

        return fileStorage.store(content);
    }
}
