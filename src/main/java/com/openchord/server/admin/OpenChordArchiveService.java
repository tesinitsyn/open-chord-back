package com.openchord.server.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openchord.server.admin.OpenChordArchiveController.ImportSummary;
import com.openchord.server.admin.OpenChordArchiveController.PlaylistOption;
import com.openchord.server.catalog.Album;
import com.openchord.server.catalog.AlbumRepository;
import com.openchord.server.catalog.Artist;
import com.openchord.server.catalog.ArtistRepository;
import com.openchord.server.catalog.Track;
import com.openchord.server.config.OpenChordProperties;
import com.openchord.server.playlist.Playlist;
import com.openchord.server.playlist.PlaylistRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.Year;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Reads and writes the portable OpenChord Archive Format draft {@code 0.1}. */
@Service
public class OpenChordArchiveService {
    private static final String VERSION = "0.1";
    private static final long MAX_ENTRY_BYTES = 20L * 1024 * 1024 * 1024;
    private static final long MAX_ARCHIVE_BYTES = 100L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRIES = 100_000;
    private static final int MAX_METADATA_BYTES = 64 * 1024 * 1024;

    private final AlbumRepository albums;
    private final ArtistRepository artists;
    private final PlaylistRepository playlists;
    private final ObjectMapper json = new ObjectMapper();
    private final Path mediaRoot;

