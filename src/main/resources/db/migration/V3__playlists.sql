CREATE TABLE playlists
(
    id         UUID PRIMARY KEY,
    name       VARCHAR(120)             NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_playlists_updated_at ON playlists (updated_at DESC);

CREATE TABLE playlist_entries
(
    id          UUID PRIMARY KEY,
    playlist_id UUID    NOT NULL REFERENCES playlists (id) ON DELETE CASCADE,
    track_id    UUID    NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL CHECK (position >= 0),
    UNIQUE (playlist_id, track_id)
);
CREATE INDEX ix_playlist_entries_order ON playlist_entries (playlist_id, position);
CREATE INDEX ix_playlist_entries_track_id ON playlist_entries (track_id);
