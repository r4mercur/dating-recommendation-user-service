package com.bjarne.datingrecommendationsuserservice.music.dto;

import com.bjarne.datingrecommendationsuserservice.music.UserSong;

import java.util.List;

/**
 * A song on a profile. {@code anthem} marks the profile song (at most one per user).
 */
public record SongResponse(String spotifyTrackId,
                           String name,
                           List<String> artists,
                           String album,
                           String imageUrl,
                           String spotifyUrl,
                           boolean anthem) {

    public static SongResponse from(UserSong song) {
        TrackResult track = song.toTrack();
        return new SongResponse(track.spotifyTrackId(), track.name(), track.artists(), track.album(), track.imageUrl(),
                track.spotifyUrl(), song.isAnthem());
    }
}
