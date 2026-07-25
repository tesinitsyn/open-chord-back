package com.openchord.server.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "albums")
public class Album {
  @Id @GeneratedValue private UUID id;
  private String title;
  private int releaseYear;
  private String artworkPath;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "artist_id")
  private Artist artist;

  @OneToMany(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("discNumber, number")
  private Set<Track> tracks = new LinkedHashSet<>();

  protected Album() {}

  public Album(String title, int releaseYear, String artworkPath, Artist artist) {
    this.title = title;
    this.releaseYear = releaseYear;
    this.artworkPath = artworkPath;
    this.artist = artist;
  }

  public void addTrack(Track track) {
    tracks.add(track);
    track.attachTo(this);
  }

  public UUID getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public int getReleaseYear() {
    return releaseYear;
  }

  public String getArtworkPath() {
    return artworkPath;
  }

  public Artist getArtist() {
    return artist;
  }

  public List<Track> getTracks() {
    return List.copyOf(tracks);
  }
}
