package com.openchord.server.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openchord.server.admin.AlbumImportController.CommitImport;
import com.openchord.server.admin.AlbumImportController.CommitTrack;
import com.openchord.server.admin.AlbumImportController.ImportDraft;
import com.openchord.server.admin.AlbumImportController.ImportResult;
import com.openchord.server.admin.AlbumImportController.ImportTrack;
import com.openchord.server.catalog.Album;
import com.openchord.server.catalog.AlbumRepository;
import com.openchord.server.catalog.Artist;
import com.openchord.server.catalog.ArtistRepository;
import com.openchord.server.catalog.Track;
import com.openchord.server.config.OpenChordProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AlbumImportService {
    private static final Set<String> AUDIO =
            Set.of("flac", "wav", "aiff", "aif", "m4a", "mp4", "mp3", "aac", "ogg", "opus");
    private static final Set<String> ARTWORK = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> LOSSLESS_TO_ALAC = Set.of("flac", "wav", "aiff", "aif");

    private final ArtistRepository artists;
    private final AlbumRepository albums;
    private final ObjectMapper json = new ObjectMapper();
    private final Path mediaRoot;

    public AlbumImportService(
            ArtistRepository artists, AlbumRepository albums, OpenChordProperties properties) {
        this.artists = artists;
        this.albums = albums;
        this.mediaRoot = properties.mediaRoot().toAbsolutePath().normalize();
    }

    public ImportDraft analyze(List<MultipartFile> files) throws IOException, InterruptedException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one audio file");
        }
        UUID id = UUID.randomUUID();
        Path staging = mediaRoot.resolve(".imports").resolve(id.toString()).normalize();
        Files.createDirectories(staging);

        List<ImportTrack> tracks = new ArrayList<>();
        String artworkFile = null;
        Map<String, Integer> artistsFound = new HashMap<>();
        Map<String, Integer> albumsFound = new HashMap<>();
        Map<Integer, Integer> yearsFound = new HashMap<>();

        for (MultipartFile upload : files) {
            if (upload.isEmpty()) continue;
            String original = safeOriginalName(upload.getOriginalFilename());
            String extension = extension(original);
            if (!AUDIO.contains(extension) && !ARTWORK.contains(extension)) continue;
            String staged = UUID.randomUUID() + "." + extension;
            Path target = staging.resolve(staged);
            try (var input = upload.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            if (ARTWORK.contains(extension)) {
                if (artworkFile == null
                        || original.toLowerCase(Locale.ROOT).matches(".*(cover|folder|front).*")) {
                    artworkFile = staged;
                }
                continue;
            }

            Probe probe = probe(target);
            Guess guess = guess(original);
            String detectedArtist = probe.artist().isBlank() ? guess.artist() : probe.artist();
            count(artistsFound, detectedArtist);
            count(albumsFound, probe.album());
            if (probe.year() > 0) yearsFound.merge(probe.year(), 1, Integer::sum);
            List<String> issues = new ArrayList<>();
            String title = probe.title();
            if (title.isBlank()) {
                title = guess.title();
                issues.add("Название взято из имени файла");
            }
            if (probe.number() <= 0) issues.add("Не найден номер трека");
            tracks.add(
                    new ImportTrack(
                            staged,
                            original,
                            title,
                            Math.max(1, probe.disc()),
                            probe.number(),
                            probe.durationMs(),
                            extension,
                            LOSSLESS_TO_ALAC.contains(extension),
                            issues));
        }

        if (tracks.isEmpty()) {
            deleteTree(staging);
            throw new IllegalArgumentException("No supported audio files found");
        }
        tracks.sort(
                Comparator.comparingInt((ImportTrack value) -> value.number() <= 0 ? 9999 : value.number())
                        .thenComparing(ImportTrack::originalFilename));
        int next = 1;
        List<ImportTrack> numbered = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (ImportTrack track : tracks) {
            int number = track.number();
            if (number <= 0 || !used.add(number)) {
                while (used.contains(next)) next++;
                number = next;
                used.add(number);
            }
            numbered.add(
                    new ImportTrack(
                            track.stagedFile(),
                            track.originalFilename(),
                            track.title(),
                            track.discNumber(),
                            number,
                            track.durationMs(),
                            track.sourceFormat(),
                            track.willTranscode(),
                            track.issues()));
        }

        List<String> issues = new ArrayList<>();
        if (artistsFound.size() > 1) issues.add("В файлах указаны разные исполнители");
        if (albumsFound.size() > 1) issues.add("В файлах указаны разные названия альбома");
        if (artworkFile == null) issues.add("Обложка не найдена");
        if (numbered.stream().anyMatch(value -> !value.issues().isEmpty())) {
            issues.add("Некоторые метаданные восстановлены автоматически");
        }
        return new ImportDraft(
                id,
                mostCommon(artistsFound, "Unknown Artist"),
                mostCommon(albumsFound, "Untitled Album"),
                mostCommonYear(yearsFound),
                artworkFile,
                numbered,
                issues);
    }

    @Transactional
    public ImportResult commit(UUID id, CommitImport request)
            throws IOException, InterruptedException {
        if (request.tracks() == null || request.tracks().isEmpty()) {
            throw new IllegalArgumentException("Album has no tracks");
        }
        Path staging = importDirectory(id);
        Artist artist =
                artists
                        .findFirstByNameIgnoreCase(required(request.artist(), "Artist"))
                        .orElseGet(() -> artists.save(new Artist(request.artist().strip())));
        if (albums
                .findFirstByArtistAndTitleIgnoreCase(artist, required(request.album(), "Album"))
                .isPresent()) {
            throw new IllegalArgumentException("This album already exists");
        }

        String artworkPath = null;
        if (request.artworkFile() != null && !request.artworkFile().isBlank()) {
            Path source = stagedFile(staging, request.artworkFile());
            artworkPath =
                    "artwork/%s-%s.%s"
                            .formatted(
                                    slug(request.album()),
                                    UUID.randomUUID(),
                                    extension(source.getFileName().toString()));
            copy(source, mediaRoot.resolve(artworkPath));
        }
        Album album = new Album(request.album().strip(), request.year(), artworkPath, artist);
        Set<String> positions = new HashSet<>();
        int transcoded = 0;
        for (CommitTrack draft : request.tracks()) {
            if (!positions.add(draft.discNumber() + ":" + draft.number())) {
                throw new IllegalArgumentException("Track positions must be unique");
            }
            Path source = stagedFile(staging, draft.stagedFile());
            boolean transcode = LOSSLESS_TO_ALAC.contains(draft.sourceFormat().toLowerCase(Locale.ROOT));
            String outputPath =
                    "tracks/%s-%02d-%02d-%s.%s"
                            .formatted(
                                    slug(request.album()),
                                    draft.discNumber(),
                                    draft.number(),
                                    UUID.randomUUID(),
                                    transcode ? "m4a" : draft.sourceFormat());
            Path target = mediaRoot.resolve(outputPath).normalize();
            Files.createDirectories(target.getParent());
            if (transcode) {
                run(
                        List.of(
                                "ffmpeg",
                                "-v",
                                "error",
                                "-y",
                                "-i",
                                source.toString(),
                                "-map",
                                "0:a:0",
                                "-c:a",
                                "alac",
                                target.toString()),
                        "Audio conversion failed");
                transcoded++;
            } else {
                copy(source, target);
            }
            Track track =
                    new Track(
                            required(draft.title(), "Track title"),
                            draft.durationMs(),
                            draft.discNumber(),
                            draft.number(),
                            outputPath,
                            transcode ? "audio/mp4" : contentType(draft.sourceFormat()));
            album.addTrack(track);
        }
        Album saved = albums.saveAndFlush(album);
        deleteTree(staging);
        return new ImportResult(saved.getId(), saved.getTitle(), request.tracks().size(), transcoded);
    }

    private Probe probe(Path file) throws IOException, InterruptedException {
        String output =
                run(
                        List.of(
                                "ffprobe",
                                "-v",
                                "error",
                                "-show_entries",
                                "format=duration:format_tags=artist,album,album_artist,title,date,year,track,disc",
                                "-of",
                                "json",
                                file.toString()),
                        "Could not read audio metadata");
        JsonNode format = json.readTree(output).path("format");
        JsonNode tags = format.path("tags");
        String artist = first(tags, "album_artist", "ALBUM_ARTIST", "artist", "ARTIST");
        String album = first(tags, "album", "ALBUM");
        String title = first(tags, "title", "TITLE");
        int year = leadingInt(first(tags, "date", "DATE", "year", "YEAR"));
        int number = leadingInt(first(tags, "track", "TRACK"));
        int disc = leadingInt(first(tags, "disc", "DISC"));
        long duration = Math.max(1, Math.round(format.path("duration").asDouble() * 1000));
        return new Probe(artist, album, title, year, number, disc, duration);
    }

    private String run(List<String> command, String errorMessage)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0)
            throw new IllegalArgumentException(errorMessage + ": " + output.strip());
        return output;
    }

    private Path importDirectory(UUID id) {
        Path path = mediaRoot.resolve(".imports").resolve(id.toString()).normalize();
        if (!path.startsWith(mediaRoot) || !Files.isDirectory(path)) {
            throw new IllegalArgumentException("Import draft not found");
        }
        return path;
    }

    private static Path stagedFile(Path directory, String name) {
        Path path = directory.resolve(name).normalize();
        if (!path.startsWith(directory) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Staged file not found");
        }
        return path;
    }

    private static void copy(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static void count(Map<String, Integer> values, String value) {
        if (!value.isBlank()) values.merge(value, 1, Integer::sum);
    }

    private static String mostCommon(Map<String, Integer> values, String fallback) {
        return values.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(fallback);
    }

    private static int mostCommonYear(Map<Integer, Integer> values) {
        return values.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(Year.now().getValue());
    }

    private static String first(JsonNode tags, String... names) {
        for (String name : names) {
            String value = tags.path(name).asText("").strip();
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static int leadingInt(String value) {
        if (value == null) return 0;
        var match = java.util.regex.Pattern.compile("^(\\d+)").matcher(value.strip());
        return match.find() ? Integer.parseInt(match.group(1)) : 0;
    }

    private static Guess guess(String filename) {
        int dot = filename.lastIndexOf('.');
        String value = dot > 0 ? filename.substring(0, dot) : filename;
        value =
                value
                        .replace('_', ' ')
                        .replaceAll("\\s+", " ")
                        .replaceFirst("^\\s*\\d+[\\s.-]*", "")
                        .replaceAll("(?i)\\s*\\([^)]*(?:info|com|net|org)[^)]*\\)\\s*$", "")
                        .strip();
        String[] parts = value.split("\\s+-\\s+", 2);
        return parts.length == 2 ? new Guess(parts[0].strip(), parts[1].strip()) : new Guess("", value);
    }

    private static String safeOriginalName(String filename) {
        if (filename == null || filename.isBlank()) return "audio";
        return Path.of(filename).getFileName().toString();
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0
                ? ""
                : filename.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(label + " is required");
        return value.strip();
    }

    private static String slug(String value) {
        String result =
                java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("(^-|-$)", "");
        return result.isBlank() ? "album" : result;
    }

    private static String contentType(String extension) {
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "mp3" -> "audio/mpeg";
            case "m4a", "mp4" -> "audio/mp4";
            case "aac" -> "audio/aac";
            case "ogg", "opus" -> "audio/ogg";
            default -> "application/octet-stream";
        };
    }

    private record Probe(
            String artist, String album, String title, int year, int number, int disc, long durationMs) {
    }

    private record Guess(String artist, String title) {
    }
}
