package com.openchord.server.catalog;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Track persistence with the aggregate fetch plan required by lyric and GraphQL projections.
 */
public interface TrackRepository extends JpaRepository<Track, UUID> {
    @EntityGraph(attributePaths = {"album", "album.artist", "lyrics"})
    @Query("select distinct track from Track track where track.id = :id")
    Optional<Track> findDetailedById(@Param("id") UUID id);
}
