package com.openchord.server;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openchord.server.catalog.Album;
import com.openchord.server.catalog.AlbumRepository;
import com.openchord.server.catalog.Artist;
import com.openchord.server.catalog.ArtistRepository;
import com.openchord.server.catalog.LyricLine;
import com.openchord.server.catalog.Track;
import com.openchord.server.playlist.PlaylistRepository;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OpenChordServerApplicationTests {
    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("openchord")
                    .withUsername("openchord")
                    .withPassword("openchord");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ArtistRepository artists;
    @Autowired
    private AlbumRepository albums;
    @Autowired
    private PlaylistRepository playlists;

    private Album album;
    private Track track;

    @BeforeEach
    void seedCatalog() throws Exception {
        playlists.deleteAll();
        albums.deleteAll();
        artists.deleteAll();

        Artist artist = artists.save(new Artist("Aurora Lines"));
        album = new Album("Afterglow", 2026, null, artist);
        track = new Track("Night Drive", 96_000, 1, 1, "tracks/night-drive.m4a", "audio/mp4");
        track.addLyricLine(new LyricLine("Streetlights drawing silver lines", 0, 8_000));
        album.addTrack(track);
        album = albums.saveAndFlush(album);

        Path audio =
                Path.of(
                        System.getProperty("java.io.tmpdir"),
                        "openchord-test-media",
                        "tracks",
                        "night-drive.m4a");
        Files.createDirectories(audio.getParent());
        Files.write(audio, "0123456789".getBytes());
    }

    @Test
    void playlistLifecyclePreservesTrackOrder() throws Exception {
        MvcResult created =
                mvc.perform(
                                post("/graphql")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                        {"query":"mutation { createPlaylist(name: \\"Night drive\\") { id name tracks { id } } }"}
                                                        """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.createPlaylist.name", is("Night drive")))
                        .andExpect(jsonPath("$.data.createPlaylist.tracks.length()", is(0)))
                        .andReturn();

        String body = created.getResponse().getContentAsString();
        String playlistId =
                com.jayway.jsonpath.JsonPath.read(body, "$.data.createPlaylist.id");

        mvc.perform(
                        post("/graphql")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                                {"query":"mutation { addTrackToPlaylist(playlistId: \\"%s\\", trackId: \\"%s\\") { tracks { id title } } }"}
                                                """
                                                .formatted(playlistId, track.getId())))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.data.addTrackToPlaylist.tracks[0].id",
                                is(track.getId().toString())));

        // Adding the same track is idempotent so a retried client mutation
        // cannot create duplicate playlist rows.
        mvc.perform(
                        post("/graphql")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                                {"query":"mutation { addTrackToPlaylist(playlistId: \\"%s\\", trackId: \\"%s\\") { tracks { id } } }"}
                                                """
                                                .formatted(playlistId, track.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addTrackToPlaylist.tracks.length()", is(1)));

        mvc.perform(
                        post("/graphql")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                                {"query":"query { playlists { id name tracks { id } } }"}
                                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.playlists[0].id", is(playlistId)))
                .andExpect(jsonPath("$.data.playlists[0].tracks[0].id", is(track.getId().toString())));

        mvc.perform(
                        post("/graphql")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                                {"query":"mutation { deletePlaylist(id: \\"%s\\") }"}
                                                """
                                                .formatted(playlistId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletePlaylist", is(true)));
    }

    @Test
    void catalogPlaybackAndRecentFlow() throws Exception {
        mvc.perform(
                        post("/graphql")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"query\":\"{ albums { id } }\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.albums[0].id", is(album.getId().toString())));

        mvc.perform(
                        post("/graphql")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                                {"query":"query { albums(search: \\"Aurora\\") { id title year artist { name } tracks { id durationMs streamUrl lyrics { text endMs } } } }"}
                                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.albums[0].id", is(album.getId().toString())))
                .andExpect(jsonPath("$.data.albums[0].tracks.length()", is(1)))
                .andExpect(jsonPath("$.data.albums[0].artist.name", is("Aurora Lines")))
                .andExpect(jsonPath("$.data.albums[0].tracks[0].lyrics[0].endMs", is(8000)));

        mvc.perform(
                        post("/graphql")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                                {"query":"mutation { recordPlayback(input: { trackId: \\"%s\\", positionMs: 120000, completed: true }) { trackId positionMs completed } }"}
                                                """
                                                .formatted(track.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordPlayback.trackId", is(track.getId().toString())))
                .andExpect(jsonPath("$.data.recordPlayback.positionMs", is(96000)))
                .andExpect(jsonPath("$.data.recordPlayback.completed", is(true)));

        mvc.perform(
                        post("/graphql")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"query\":\"{ recentlyPlayed { id } }\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentlyPlayed[0].id", is(album.getId().toString())));
    }

    @Test
    void audioSupportsByteRanges() throws Exception {
        MvcResult pending =
                mvc.perform(get("/media/tracks/{id}", track.getId()).header("Range", "bytes=2-5"))
                        .andExpect(request().asyncStarted())
                        .andReturn();

        mvc.perform(asyncDispatch(pending))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Range", "bytes 2-5/10"))
                .andExpect(content().bytes("2345".getBytes()));
    }

    @Test
    void exportsPortableOpenChordArchive() throws Exception {
        byte[] archive = exportArchive();

        Map<String, byte[]> entries = unzip(archive);
        assertNotNull(entries.get("manifest.json"));
        assertNotNull(entries.get("catalog/artists.jsonl"));
        assertNotNull(entries.get("catalog/albums.jsonl"));
        assertNotNull(entries.get("catalog/tracks.jsonl"));
        assertNotNull(entries.get("catalog/assets.jsonl"));
        assertTrue(new String(entries.get("manifest.json")).contains("\"format\":\"openchord\""));
        assertTrue(new String(entries.get("catalog/tracks.jsonl")).contains("\"purpose\":\"playable\""));
        assertEquals(
                1,
                entries.keySet().stream()
                        .filter(path -> path.startsWith("assets/sha256/"))
                        .count());
    }

    @Test
    void exportedArchiveRestoresCatalogAndMedia() throws Exception {
        byte[] archive = exportArchive();
        playlists.deleteAll();
        albums.deleteAll();
        artists.deleteAll();

        MockMultipartFile upload =
                new MockMultipartFile(
                        "archive",
                        "library.openchord",
                        "application/vnd.openchord.archive+zip",
                        archive);
        mvc.perform(multipart("/api/admin/openchord/import").file(upload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.albums", is(1)))
                .andExpect(jsonPath("$.tracks", is(1)))
                .andExpect(jsonPath("$.playlists", is(0)))
                .andExpect(jsonPath("$.skippedAlbums", is(0)));

        Album restored = albums.findAllDetailed().getFirst();
        assertEquals("Afterglow", restored.getTitle());
        assertEquals("Night Drive", restored.getTracks().getFirst().getTitle());
        assertTrue(
                Files.exists(
                        Path.of(System.getProperty("java.io.tmpdir"), "openchord-test-media")
                                .resolve(restored.getTracks().getFirst().getAudioPath())));
    }

    @Test
    void healthReportsReady() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    private static Map<String, byte[]> unzip(byte[] archive) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                entries.put(entry.getName(), input.readAllBytes());
            }
        }
        return entries;
    }

    private byte[] exportArchive() throws Exception {
        MvcResult pending =
                mvc.perform(get("/api/admin/openchord/export"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        return mvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .contentType(
                                        "application/vnd.openchord.archive+zip"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }
}
