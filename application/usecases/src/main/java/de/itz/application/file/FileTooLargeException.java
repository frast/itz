package de.itz.application.file;

public class FileTooLargeException extends RuntimeException {
    public FileTooLargeException() {
        super("The uploaded file exceeds the maximum allowed size");
    }
}
