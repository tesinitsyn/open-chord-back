package com.openchord.server.playback;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Persistence access for playback history.
 *
 * <p>Recent album IDs are ordered by the newest event in each album group, not by insertion order
 * or an individual track's latest event.
 */
public interface PlaybackEventRepository extends JpaRepository<PlaybackEvent, UUID> {
    @Query(
            """
                    select event.track.album.id
                    from PlaybackEvent event
                    group by event.track.album.id
                    order by max(event.playedAt) desc
                    """)
    List<UUID> findRecentAlbumIds(Pageable pageable);
}
