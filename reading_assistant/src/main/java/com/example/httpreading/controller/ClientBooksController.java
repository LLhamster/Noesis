package com.example.httpreading.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.example.httpreading.service.BookFileDownloadService;
import com.example.httpreading.service.BookFileDownloadService.BookFileDownload;

import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/client/books")
@Validated
public class ClientBooksController {

    private final BookFileDownloadService bookFileDownloadService;

    public ClientBooksController(BookFileDownloadService bookFileDownloadService) {
        this.bookFileDownloadService = bookFileDownloadService;
    }

    @GetMapping("/{bookId}/file")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable @Positive Long bookId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange) {
        BookFileDownload download = bookFileDownloadService.getDownload(bookId);
        HttpHeaders headers = responseHeaders(download);

        if (matchesEntityTag(ifNoneMatch, download.entityTag())) {
            return new ResponseEntity<>(headers, HttpStatus.NOT_MODIFIED);
        }

        if (rangeHeader == null || rangeHeader.isBlank()
                || (ifRange != null && !matchesIfRange(ifRange, download.entityTag()))) {
            headers.setContentLength(download.contentLength());
            return new ResponseEntity<>(stream(download.path(), 0, download.contentLength()),
                headers,
                HttpStatus.OK);
        }

        return partialResponse(download.path(), download.contentLength(), rangeHeader, headers);
    }

    private HttpHeaders responseHeaders(BookFileDownload download) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(download.mediaType()));
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(download.filename(), StandardCharsets.UTF_8)
            .build());
        headers.setETag(download.entityTag());
        headers.setCacheControl(CacheControl.noCache().cachePrivate());
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.set("X-Content-Type-Options", "nosniff");
        return headers;
    }

    private ResponseEntity<StreamingResponseBody> partialResponse(Path path,
                                              long contentLength,
                                              String rangeHeader,
                                              HttpHeaders headers) {
        try {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            if (ranges.size() != 1 || contentLength <= 0) {
                return rangeNotSatisfiable(contentLength, headers);
            }
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            if (start < 0 || start >= contentLength || end < start) {
                return rangeNotSatisfiable(contentLength, headers);
            }
            long count = end - start + 1;
            headers.set(HttpHeaders.CONTENT_RANGE,
                "bytes " + start + "-" + end + "/" + contentLength);
            headers.setContentLength(count);
            return new ResponseEntity<>(
                stream(path, start, count),
                headers,
                HttpStatus.PARTIAL_CONTENT);
        } catch (IllegalArgumentException e) {
            return rangeNotSatisfiable(contentLength, headers);
        }
    }

    private StreamingResponseBody stream(Path path, long start, long count) {
        return outputStream -> {
            try (InputStream inputStream = Files.newInputStream(path)) {
                inputStream.skipNBytes(start);
                byte[] buffer = new byte[8192];
                long remaining = count;
                while (remaining > 0) {
                    int read = inputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read < 0) {
                        throw new IOException("Source file ended before the declared content length");
                    }
                    outputStream.write(buffer, 0, read);
                    remaining -= read;
                }
            }
        };
    }

    private ResponseEntity<StreamingResponseBody> rangeNotSatisfiable(
            long contentLength,
            HttpHeaders headers) {
        headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength);
        headers.remove(HttpHeaders.CONTENT_LENGTH);
        return new ResponseEntity<>(headers, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
    }

    private boolean matchesEntityTag(String requestValue, String entityTag) {
        if (requestValue == null || requestValue.isBlank()) {
            return false;
        }
        for (String candidate : requestValue.split(",")) {
            String trimmed = candidate.trim();
            if ("*".equals(trimmed)
                    || stripWeakPrefix(trimmed).equals(stripWeakPrefix(entityTag))) {
                return true;
            }
        }
        return false;
    }

    private String stripWeakPrefix(String entityTag) {
        return entityTag.startsWith("W/") ? entityTag.substring(2) : entityTag;
    }

    private boolean matchesIfRange(String requestValue, String entityTag) {
        String candidate = requestValue.trim();
        return !candidate.startsWith("W/") && candidate.equals(entityTag);
    }
}
