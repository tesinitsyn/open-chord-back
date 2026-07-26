package com.openchord.server.graphql;

import com.openchord.server.catalog.CatalogService;
import com.openchord.server.config.OpenChordProperties;
import com.openchord.server.graphql.CatalogTypes.AlbumView;
import com.openchord.server.graphql.CatalogTypes.PlaybackEventInput;
import com.openchord.server.graphql.CatalogTypes.PlaybackEventView;
import com.openchord.server.playback.PlaybackService;

import java.util.List;
import java.util.UUID;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * Resolves the public catalog queries and playback mutation defined in {@code schema.graphqls}.
 *
 * <p>Domain entities never cross the GraphQL boundary; they are mapped to immutable transport
 * records and media paths are expanded using the configured public base URL.
 */
@Controller
public class CatalogGraphQlController {
    private final CatalogService catalog;
    private final PlaybackService playback;
    private final OpenChordProperties properties;

    public CatalogGraphQlController(
            CatalogService catalog, PlaybackService playback, OpenChordProperties properties) {
        this.catalog = catalog;
        this.playback = playback;
        this.properties = properties;
    }

    @QueryMapping
    public List<AlbumView> albums(
            @Argument String search, @Argument Integer limit, @Argument Integer offset) {
        return catalog.albums(search, limit == null ? 50 : limit, offset == null ? 0 : offset).stream()
                .map(album -> AlbumView.from(album, properties))
                .toList();
    }

    @QueryMapping
    public AlbumView album(@Argument UUID id) {
        return catalog.album(id).map(value -> AlbumView.from(value, properties)).orElse(null);
    }

    @QueryMapping
    public List<AlbumView> recentlyPlayed(@Argument Integer limit) {
        return catalog.recentlyPlayed(limit == null ? 10 : limit).stream()
                .map(album -> AlbumView.from(album, properties))
                .toList();
    }

    @MutationMapping
    public PlaybackEventView recordPlayback(@Argument PlaybackEventInput input) {
        return PlaybackEventView.from(playback.record(input));
    }
}
