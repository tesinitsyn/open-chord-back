package com.openchord.server.media;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.PARTIAL_CONTENT;

import com.openchord.server.catalog.Album;
import com.openchord.server.catalog.AlbumRepository;
import com.openchord.server.catalog.Track;
import com.openchord.server.catalog.TrackRepository;
import com.openchord.server.config.OpenChordProperties;
import com.openchord.server.playlist.Playlist;
import com.openchord.server.playlist.PlaylistRepository;

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

/**
 * Serves managed audio and artwork files.
 *
 * <p>Audio responses support either a complete representation or one HTTP byte range so native
 * players can seek without downloading an entire track. Multiple ranges and unsatisfiable ranges
 * return {@code 416}. Stored paths are resolved and validated below the configured media root
 * before a resource is exposed.
 */
@RestController
@RequestMapping("/media")
public class MediaController {
    private final TrackRepository tracks;
    private final AlbumRepository albums;
    private final PlaylistRepository playlists;
    private final Path mediaRoot;

    public MediaController(
            TrackRepository tracks,
            AlbumRepository albums,
            PlaylistRepository playlists,
            OpenChordProperties properties) {
        this.tracks = tracks;
        this.albums = albums;
        this.playlists = playlists;
        this.mediaRoot = properties.mediaRoot().toAbsolutePath().normalize();
    }

    /**
     * Streams a track, optionally limiting the response to one byte range.
     *
     * @param id          catalog identifier of the track
     * @param rangeHeader optional HTTP {@code Range} header
     * @return {@code 200} for the complete file, {@code 206} for one satisfiable range, or {@code
     * 416} for a malformed, multiple, or unsatisfiable range
     * @throws IOException if the managed file cannot be inspected
     */
    @GetMapping("/tracks/{id}")
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

    /**
     * Returns artwork for an album.
     *
     * @param id catalog identifier of the album
     * @return the artwork resource with its detected media type
     * @throws IOException if the managed file cannot be inspected
     */
    @GetMapping("/artwork/{id}")
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

    /** Returns user-supplied artwork for a playlist. */
    @GetMapping("/playlist-artwork/{id}")
    public ResponseEntity<Resource> playlistArtwork(@PathVariable UUID id) throws IOException {
        Playlist playlist =
                playlists
                        .findById(id)
                        .filter(value -> value.getArtworkPath() != null)
                        .orElseThrow(
                                () -> new ResponseStatusException(NOT_FOUND, "Artwork not found"));
        Resource resource = resource(playlist.getArtworkPath(), "Artwork file unavailable");
        MediaType contentType = MediaType.parseMediaType(playlist.getArtworkContentType());
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(resource.contentLength())
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1)).cachePublic())
                .body(resource);
    }

    private Resource resource(String storedPath, String unavailableMessage) {
        Path path = mediaRoot.resolve(storedPath).normalize();
        if (!path.startsWith(mediaRoot) || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(NOT_FOUND, unavailableMessage);
        }
        return new FileSystemResource(path);
    }
}
