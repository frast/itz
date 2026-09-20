package de.itz.domain.file;

public class InvalidFileNameException extends IllegalArgumentException {
    public InvalidFileNameException() {
        super("A file name must be nonblank, valid Unicode, contain at most 255 Unicode code points "
                + "and contain no path components or forbidden characters");
    }
}
