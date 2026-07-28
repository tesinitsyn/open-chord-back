package com.openchord.server.playlist;

import com.openchord.server.config.OpenChordProperties;
import com.openchord.server.graphql.CatalogTypes.PlaylistView;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** HTTP endpoint for playlist creation with optional binary artwork. */
@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {
    private final PlaylistService playlists;
    private final OpenChordProperties properties;

    public PlaylistController(PlaylistService playlists, OpenChordProperties properties) {
        this.playlists = playlists;
        this.properties = properties;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PlaylistView create(
            @RequestParam String name,
            @RequestParam(defaultValue = "") String description,
            @RequestParam(required = false) MultipartFile artwork) {
        return PlaylistView.from(playlists.create(name, description, artwork), properties);
    }
}
