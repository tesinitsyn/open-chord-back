package com.openchord.server.admin;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/imports")
/** Two-phase album-import HTTP adapter: analyze first, then commit an edited draft. */
public class AlbumImportController {
    /** Import workflow responsible for staging, probing, conversion, and persistence. */
    private final AlbumImportService imports;

    /** Creates the controller for the album-import workflow. */
    public AlbumImportController(AlbumImportService imports) {
        this.imports = imports;
    }

    @PostMapping(path = "/analyze", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    /** Stages uploaded files and returns editable metadata without mutating the catalog. */
    public ImportDraft analyze(@RequestParam List<MultipartFile> files)
            throws IOException, InterruptedException {
        return imports.analyze(files);
    }

    @PostMapping("/{id}/commit")
    @ResponseStatus(HttpStatus.CREATED)
    /** Commits an analyzed draft after applying administrator corrections. */
    public ImportResult commit(@PathVariable UUID id, @RequestBody CommitImport request)
            throws IOException, InterruptedException {
        return imports.commit(id, request);
    }

    /** Editable album-level analysis result and its opaque staging identifier. */
    public record ImportDraft(
            UUID id,
            String artist,
            String album,
            int year,
            String artworkFile,
            List<ImportTrack> tracks,
            List<String> issues) {
    }

    /** Detected metadata and conversion plan for one staged audio file. */
    public record ImportTrack(
            String stagedFile,
            String originalFilename,
            String title,
            int discNumber,
            int number,
            long durationMs,
            String sourceFormat,
            boolean willTranscode,
            List<String> issues) {
    }

    /** Administrator-approved album metadata submitted during commit. */
    public record CommitImport(
            String artist, String album, int year, String artworkFile, List<CommitTrack> tracks) {
    }

    /** Administrator-approved metadata for one staged track. */
    public record CommitTrack(
            String stagedFile,
            String title,
            int discNumber,
            int number,
            long durationMs,
            String sourceFormat) {
    }

    /** Summary returned after a successful catalog commit. */
    public record ImportResult(
            UUID albumId, String album, int importedTracks, int transcodedTracks) {
    }
}
