package com.openchord.server.media;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.PARTIAL_CONTENT;

import com.openchord.server.catalog.Album;
import com.openchord.server.catalog.AlbumRepository;
import com.openchord.server.catalog.Track;
import com.openchord.server.catalog.TrackRepository;
import com.openchord.server.config.OpenChordProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/media")
/** Serves managed audio and artwork without exposing filesystem paths. */
public class MediaController {
    /** Track metadata source used to authorize audio identifiers and locate files. */
    private final TrackRepository tracks;
    /** Album metadata source used to authorize artwork identifiers and locate files. */
    private final AlbumRepository albums;
    /** Normalized boundary for every path resolved by this controller. */
    private final Path mediaRoot;

    /** Creates the media adapter and normalizes the configured root once. */
    public MediaController(
            TrackRepository tracks, AlbumRepository albums, OpenChordProperties properties) {
        this.tracks = tracks;
        this.albums = albums;
        this.mediaRoot = properties.mediaRoot().toAbsolutePath().normalize();
    }

    @GetMapping("/tracks/{id}")
    /**
     * Streams a complete track or one byte range.
     *
     * <p>Single-range support is sufficient for AVPlayer seeking and avoids buffering full files.
     */
    public ResponseEntity<StreamingResponseBody> track(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader)
            throws IOException {
        Track track =
                tracks
                        .findById(id)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Track not found"));
        Resource resource = resource(track.getAudioPath(), "Audio file unavailable");
        MediaType contentType = MediaType.parseMediaType(track.getContentType());

        if (rangeHeader == null) {
            StreamingResponseBody body =
                    output -> {
                        try (InputStream input = resource.getInputStream()) {
                            input.transferTo(output);
                        }
                    };
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .contentLength(resource.contentLength())
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePrivate())
                    .body(body);
        }

        List<HttpRange> ranges;
        try {
            ranges = HttpRange.parseRanges(rangeHeader);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(416)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + resource.contentLength())
                    .build();
        }
        if (ranges.size() != 1) {
            return ResponseEntity.status(416)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + resource.contentLength())
                    .build();
        }
        long contentLength = resource.contentLength();
        long start;
        long end;
        try {
            start = ranges.getFirst().getRangeStart(contentLength);
            end = ranges.getFirst().getRangeEnd(contentLength);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(416)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                    .build();
        }
        long rangeLength = end - start + 1;
        StreamingResponseBody body =
                output -> {
                    try (InputStream input = resource.getInputStream()) {
                        input.skipNBytes(start);
                        byte[] buffer = new byte[64 * 1024];
                        long remaining = rangeLength;
                        while (remaining > 0) {
                            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                            if (read < 0) {
                                break;
                            }
                            output.write(buffer, 0, read);
                            remaining -= read;
                        }
                    }
                };
        return ResponseEntity.status(PARTIAL_CONTENT)
                .contentType(contentType)
                .contentLength(rangeLength)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes %d-%d/%d".formatted(start, end, contentLength))
                .body(body);
    }

    @GetMapping("/artwork/{id}")
    /** Returns cacheable album artwork with a filesystem-detected content type. */
    public ResponseEntity<Resource> artwork(@PathVariable UUID id) throws IOException {
        Album album =
                albums
                        .findById(id)
                        .filter(value -> value.getArtworkPath() != null)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Artwork not found"));
        Resource resource = resource(album.getArtworkPath(), "Artwork file unavailable");
        String detected = Files.probeContentType(resource.getFile().toPath());
        MediaType contentType =
                detected == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(detected);
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(resource.contentLength())
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1)).cachePublic())
                .body(resource);
    }

    /** Resolves a managed relative path and rejects traversal or missing files. */
    private Resource resource(String storedPath, String unavailableMessage) {
        Path path = mediaRoot.resolve(storedPath).normalize();
        if (!path.startsWith(mediaRoot) || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(NOT_FOUND, unavailableMessage);
        }
        return new FileSystemResource(path);
    }
}
