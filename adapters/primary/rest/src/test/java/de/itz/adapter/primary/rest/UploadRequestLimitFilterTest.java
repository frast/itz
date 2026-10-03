package de.itz.adapter.primary.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

class UploadRequestLimitFilterTest {
    @Test
    void boundsUnknownLengthRequestsWithoutLoadingTheirEntireContent() throws IOException {
        Source source = new Source(UploadRequestLimitFilter.MAX_REQUEST_SIZE + 100);
        try (var input = new UploadRequestLimitFilter.BoundedInputStream(source)) {
            byte[] buffer = new byte[8192];
            long read = 0;
            while (read < UploadRequestLimitFilter.MAX_REQUEST_SIZE) {
                read += input.read(buffer);
            }
            assertEquals(UploadRequestLimitFilter.MAX_REQUEST_SIZE, read);
            assertThrows(PayloadTooLargeException.class, input::read);
            assertEquals(99, source.remaining);
        }
    }

    @Test
    void acceptsExactTransportLimitAndEmptyContent() throws IOException {
        for (long size : new long[]{0, UploadRequestLimitFilter.MAX_REQUEST_SIZE}) {
            try (var input = new UploadRequestLimitFilter.BoundedInputStream(new Source(size))) {
                assertEquals(size, input.transferTo(java.io.OutputStream.nullOutputStream()));
            }
        }
    }

    @Test
    void readerLimitIsNotReportedAsMalformedInput() {
        var mapper = new RequestBodyExceptionMapper();
        try (var response = mapper
                .toResponse(new org.jboss.resteasy.spi.ReaderException(new PayloadTooLargeException()))) {
            assertEquals(413, response.getStatus());
        }
    }

    private static class Source extends ServletInputStream {
        private long remaining;

        Source(long remaining) {
            this.remaining = remaining;
        }

        @Override
        public int read() {
            return remaining-- > 0 ? 0 : -1;
        }

        @Override
        public int read(@SuppressWarnings("null") byte[] bytes, int offset, int length) {
            if (length == 0)
                return 0;
            if (remaining <= 0)
                return -1;
            int count = (int) Math.min(remaining, length);
            remaining -= count;
            return count;
        }

        @Override
        public boolean isFinished() {
            return remaining <= 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(@SuppressWarnings("null") ReadListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
