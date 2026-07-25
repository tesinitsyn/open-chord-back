package com.openchord.server.catalog;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistRepository extends JpaRepository<Artist, UUID> {
  java.util.Optional<Artist> findFirstByNameIgnoreCase(String name);
}
