package com.openchord.server.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlbumRepository extends JpaRepository<Album, UUID> {
  @EntityGraph(attributePaths = {"artist", "tracks", "tracks.lyrics"})
  @Query("select distinct a from Album a where a.id = :id")
  Optional<Album> findDetailedById(@Param("id") UUID id);

  @EntityGraph(attributePaths = {"artist", "tracks", "tracks.lyrics"})
  @Query(
      """
            select distinct a from Album a
            join a.artist artist
            where :search is null
               or lower(a.title) like lower(concat('%', :search, '%'))
               or lower(artist.name) like lower(concat('%', :search, '%'))
            order by a.releaseYear desc, a.title
            """)
  List<Album> searchDetailed(@Param("search") String search);
}
