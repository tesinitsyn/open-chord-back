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

  public record ImportDraft(
      UUID id,
      String artist,
      String album,
      int year,
      String artworkFile,
      List<ImportTrack> tracks,
      List<String> issues) {}

  public record ImportTrack(
      String stagedFile,
      String originalFilename,
      String title,
      int discNumber,
      int number,
      long durationMs,
      String sourceFormat,
      boolean willTranscode,
      List<String> issues) {}

  public record CommitImport(
      String artist, String album, int year, String artworkFile, List<CommitTrack> tracks) {}

  public record CommitTrack(
      String stagedFile,
      String title,
      int discNumber,
      int number,
      long durationMs,
      String sourceFormat) {}

  public record ImportResult(UUID albumId, String album, int importedTracks, int transcodedTracks) {}
}