    public OpenChordArchiveService(
            AlbumRepository albums,
            ArtistRepository artists,
            PlaylistRepository playlists,
            OpenChordProperties properties) {
        this.albums = albums;
        this.artists = artists;
        this.playlists = playlists;
        this.mediaRoot = properties.mediaRoot().toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public List<PlaylistOption> playlistOptions() {
        return playlists.findAllDetailed().stream()
                .map(value -> new PlaylistOption(value.getId(), value.getName(), value.getEntries().size()))
                .toList();
    }

    /**
     * Streams a self-contained archive of the full library or one playlist.
     *
     * <p>Current managed audio is described as {@code playable}. OpenChord cannot prove that older
     * media is byte-identical to the file originally uploaded because the legacy album importer
     * transcodes some lossless formats.
     */
    @Transactional(readOnly = true)
    public void export(ExportRequest request, OutputStream output) throws IOException {
        List<Playlist> selectedPlaylists;
        List<Album> selectedAlbums;
        if (request.playlistId() == null) {
            selectedPlaylists = playlists.findAllDetailed();
            selectedAlbums = albums.findAllDetailed();
        } else {
            Playlist playlist =
                    playlists
                            .findDetailedById(request.playlistId())
                            .orElseThrow(() -> new IllegalArgumentException("Playlist not found"));
            selectedPlaylists = List.of(playlist);
            selectedAlbums =
                    playlist.getEntries().stream()
                            .map(entry -> entry.getTrack().getAlbum())
                            .collect(
                                    java.util.stream.Collectors.collectingAndThen(
                                            java.util.stream.Collectors.toMap(
                                                    Album::getId,
                                                    value -> value,
                                                    (left, right) -> left,
                                                    LinkedHashMap::new),
                                            map -> List.copyOf(map.values())));
        }

        Map<String, Asset> assets = collectAssets(selectedAlbums, selectedPlaylists);
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            writeJson(zip, "manifest.json", manifest(request, selectedPlaylists));
            writeJsonLines(
                    zip,
                    "catalog/artists.jsonl",
                    selectedAlbums.stream()
                            .map(Album::getArtist)
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            Artist::getId,
                                            this::artistRecord,
                                            (left, right) -> left,
                                            LinkedHashMap::new))
                            .values());
            writeJsonLines(
                    zip, "catalog/albums.jsonl", selectedAlbums.stream().map(this::albumRecord).toList());
            writeJsonLines(
                    zip,
                    "catalog/tracks.jsonl",
                    selectedAlbums.stream()
                            .flatMap(album -> album.getTracks().stream())
                            .map(this::trackRecord)
                            .toList());
            writeJsonLines(
                    zip,
                    "catalog/playlists.jsonl",
                    selectedPlaylists.stream().map(this::playlistRecord).toList());
            writeJsonLines(
                    zip,
                    "catalog/assets.jsonl",
                    assets.values().stream().map(this::assetRecord).toList());
            for (Asset asset : assets.values()) {
                zip.putNextEntry(new ZipEntry(asset.archivePath()));
                Files.copy(asset.source(), zip);
                zip.closeEntry();
            }
        }
    }

    /**
     * Validates and commits one uploaded archive.
     *
     * <p>Album title and artist are used as the current catalog's conflict boundary. Existing
     * albums are left untouched and reported as skipped.
     */
    @Transactional
    public ImportSummary importArchive(MultipartFile upload) throws IOException {
        if (upload == null || upload.isEmpty()) {
            throw new IllegalArgumentException("Choose a .openchord archive");
        }
        try (ImportedArchive archive = readArchive(upload)) {
            requireManifest(archive.json("manifest.json", json));

            Map<String, JsonNode> artistRecords = records(archive, "catalog/artists.jsonl");
            Map<String, JsonNode> albumRecords = records(archive, "catalog/albums.jsonl");
            Map<String, JsonNode> trackRecords = records(archive, "catalog/tracks.jsonl");
            Map<String, JsonNode> playlistRecords = records(archive, "catalog/playlists.jsonl");
            Map<String, JsonNode> assetRecords = records(archive, "catalog/assets.jsonl");

            Map<String, Artist> importedArtists = new HashMap<>();
            Map<String, Track> importedTracks = new HashMap<>();
            int importedAlbumCount = 0;
            int importedTrackCount = 0;
            int skippedAlbumCount = 0;

            List<JsonNode> orderedAlbums =
                    albumRecords.values().stream()
                            .sorted(Comparator.comparing(value -> requiredText(value, "title")))
                            .toList();
            for (JsonNode albumRecord : orderedAlbums) {
            JsonNode ownerCredit =
                    firstCredit(albumRecord.path("credits"), "album-artist", "primary");
            String artistId = requiredText(ownerCredit, "artistId");
            JsonNode artistRecord =
                    requireRecord(artistRecords, artistId, "album artist");
            Artist artist =
                    importedArtists.computeIfAbsent(
                            artistId,
                            ignored ->
                                    artists
                                            .findFirstByNameIgnoreCase(requiredText(artistRecord, "name"))
                                            .orElseGet(
                                                    () ->
                                                            artists.save(
                                                                    new Artist(
                                                                            requiredText(
                                                                                    artistRecord,
                                                                                    "name")))));
            String title = requiredText(albumRecord, "title");
            var existingAlbum = albums.findFirstByArtistAndTitleIgnoreCase(artist, title);
            if (existingAlbum.isPresent()) {
                Album existing = albums.findDetailedById(existingAlbum.get().getId()).orElseThrow();
                for (JsonNode trackIdNode : requiredArray(albumRecord, "trackIds")) {
                    JsonNode trackRecord =
                            requireRecord(trackRecords, trackIdNode.asText(), "track");
                    existing.getTracks().stream()
                            .filter(
                                    candidate ->
                                            candidate.getDiscNumber()
                                                            == requiredPositiveInt(
                                                                    trackRecord, "discNumber")
                                                    && candidate.getNumber()
                                                            == requiredPositiveInt(
                                                                    trackRecord, "trackNumber"))
                            .findFirst()
                            .ifPresent(
                                    candidate ->
                                            importedTracks.put(trackIdNode.asText(), candidate));
                }
                skippedAlbumCount++;
                continue;
            }

            int year = releaseYear(albumRecord.path("releaseDate").asText(""));
            String artworkPath = null;
            JsonNode artwork = albumRecord.path("artwork");
            if (artwork.isArray() && !artwork.isEmpty()) {
                String assetId = requiredText(artwork.get(0), "assetId");
                artworkPath = storeAsset(archive, requireRecord(assetRecords, assetId, "artwork"), "artwork");
            }
            Album album = new Album(title, year, artworkPath, artist);
            for (JsonNode trackIdNode : requiredArray(albumRecord, "trackIds")) {
                String trackId = trackIdNode.asText();
                JsonNode trackRecord = requireRecord(trackRecords, trackId, "track");
                JsonNode media = preferredMedia(requiredArray(trackRecord, "media"));
                String assetId = requiredText(media, "assetId");
                JsonNode asset = requireRecord(assetRecords, assetId, "track media");
                String audioPath = storeAsset(archive, asset, "tracks");
                Track track =
                        new Track(
                                requiredText(trackRecord, "title"),
                                requiredPositiveLong(trackRecord, "durationMs"),
                                requiredPositiveInt(trackRecord, "discNumber"),
                                requiredPositiveInt(trackRecord, "trackNumber"),
                                audioPath,
                                requiredText(asset, "mediaType"));
                album.addTrack(track);
                importedTracks.put(trackId, track);
                importedTrackCount++;
            }
            albums.saveAndFlush(album);
            importedAlbumCount++;
            }

            int importedPlaylistCount = 0;
            for (JsonNode playlistRecord : playlistRecords.values()) {
            Playlist playlist =
                    new Playlist(
                            requiredText(playlistRecord, "name"),
                            playlistRecord.path("description").asText(""),
                            Instant.now());
            Set<UUID> included = new HashSet<>();
            for (JsonNode entry : requiredArray(playlistRecord, "entries")) {
                Track track = importedTracks.get(requiredText(entry, "trackId"));
                if (track != null && included.add(track.getId())) {
                    playlist.addTrack(track, Instant.now());
                }
            }
            playlists.saveAndFlush(playlist);
                importedPlaylistCount++;
            }
            return new ImportSummary(
                    importedAlbumCount, importedTrackCount, importedPlaylistCount, skippedAlbumCount);
        }
    }

    private ObjectNode manifest(ExportRequest request, List<Playlist> selectedPlaylists) {
        ObjectNode manifest = json.createObjectNode();
        manifest.put("format", "openchord");
        manifest.put("formatVersion", VERSION);
        manifest.put("createdAt", Instant.now().toString());
        manifest.put("scope", request.playlistId() == null ? "library" : "playlist");
        manifest.put("mediaPolicy", "playable");
        ArrayNode roots = manifest.putArray("rootIds");
        selectedPlaylists.forEach(playlist -> roots.add(id("playlist", playlist.getId())));
        ObjectNode generator = manifest.putObject("generator");
        generator.put("name", "OpenChord Server");
        generator.put("version", "0.0.1");
        return manifest;
    }

    private ObjectNode artistRecord(Artist artist) {
        ObjectNode record = json.createObjectNode();
        record.put("id", id("artist", artist.getId()));
        record.put("name", artist.getName());
        return record;
    }

    private ObjectNode albumRecord(Album album) {
        ObjectNode record = json.createObjectNode();
        record.put("id", id("album", album.getId()));
        record.put("title", album.getTitle());
        record.put("type", "album");
        record.put("releaseDate", Integer.toString(album.getReleaseYear()));
        credit(record.putArray("credits"), album.getArtist(), "album-artist");
        ArrayNode tracks = record.putArray("trackIds");
        album.getTracks().forEach(track -> tracks.add(id("track", track.getId())));
        if (album.getArtworkPath() != null) {
            ObjectNode artwork = record.putArray("artwork").addObject();
            artwork.put("assetId", assetId(resolveMedia(album.getArtworkPath())));
            artwork.put("purpose", "front");
        }
        return record;
    }

    private ObjectNode trackRecord(Track track) {
        ObjectNode record = json.createObjectNode();
        record.put("id", id("track", track.getId()));
        record.put("title", track.getTitle());
        record.put("albumId", id("album", track.getAlbum().getId()));
        record.put("discNumber", track.getDiscNumber());
        record.put("trackNumber", track.getNumber());
        record.put("durationMs", track.getDurationMs());
        credit(record.putArray("credits"), track.getAlbum().getArtist(), "primary");
        ObjectNode media = record.putArray("media").addObject();
        media.put("assetId", assetId(resolveMedia(track.getAudioPath())));
        media.put("purpose", "playable");
        media.put("preferred", true);
        return record;
    }

    private ObjectNode playlistRecord(Playlist playlist) {
        ObjectNode record = json.createObjectNode();
        record.put("id", id("playlist", playlist.getId()));
        record.put("name", playlist.getName());
        record.put("description", playlist.getDescription());
        record.put("createdAt", playlist.getCreatedAt().toString());
        record.put("updatedAt", playlist.getUpdatedAt().toString());
        if (playlist.getArtworkPath() != null) {
            record.put("artworkAssetId", assetId(resolveMedia(playlist.getArtworkPath())));
        }
        ArrayNode entries = record.putArray("entries");
        playlist.getEntries()
                .forEach(
                        entry -> {
                            ObjectNode item = entries.addObject();
                            item.put("id", id("playlist-entry", entry.getId()));
                            item.put("trackId", id("track", entry.getTrack().getId()));
                        });
        return record;
    }

    private void credit(ArrayNode credits, Artist artist, String role) {
        ObjectNode credit = credits.addObject();
        credit.put("artistId", id("artist", artist.getId()));
        credit.put("role", role);
    }

    private Map<String, Asset> collectAssets(
            List<Album> selectedAlbums, List<Playlist> selectedPlaylists) throws IOException {
        Map<String, Asset> result = new LinkedHashMap<>();
        Consumer<String> include =
                relative -> {
                    if (relative == null) return;
                    try {
                        Path path = resolveMedia(relative);
                        String digest = sha256(path);
                        result.putIfAbsent(
                                digest,
                                new Asset(
                                        digest,
                                        path,
                                        "assets/sha256/%s/%s%s"
                                                .formatted(
                                                        digest.substring(0, 2),
                                                        digest,
                                                        extension(path.getFileName().toString())),
                                        mediaType(path)));
                    } catch (IOException exception) {
                        throw new ArchiveIoException(exception);
                    }
                };
        try {
            selectedAlbums.forEach(
                    album -> {
                        include.accept(album.getArtworkPath());
                        album.getTracks().forEach(track -> include.accept(track.getAudioPath()));
                    });
            selectedPlaylists.forEach(playlist -> include.accept(playlist.getArtworkPath()));
        } catch (ArchiveIoException exception) {
            throw exception.cause;
        }
        return result;
    }

    private ObjectNode assetRecord(Asset asset) {
        ObjectNode record = json.createObjectNode();
        record.put("id", "asset." + asset.digest());
        record.put("availability", "embedded");
        record.put("path", asset.archivePath());
        record.put("sha256", asset.digest());
        try {
            record.put("byteLength", Files.size(asset.source()));
        } catch (IOException exception) {
            throw new ArchiveIoException(exception);
        }
        record.put("mediaType", asset.mediaType());
        record.put("originalFilename", asset.source().getFileName().toString());
        return record;
    }

    private void writeJson(ZipOutputStream zip, String path, JsonNode value) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(json.writeValueAsBytes(value));
        zip.closeEntry();
    }

    private void writeJsonLines(ZipOutputStream zip, String path, Iterable<? extends JsonNode> values)
            throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        for (JsonNode value : values) {
            zip.write(json.writeValueAsBytes(value));
            zip.write('\n');
        }
        zip.closeEntry();
    }

    private ImportedArchive readArchive(MultipartFile upload) throws IOException {
        Path staging =
                mediaRoot.resolve(".archive-imports").resolve(UUID.randomUUID().toString()).normalize();
        Files.createDirectories(staging);
        Map<String, Path> entries = new HashMap<>();
        long total = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(upload.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_ENTRIES) throw new IllegalArgumentException("Archive has too many entries");
                if (entry.isDirectory()) continue;
                String path = safeArchivePath(entry.getName());
                if (entries.containsKey(path)) {
                    throw new IllegalArgumentException("Archive contains duplicate path: " + path);
                }
                long limit =
                        path.startsWith("catalog/") || path.equals("manifest.json")
                                ? MAX_METADATA_BYTES
                                : MAX_ENTRY_BYTES;
                Path target = staging.resolve("entry-" + count);
                byte[] buffer = new byte[64 * 1024];
                int read;
                long entryBytes = 0;
                try (OutputStream targetOutput = Files.newOutputStream(target)) {
                    while ((read = zip.read(buffer)) != -1) {
                        entryBytes += read;
                        total += read;
                        if (entryBytes > limit || total > MAX_ARCHIVE_BYTES) {
                            throw new IllegalArgumentException("Archive exceeds configured size limits");
                        }
                        targetOutput.write(buffer, 0, read);
                    }
                }
                entries.put(path, target);
            }
        } catch (Exception exception) {
            deleteTree(staging);
            throw exception;
        }
        return new ImportedArchive(staging, entries);
    }

    private Map<String, JsonNode> records(ImportedArchive archive, String path) throws IOException {
        Path content = archive.entries().get(path);
        if (content == null) return Map.of();
        Map<String, JsonNode> result = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(content, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) throw new IllegalArgumentException(path + " contains a blank line");
                JsonNode record = json.readTree(line);
                String id = requiredText(record, "id");
                if (result.putIfAbsent(id, record) != null) {
                    throw new IllegalArgumentException(path + " contains duplicate id: " + id);
                }
            }
        }
        return result;
    }

    private void requireManifest(JsonNode manifest) {
        if (!"openchord".equals(requiredText(manifest, "format"))) {
            throw new IllegalArgumentException("Not an OpenChord archive");
        }
        if (!VERSION.equals(requiredText(manifest, "formatVersion"))) {
            throw new IllegalArgumentException(
                    "Unsupported OpenChord format version: " + manifest.path("formatVersion").asText());
        }
    }

    private String storeAsset(ImportedArchive archive, JsonNode record, String directory)
            throws IOException {
        if (!"embedded".equals(requiredText(record, "availability"))) {
            throw new IllegalArgumentException("Required media asset is not embedded");
        }
        String path = safeArchivePath(requiredText(record, "path"));
        Path source = archive.entries().get(path);
        if (source == null) throw new IllegalArgumentException("Missing embedded asset: " + path);
        long expectedLength = record.path("byteLength").asLong(-1);
        if (expectedLength != Files.size(source))
            throw new IllegalArgumentException("Asset size mismatch: " + path);
        String digest = sha256(source);
        if (!digest.equals(requiredText(record, "sha256"))) {
            throw new IllegalArgumentException("Asset digest mismatch: " + path);
        }
        String relative =
                "%s/%s%s".formatted(directory, UUID.randomUUID(), extension(Path.of(path).getFileName().toString()));
        Path target = resolveMedia(relative);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return relative;
    }

    private JsonNode preferredMedia(ArrayNode media) {
        for (JsonNode candidate : media) {
            if (candidate.path("preferred").asBoolean()) return candidate;
        }
        if (media.isEmpty()) throw new IllegalArgumentException("Track has no embedded media");
        return media.get(0);
    }

    private JsonNode firstCredit(JsonNode credits, String... roles) {
        if (!credits.isArray()) throw new IllegalArgumentException("Album credits are required");
        for (String role : roles) {
            for (JsonNode credit : credits) {
                if (role.equals(credit.path("role").asText())) return credit;
            }
        }
        if (!credits.isEmpty()) return credits.get(0);
        throw new IllegalArgumentException("Album credits are empty");
    }

    private static ArrayNode requiredArray(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (!value.isArray()) throw new IllegalArgumentException(name + " must be an array");
        return (ArrayNode) value;
    }

    private static JsonNode requireRecord(Map<String, JsonNode> records, String id, String label) {
        JsonNode value = records.get(id);
        if (value == null) throw new IllegalArgumentException("Missing " + label + ": " + id);
        return value;
    }

    private static String requiredText(JsonNode node, String name) {
        String value = node.path(name).asText("").strip();
        if (value.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static int requiredPositiveInt(JsonNode node, String name) {
        int value = node.path(name).asInt(0);
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static long requiredPositiveLong(JsonNode node, String name) {
        long value = node.path(name).asLong(0);
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static int releaseYear(String value) {
        if (value.length() >= 4) {
            try {
                int year = Integer.parseInt(value.substring(0, 4));
                if (year >= 1000 && year <= 9999) return year;
            } catch (NumberFormatException ignored) {
                // Fall through to the current year for incomplete external metadata.
            }
        }
        return Year.now().getValue();
    }

    private Path resolveMedia(String relative) {
        Path resolved = mediaRoot.resolve(relative).normalize();
        if (!resolved.startsWith(mediaRoot)) throw new IllegalArgumentException("Invalid media path");
        return resolved;
    }

    private String assetId(Path path) {
        try {
            return "asset." + sha256(path);
        } catch (IOException exception) {
            throw new ArchiveIoException(exception);
        }
    }

    private static String id(String kind, UUID value) {
        return kind + "." + value;
    }

    private static String safeArchivePath(String value) {
        String normalized = value.replace('\\', '/');
        if (normalized.startsWith("/")
                || normalized.matches("^[A-Za-z]:.*")
                || normalized.contains("../")
                || normalized.equals("..")) {
            throw new IllegalArgumentException("Unsafe archive path");
        }
        Path path = Path.of(normalized).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            throw new IllegalArgumentException("Unsafe archive path");
        }
        return path.toString().replace('\\', '/');
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest = digest();
        try (DigestInputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return "";
        String value = filename.substring(dot).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "");
        return value.length() > 17 ? "" : value;
    }

    private static String mediaType(Path path) {
        try {
            String detected = Files.probeContentType(path);
            return detected == null ? "application/octet-stream" : detected;
        } catch (IOException exception) {
            return "application/octet-stream";
        }
    }

    public record ExportRequest(UUID playlistId) {
        static ExportRequest parse(String scope, UUID playlistId) {
            return switch (scope.toLowerCase(Locale.ROOT)) {
                case "library" -> {
                    if (playlistId != null) {
                        throw new IllegalArgumentException("playlistId is only valid for playlist scope");
                    }
                    yield new ExportRequest(null);
                }
                case "playlist" -> {
                    if (playlistId == null) {
                        throw new IllegalArgumentException("playlistId is required");
                    }
                    yield new ExportRequest(playlistId);
                }
                default -> throw new IllegalArgumentException("scope must be library or playlist");
            };
        }
    }

    private record Asset(
            String digest, Path source, String archivePath, String mediaType) {
    }

    private record ImportedArchive(Path staging, Map<String, Path> entries)
            implements AutoCloseable {
        JsonNode json(String path, ObjectMapper json) throws IOException {
            Path file = entries.get(path);
            if (file == null) throw new IllegalArgumentException("Missing " + path);
            return json.readTree(file.toFile());
        }

        @Override
        public void close() throws IOException {
            deleteTree(staging);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class ArchiveIoException extends RuntimeException {
        private final IOException cause;

        private ArchiveIoException(IOException cause) {
            super(cause);
            this.cause = cause;
        }
    }
}
