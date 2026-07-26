package com.openchord.server.playback;

import com.openchord.server.catalog.Track;
import com.openchord.server.catalog.TrackRepository;
import com.openchord.server.graphql.CatalogTypes.PlaybackEventInput;
import graphql.GraphqlErrorException;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/** Validates and persists client playback progress events. */
public class PlaybackService {
    /** Track source used to validate event targets and clamp progress. */
    private final TrackRepository tracks;
    /** Playback history persistence port. */
    private final PlaybackEventRepository events;

    /** Creates the playback service from its persistence ports. */
    public PlaybackService(TrackRepository tracks, PlaybackEventRepository events) {
        this.tracks = tracks;
        this.events = events;
    }

    @Transactional
    /** Records a playback event, rejecting invalid positions and unknown tracks. */
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
