package com.openchord.server.playlist;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Playlist persistence with the complete track graph needed by GraphQL projections. */
public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {
    @EntityGraph(
            attributePaths = {
                "entries",
                "entries.track",
                "entries.track.album",
                "entries.track.album.artist",
                "entries.track.lyrics"
            })
    @Query("select distinct playlist from Playlist playlist order by playlist.updatedAt desc")
    List<Playlist> findAllDetailed();

    @EntityGraph(
            attributePaths = {
                "entries",
                "entries.track",
                "entries.track.album",
                "entries.track.album.artist",
                "entries.track.lyrics"
            })
    @Query("select distinct playlist from Playlist playlist where playlist.id = :id")
    Optional<Playlist> findDetailedById(@Param("id") UUID id);
}
