package com.openchord.server.admin;

import com.openchord.server.catalog.Album;
import com.openchord.server.catalog.AlbumRepository;
import com.openchord.server.catalog.Artist;
import com.openchord.server.catalog.ArtistRepository;
import com.openchord.server.catalog.LyricLine;
import com.openchord.server.catalog.Track;
import com.openchord.server.catalog.TrackRepository;
import com.openchord.server.config.OpenChordProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Implements direct catalog administration operations that do not use the album import workflow.
 */
@Service
public class AdminCatalogService {
    private static final Pattern LRC =
            Pattern.compile("^\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]\\s*(.*)$");

    private final ArtistRepository artists;
    private final AlbumRepository albums;
    private final TrackRepository tracks;
    private final Path mediaRoot;

    public AdminCatalogService(
            ArtistRepository artists,
            AlbumRepository albums,
            TrackRepository tracks,
            OpenChordProperties properties) {
        this.artists = artists;
        this.albums = albums;
        this.tracks = tracks;
        this.mediaRoot = properties.mediaRoot().toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public List<AdminController.AlbumView> catalog() {
        return albums.findAllDetailed().stream().map(AdminController.AlbumView::from).toList();
    }

    /**
     * Stores a track and creates missing artist or album records.
     *
     * @param artistName artist display name
     * @param albumTitle album display title
     * @param year album release year
     * @param title track display title
     * @param durationMs track duration in milliseconds
     * @param discNumber one-based disc number
     * @param number one-based position on the disc
     * @param audio required audio upload
     * @param artwork optional album artwork upload
     * @param lyrics optional synchronized LRC document
     * @return the newly persisted track
     * @throws IOException if managed media cannot be written
     * @throws IllegalArgumentException if required metadata or audio is missing
     */
    @Transactional
    public AdminController.TrackView createTrack(
            String artistName,
            String albumTitle,
            int releaseYear,
            String trackTitle,
            int discNumber,
            int trackNumber,
            long durationMs,
            String lyrics,
            MultipartFile audio,
            MultipartFile artwork)
            throws IOException {
        Artist artist =
                artists
                        .findFirstByNameIgnoreCase(required(artistName, "Artist"))
                        .orElseGet(() -> artists.save(new Artist(artistName.strip())));
        Album album =
                albums
                        .findFirstByArtistAndTitleIgnoreCase(artist, required(albumTitle, "Album"))
                        .orElseGet(() -> albums.save(new Album(albumTitle.strip(), releaseYear, null, artist)));

        String audioExtension = extension(audio.getOriginalFilename());
        String audioPath =
                "tracks/%s-%s%s".formatted(slug(artistName), UUID.randomUUID(), audioExtension);
        store(audio, audioPath);

        if (artwork != null && !artwork.isEmpty()) {
            String artworkPath =
                    "artwork/%s-%s%s"
                            .formatted(
                                    slug(albumTitle), UUID.randomUUID(), extension(artwork.getOriginalFilename()));
            store(artwork, artworkPath);
            album.setArtworkPath(artworkPath);
        }

        String contentType =
                audio.getContentType() == null ? "application/octet-stream" : audio.getContentType();
        Track track =
                new Track(
                        required(trackTitle, "Track"),
                        durationMs,
                        discNumber,
                        trackNumber,
                        audioPath,
                        contentType);
        parseLyrics(lyrics, durationMs).forEach(track::addLyricLine);
        album.addTrack(track);
        albums.saveAndFlush(album);
        return AdminController.TrackView.from(track);
    }

    /**
     * Replaces all synchronized lyric lines for a track.
     *
     * @param id identifier of the track to update
     * @param lyrics LRC document, or a blank value to remove lyrics
     * @return the updated track projection
     * @throws IllegalArgumentException if the track does not exist or the LRC document is invalid
     */
    @Transactional
    public AdminController.TrackView replaceLyrics(UUID id, String lyrics) {
        Track track =
                tracks
                        .findDetailedById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Track not found"));
        track.replaceLyrics(parseLyrics(lyrics, track.getDurationMs()));
        return AdminController.TrackView.from(tracks.saveAndFlush(track));
    }

    /**
     * Parses an LRC document and derives each line's end from the following timestamp.
     *
     * @param source LRC text
     * @param durationMs duration used as the final line's end
     * @return timestamp-ordered lyric lines
     * @throws IllegalArgumentException if a nonblank line has no supported timestamp
     */
    static List<LyricLine> parseLyrics(String source, long durationMs) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        record Draft(long start, String text) {
        }
        List<Draft> drafts = new ArrayList<>();
        String[] lines = source.replace("\r", "").split("\n");
        for (int index = 0; index < lines.length; index++) {
            Matcher match = LRC.matcher(lines[index].strip());
            if (match.matches()) {
                long fraction = match.group(3) == null ? 0 : Long.parseLong(match.group(3));
                if (match.group(3) != null && match.group(3).length() == 2) fraction *= 10;
                if (match.group(3) != null && match.group(3).length() == 1) fraction *= 100;
                long start =
                        (Long.parseLong(match.group(1)) * 60 + Long.parseLong(match.group(2))) * 1000
                                + fraction;
                if (!match.group(4).isBlank()) drafts.add(new Draft(start, match.group(4).strip()));
            } else if (!lines[index].isBlank()) {
                drafts.add(new Draft(index * 5_000L, lines[index].strip()));
            }
        }
        drafts.sort(Comparator.comparingLong(Draft::start));
        List<LyricLine> result = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            Draft current = drafts.get(index);
            long end =
                    index + 1 < drafts.size()
                            ? drafts.get(index + 1).start()
                            : Math.max(current.start() + 1_000, durationMs);
            if (current.start() < durationMs && end > current.start()) {
                result.add(new LyricLine(current.text(), current.start(), Math.min(end, durationMs)));
            }
        }
        return result;
    }

    private void store(MultipartFile file, String relativePath) throws IOException {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("Audio file is required");
        Path target = mediaRoot.resolve(relativePath).normalize();
        if (!target.startsWith(mediaRoot)) throw new IllegalArgumentException("Invalid media path");
        Files.createDirectories(target.getParent());
        try (var input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(label + " is required");
        return value.strip();
    }

    private static String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0
                ? ""
                : filename.substring(dot).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "");
    }

    private static String slug(String value) {
        String normalized =
                Normalizer.normalize(value, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "media" : normalized;
    }
}
