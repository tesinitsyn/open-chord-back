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

/**
 * HTTP API for the reviewable album import workflow.
 *
 * @see AlbumImportService
 */
@RestController
@RequestMapping("/api/admin/imports")
public class AlbumImportController {
    private final AlbumImportService imports;

    public AlbumImportController(AlbumImportService imports) {
        this.imports = imports;
    }

    @PostMapping(path = "/analyze", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportDraft analyze(@RequestParam List<MultipartFile> files)
            throws IOException, InterruptedException {
        return imports.analyze(files);
    }

    @PostMapping("/{id}/commit")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResult commit(@PathVariable UUID id, @RequestBody CommitImport request)
            throws IOException, InterruptedException {
        return imports.commit(id, request);
    }

    /**
     * Metadata detected during analysis and presented for review.
     *
     * @param id          opaque staging identifier required to commit the draft
     * @param artist      detected album artist
     * @param album       detected album title
     * @param year        detected release year
     * @param artworkFile opaque staged artwork filename, if one was selected
     * @param tracks      detected audio files
     * @param issues      album-level warnings that require review
     */
    public record ImportDraft(
            UUID id,
            String artist,
            String album,
            int year,
            String artworkFile,
            List<ImportTrack> tracks,
            List<String> issues) {
    }

    /**
     * Detected metadata and conversion plan for one staged audio file.
     *
     * @param stagedFile       opaque filename that must be returned unchanged during commit
     * @param originalFilename filename supplied by the client
     * @param title            detected or inferred title
     * @param discNumber       one-based disc number
     * @param number           one-based track number
     * @param durationMs       probed duration in milliseconds
     * @param sourceFormat     lowercase source extension
     * @param willTranscode    whether commit will normalize the source to ALAC
     * @param issues           warnings specific to this track
     */
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

    /**
     * Reviewed album metadata submitted for commit.
     *
     * @param artist      album artist
     * @param album       album title
     * @param year        release year
     * @param artworkFile selected opaque artwork filename, if any
     * @param tracks      reviewed tracks
     */
    public record CommitImport(
            String artist, String album, int year, String artworkFile, List<CommitTrack> tracks) {
    }

    /**
     * Reviewed metadata for one staged audio file.
     *
     * @param stagedFile   opaque filename returned during analysis
     * @param title        track title
     * @param discNumber   one-based disc number
     * @param number       one-based track number
     * @param durationMs   duration in milliseconds
     * @param sourceFormat lowercase source extension
     */
    public record CommitTrack(
            String stagedFile,
            String title,
            int discNumber,
            int number,
            long durationMs,
            String sourceFormat) {
    }

    /**
     * Summary returned after the reviewed import has been persisted.
     *
     * @param albumId          persisted album identifier
     * @param album            persisted album title
     * @param importedTracks   number of imported tracks
     * @param transcodedTracks number of sources converted to ALAC
     */
    public record ImportResult(
            UUID albumId, String album, int importedTracks, int transcodedTracks) {
    }
}
