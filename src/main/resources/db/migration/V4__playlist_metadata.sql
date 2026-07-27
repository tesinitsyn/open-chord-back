ALTER TABLE playlists
    ADD COLUMN description VARCHAR(500) NOT NULL DEFAULT '',
    ADD COLUMN artwork_path VARCHAR(1024),
    ADD COLUMN artwork_content_type VARCHAR(100);
