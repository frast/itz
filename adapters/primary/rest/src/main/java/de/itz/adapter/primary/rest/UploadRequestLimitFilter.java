package de.itz.adapter.primary.rest;

import java.io.IOException;
import java.util.Objects;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/** Bounds the entire upload request, including extra parts and multipart overhead. */
public class UploadRequestLimitFilter implements Filter {
    static final long MAX_REQUEST_SIZE = 27L * 1024 * 1024;

    @Override
    public void doFilter(
            @SuppressWarnings("null") ServletRequest request,
            @SuppressWarnings("null") ServletResponse response,
            @SuppressWarnings("null") FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        HttpServletResponse output = (HttpServletResponse) response;
        if (http.getContentLengthLong() > MAX_REQUEST_SIZE) {
            output.sendError(413);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(http) {
            private @org.jspecify.annotations.Nullable ServletInputStream bounded;

            @Override
            public ServletInputStream getInputStream() throws IOException {
                var b = bounded;
                if (b == null) {
                    b = new BoundedInputStream(super.getInputStream());
                }
                bounded = b;
                return b;
            }
        }, response);
    }

    static final class BoundedInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private long count;

        BoundedInputStream(ServletInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            if (count > MAX_REQUEST_SIZE) {
                throw new PayloadTooLargeException();
            }
            int value = delegate.read();
            if (value >= 0) {
                record(1);
            }
            return value;
        }

        @Override
        public int read(@SuppressWarnings("null") byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) {
                return 0;
            }
            if (count > MAX_REQUEST_SIZE) {
                throw new PayloadTooLargeException();
            }
            // Never ask the container to read beyond our cap before we can classify the failure.
            int read = delegate.read(bytes, offset, (int) Math.min(length, MAX_REQUEST_SIZE - count + 1));
            if (read > 0) {
                record(read);
            }
            return read;
        }

        private void record(int bytes) {
            count += bytes;
            if (count > MAX_REQUEST_SIZE) {
                throw new PayloadTooLargeException();
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(@SuppressWarnings("null") ReadListener listener) {
            delegate.setReadListener(listener);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
