package de.itz.application.file;

public class FileUploadException extends RuntimeException {
    public FileUploadException(Throwable cause) {
        super("The file could not be stored", cause);
    }
}
