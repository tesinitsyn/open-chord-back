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

@Controller
/** GraphQL adapter that maps domain aggregates into stable client-facing view records. */
public class CatalogGraphQlController {
    /** Catalog query use cases. */
    private final CatalogService catalog;
    /** Playback-history mutation use cases. */
    private final PlaybackService playback;
    /** Public URL configuration used when building media links. */
    private final OpenChordProperties properties;

    /** Creates the GraphQL adapter with its application services and URL configuration. */
    public CatalogGraphQlController(
            CatalogService catalog, PlaybackService playback, OpenChordProperties properties) {
        this.catalog = catalog;
        this.playback = playback;
        this.properties = properties;
    }

    @QueryMapping
    /** Resolves the paginated album catalog with an optional text search. */
    public List<AlbumView> albums(
            @Argument String search, @Argument Integer limit, @Argument Integer offset) {
        return catalog.albums(search, limit == null ? 50 : limit, offset == null ? 0 : offset).stream()
                .map(album -> AlbumView.from(album, properties))
                .toList();
    }

    @QueryMapping
    /** Resolves a single album, returning GraphQL {@code null} when it does not exist. */
    public AlbumView album(@Argument UUID id) {
        return catalog.album(id).map(value -> AlbumView.from(value, properties)).orElse(null);
    }

    @QueryMapping
    /** Resolves albums ordered by recent playback activity. */
    public List<AlbumView> recentlyPlayed(@Argument Integer limit) {
        return catalog.recentlyPlayed(limit == null ? 10 : limit).stream()
                .map(album -> AlbumView.from(album, properties))
                .toList();
    }

    @MutationMapping
    /** Validates and records playback progress from a client. */
    public PlaybackEventView recordPlayback(@Argument PlaybackEventInput input) {
        return PlaybackEventView.from(playback.record(input));
    }
}
