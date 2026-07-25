INSERT INTO artists (id, name)
VALUES ('10000000-0000-0000-0000-000000000001', 'Aurora Lines');

INSERT INTO albums (id, title, release_year, artwork_path, artist_id)
VALUES (
    '20000000-0000-0000-0000-000000000001',
    'Afterglow',
    2026,
    NULL,
    '10000000-0000-0000-0000-000000000001'
);

INSERT INTO tracks (
    id,
    title,
    duration_ms,
    disc_number,
    number,
    audio_path,
    content_type,
    album_id
)
VALUES (
    '30000000-0000-0000-0000-000000000001',
    'Night Drive',
    96000,
    1,
    1,
    'demo/OpenChordDemo.m4a',
    'audio/mp4',
    '20000000-0000-0000-0000-000000000001'
);

INSERT INTO lyric_lines (id, text, start_ms, end_ms, track_id)
VALUES
    ('40000000-0000-0000-0000-000000000001', 'Streetlights drawing silver lines', 0, 8000, '30000000-0000-0000-0000-000000000001'),
    ('40000000-0000-0000-0000-000000000002', 'The city breathes behind the glass', 8000, 16000, '30000000-0000-0000-0000-000000000001'),
    ('40000000-0000-0000-0000-000000000003', 'We let the quiet fill the space', 16000, 24000, '30000000-0000-0000-0000-000000000001'),
    ('40000000-0000-0000-0000-000000000004', 'And watch the empty stations pass', 24000, 33000, '30000000-0000-0000-0000-000000000001'),
    ('40000000-0000-0000-0000-000000000005', 'Stay with me into the afterglow', 33000, 43000, '30000000-0000-0000-0000-000000000001'),
    ('40000000-0000-0000-0000-000000000006', 'Where every signal turns to gold', 43000, 52000, '30000000-0000-0000-0000-000000000001'),
    ('40000000-0000-0000-0000-000000000007', 'No map, no reason to go home', 52000, 61000, '30000000-0000-0000-0000-000000000001'),
    ('40000000-0000-0000-0000-000000000008', 'Just one more story left untold', 61000, 71000, '30000000-0000-0000-0000-000000000001'),
    ('40000000-0000-0000-0000-000000000009', 'The morning waits beyond the road', 71000, 82000, '30000000-0000-0000-0000-000000000001'),
    ('40000000-0000-0000-0000-000000000010', 'But for a while we are not alone', 82000, 94000, '30000000-0000-0000-0000-000000000001');
