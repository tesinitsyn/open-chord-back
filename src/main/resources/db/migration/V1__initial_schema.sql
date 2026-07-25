CREATE TABLE artists (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);
CREATE INDEX ix_artists_name ON artists (name);

CREATE TABLE albums (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    release_year INTEGER NOT NULL CHECK (release_year BETWEEN 1000 AND 9999),
    artwork_path VARCHAR(1024),
    artist_id UUID NOT NULL REFERENCES artists(id) ON DELETE CASCADE
);
CREATE INDEX ix_albums_title ON albums (title);

CREATE TABLE tracks (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    duration_ms BIGINT NOT NULL CHECK (duration_ms > 0),
    disc_number INTEGER NOT NULL CHECK (disc_number > 0),
    number INTEGER NOT NULL CHECK (number > 0),
    audio_path VARCHAR(1024) NOT NULL UNIQUE,
    content_type VARCHAR(127) NOT NULL,
    album_id UUID NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
    UNIQUE (album_id, disc_number, number)
);
CREATE INDEX ix_tracks_title ON tracks (title);

CREATE TABLE lyric_lines (
    id UUID PRIMARY KEY,
    text TEXT NOT NULL,
    start_ms BIGINT NOT NULL CHECK (start_ms >= 0),
    end_ms BIGINT NOT NULL CHECK (end_ms > start_ms),
    track_id UUID NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
    UNIQUE (track_id, start_ms)
);

CREATE TABLE playback_events (
    id UUID PRIMARY KEY,
    track_id UUID NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
    played_at TIMESTAMP WITH TIME ZONE NOT NULL,
    position_ms BIGINT NOT NULL CHECK (position_ms >= 0),
    completed BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_playback_events_track_id ON playback_events (track_id);
CREATE INDEX ix_playback_events_played_at ON playback_events (played_at);
