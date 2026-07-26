package com.openchord.server.playback;

import com.openchord.server.catalog.Track;
import com.openchord.server.catalog.TrackRepository;
import com.openchord.server.graphql.CatalogTypes.PlaybackEventInput;
import graphql.GraphqlErrorException;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records client playback progress and completion events for catalog history. */
@Service
public class PlaybackService {
    private final TrackRepository tracks;
    private final PlaybackEventRepository events;

    public PlaybackService(TrackRepository tracks, PlaybackEventRepository events) {
        this.tracks = tracks;
        this.events = events;
    }

    /**
     * Persists a playback event after resolving and validating its track.
     *
     * @param input client-reported playback state
     * @return the persisted event
     * @throws graphql.GraphQLException if the position is negative or the track does not exist
     */
    @Transactional
    public PlaybackEvent record(PlaybackEventInput input) {
        if (input.positionMs() < 0) {
            throw GraphqlErrorException.newErrorException()
                    .message("positionMs must be non-negative")
                    .extensions(java.util.Map.of("code", "BAD_USER_INPUT"))
                    .build();
        }
        Track track =
                tracks
                        .findById(input.trackId())
                        .orElseThrow(
                                () ->
                                        GraphqlErrorException.newErrorException()
                                                .message("Track not found")
                                                .extensions(java.util.Map.of("code", "NOT_FOUND"))
                                                .build());
        return events.save(
                new PlaybackEvent(
                        track,
                        input.playedAt() == null ? Instant.now() : input.playedAt().toInstant(),
                        Math.min(input.positionMs(), track.getDurationMs()),
                        input.completed()));
    }
}
