package com.bjarne.datingrecommendationsuserservice.steam;

import com.bjarne.datingrecommendationsuserservice.exception.BadRequestException;
import com.bjarne.datingrecommendationsuserservice.exception.NotFoundException;
import com.bjarne.datingrecommendationsuserservice.exception.UnprocessableException;
import com.bjarne.datingrecommendationsuserservice.games.IgdbClient;
import com.bjarne.datingrecommendationsuserservice.games.dto.GameResult;
import com.bjarne.datingrecommendationsuserservice.games.dto.SteamSuggestion;
import com.bjarne.datingrecommendationsuserservice.steam.SteamClient.SteamGame;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Suggests games from a Steam library. Nothing is saved: the user picks from the suggestions and saves with
 * {@code PUT /api/me/games}. The SteamID is only used during the request and never stored or logged.
 */
@Service
public class SteamImportService {

    public static final int MAX_SUGGESTIONS = 20;

    private static final Pattern STEAM_ID_64 = Pattern.compile("7656119\\d{10}");
    private static final Pattern VANITY_NAME = Pattern.compile("[A-Za-z0-9_-]{2,32}");
    private static final String HEADER_IMAGE = "https://cdn.cloudflare.steamstatic.com/steam/apps/%d/header.jpg";

    private final SteamClient steamClient;
    private final IgdbClient igdbClient;

    public SteamImportService(SteamClient steamClient, IgdbClient igdbClient) {
        this.steamClient = steamClient;
        this.igdbClient = igdbClient;
    }

    public List<SteamSuggestion> suggest(String profile) {
        String steamId = resolveSteamId(profile);

        Optional<List<SteamGame>> owned = steamClient.ownedGames(steamId);
        if (owned.isEmpty()) {
            throw new UnprocessableException("Your Steam game details are private. Set 'Game details' to public in your Steam privacy settings and try again.");
        }
        List<SteamGame> library = owned.get();
        if (library.isEmpty()) {
            library = steamClient.recentlyPlayedGames(steamId).orElse(List.of());
        }
        if (library.isEmpty()) {
            throw new UnprocessableException("No games are visible on your Steam profile");
        }

        List<SteamGame> top = library.stream()
                .sorted(Comparator.comparingInt(SteamGame::playtimeMinutes).reversed().thenComparing(SteamGame::name))
                .limit(MAX_SUGGESTIONS)
                .toList();
        Map<Long, GameResult> igdbGames = igdbClient.findBySteamAppIds(top.stream().map(SteamGame::appId).toList());

        Set<Long> seenGames = new HashSet<>();
        return top.stream()
                .map(steamGame -> toSuggestion(steamGame, igdbGames.get(steamGame.appId())))
                // several Steam apps (demo, soundtrack, regional version) can belong to one game: keep the most played
                .filter(suggestion -> suggestion.igdbId() == null || seenGames.add(suggestion.igdbId()))
                .toList();
    }

    private static SteamSuggestion toSuggestion(SteamGame steamGame, GameResult igdbGame) {
        double hours = Math.round(steamGame.playtimeMinutes() / 6.0) / 10.0;
        String headerImage = HEADER_IMAGE.formatted(steamGame.appId());
        if (igdbGame == null) {
            return new SteamSuggestion(null, steamGame.name(), headerImage, steamGame.appId(), hours);
        }
        return new SteamSuggestion(igdbGame.igdbId(), igdbGame.name(),
                igdbGame.coverUrl() != null ? igdbGame.coverUrl() : headerImage, steamGame.appId(), hours);
    }

    /** Accepts a profile URL (/profiles/{id} or /id/{vanity}), a SteamID64 or a vanity name. */
    private String resolveSteamId(String profile) {
        String input = profile == null ? "" : profile.trim();
        String vanity;

        if (STEAM_ID_64.matcher(input).matches()) {
            return input;
        } else if (input.toLowerCase(Locale.ROOT).startsWith("http") || input.toLowerCase(Locale.ROOT).startsWith("steamcommunity.com")) {
            String[] segments = pathSegments(input);
            if (segments.length < 2 || !(segments[0].equals("id") || segments[0].equals("profiles"))) {
                throw new BadRequestException("Not a Steam profile URL");
            }
            if (segments[0].equals("profiles")) {
                if (!STEAM_ID_64.matcher(segments[1]).matches()) {
                    throw new BadRequestException("Not a valid SteamID64 in the profile URL");
                }
                return segments[1];
            }
            vanity = segments[1];
        } else {
            vanity = input;
        }

        if (!VANITY_NAME.matcher(vanity).matches()) {
            throw new BadRequestException("Enter a Steam profile URL, a vanity name or a SteamID64");
        }
        return steamClient.resolveVanity(vanity).orElseThrow(() -> new NotFoundException("Steam profile not found"));
    }

    private static String[] pathSegments(String input) {
        try {
            URI uri = URI.create(input.toLowerCase(Locale.ROOT).startsWith("http") ? input : "https://" + input);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!host.equals("steamcommunity.com") && !host.endsWith(".steamcommunity.com")) {
                throw new BadRequestException("Not a Steam profile URL");
            }
            String path = uri.getPath() == null ? "" : uri.getPath();
            return java.util.Arrays.stream(path.split("/")).filter(segment -> !segment.isEmpty()).toArray(String[]::new);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Not a Steam profile URL");
        }
    }
}
