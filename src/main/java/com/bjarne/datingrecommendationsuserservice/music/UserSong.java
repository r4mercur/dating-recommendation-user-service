package com.bjarne.datingrecommendationsuserservice.music;

import com.bjarne.datingrecommendationsuserservice.music.dto.TrackResult;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A song on a profile: a copy of the Spotify track data taken when the user picked it.
 */
@Entity
@Table(name = "user_songs")
@Getter
@NoArgsConstructor
public class UserSong {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String spotifyTrackId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private List<String> artists;

    private String album;

    @Column(length = 512)
    private String imageUrl;

    @Column(nullable = false)
    private boolean anthem;

    public UserSong(Long userId, int position, TrackResult track, boolean anthem) {
        this.userId = userId;
        this.position = position;
        this.spotifyTrackId = track.spotifyTrackId();
        this.name = track.name();
        this.artists = List.copyOf(track.artists());
        this.album = track.album();
        this.imageUrl = track.imageUrl();
        this.anthem = anthem;
    }

    public TrackResult toTrack() {
        return TrackResult.of(spotifyTrackId, name, artists, album, imageUrl);
    }
}
