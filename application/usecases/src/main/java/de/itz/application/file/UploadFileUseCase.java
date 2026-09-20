package de.itz.application.file;

import de.itz.domain.file.UploadedFile;

public interface UploadFileUseCase {
    UploadedFile execute(FileContent content);
}
