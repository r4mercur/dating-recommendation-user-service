package com.bjarne.datingrecommendationsuserservice.games;

import com.bjarne.datingrecommendationsuserservice.games.dto.GameResult;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A favourite game on a profile: a copy of the IGDB data taken when the user picked it.
 */
@Entity
@Table(name = "user_games")
@Getter
@NoArgsConstructor
public class UserGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private long igdbId;

    @Column(nullable = false)
    private String name;

    @Column(length = 512)
    private String coverUrl;

    private Integer year;

    @Column(nullable = false)
    private List<String> platforms;

    private Long steamAppId;

    public UserGame(Long userId, int position, GameResult game) {
        this.userId = userId;
        this.position = position;
        this.igdbId = game.igdbId();
        this.name = game.name();
        this.coverUrl = game.coverUrl();
        this.year = game.year();
        this.platforms = List.copyOf(game.platforms());
        this.steamAppId = game.steamAppId();
    }

    public GameResult toGame() {
        return new GameResult(igdbId, name, coverUrl, year, platforms, steamAppId);
    }
}
