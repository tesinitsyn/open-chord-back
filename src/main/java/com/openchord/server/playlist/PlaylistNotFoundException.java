package com.openchord.server.playlist;

/** Raised when a playlist mutation targets an unknown aggregate or entry. */
public class PlaylistNotFoundException extends RuntimeException {
    public PlaylistNotFoundException(String message) {
        super(message);
    }
}
