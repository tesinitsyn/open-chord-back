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
@Table(name = "tracks")
public class Track {
  @Id @GeneratedValue private UUID id;
  private String title;
  private long durationMs;
  private int discNumber;
  private int number;
  private String audioPath;
  private String contentType;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "album_id")
  private Album album;

  @OneToMany(mappedBy = "track", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("startMs")
  private Set<LyricLine> lyrics = new LinkedHashSet<>();

  protected Track() {}

  public Track(
      String title,
      long durationMs,
      int discNumber,
      int number,
      String audioPath,
      String contentType) {
    this.title = title;
    this.durationMs = durationMs;
    this.discNumber = discNumber;
    this.number = number;
    this.audioPath = audioPath;
    this.contentType = contentType;
  }

  void attachTo(Album album) {
    this.album = album;
  }

  public void addLyricLine(LyricLine line) {
    lyrics.add(line);
    line.attachTo(this);
  }

  public UUID getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public long getDurationMs() {
    return durationMs;
  }

  public int getDiscNumber() {
    return discNumber;
  }

  public int getNumber() {
    return number;
  }

  public String getAudioPath() {
    return audioPath;
  }

  public String getContentType() {
    return contentType;
  }

  public Album getAlbum() {
    return album;
  }

  public List<LyricLine> getLyrics() {
    return List.copyOf(lyrics);
  }
}
