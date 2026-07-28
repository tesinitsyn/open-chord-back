package com.openchord.server.admin;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Administration transport for portable OpenChord archives.
 *
 * <p>Exports stream directly to the client so a large library is never buffered as one byte array.
 * Imports treat the uploaded ZIP as untrusted input and delegate validation to {@link
 * OpenChordArchiveService}.
 */
@RestController
@RequestMapping("/api/admin/openchord")
public class OpenChordArchiveController {
    public static final MediaType OPENCHORD =
            MediaType.parseMediaType("application/vnd.openchord.archive+zip");

    private final OpenChordArchiveService archives;

    public OpenChordArchiveController(OpenChordArchiveService archives) {
        this.archives = archives;
    }

    @GetMapping("/playlists")
    public List<PlaylistOption> playlists() {
        return archives.playlistOptions();
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam(defaultValue = "library") String scope,
            @RequestParam(required = false) UUID playlistId) {
        OpenChordArchiveService.ExportRequest request =
                OpenChordArchiveService.ExportRequest.parse(scope, playlistId);
        StreamingResponseBody body = output -> archives.export(request, output);
        String filename =
                request.playlistId() == null
                        ? "openchord-library.openchord"
                        : "openchord-playlist-%s.openchord".formatted(request.playlistId());
        return ResponseEntity.ok()
                .contentType(OPENCHORD)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportSummary importArchive(@RequestParam MultipartFile archive) throws IOException {
        return archives.importArchive(archive);
    }

    /** Playlist choice displayed by archive export clients. */
    public record PlaylistOption(UUID id, String name, int tracks) {
    }

    /** Result of a committed archive import. */
    public record ImportSummary(int albums, int tracks, int playlists, int skippedAlbums) {
    }
}
