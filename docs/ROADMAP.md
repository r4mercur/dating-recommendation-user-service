# Roadmap: Dating-Recommendations User Service

Übergabedokument für die weitere Umsetzung. **Neue Session: Diese Datei zuerst lesen, dann mit der nächsten offenen Phase weitermachen.**

- Backend (dieses Repo): Spring Boot 4.1, Java 24 (Gradle Toolchain), Postgres + Flyway, Kafka, Apache Solr, SeaweedFS (S3-kompatibler Speicher für Profilbilder)
- Frontend: `C:\Users\bjarn\WebstormProjects\dating-recommendations-fe` (Vue 3, Pinia, PrimeVue, Vite-Proxy `/api` → `localhost:8080`)

## Ziel

Das Backend soll alle Funktionen der Plattform abbilden:
1. Partnervorschläge auf Basis von Interessen/Hobbies und Standort (Solr)
2. Like-Typen (Like, Super-Like, …) und Matches
3. Messaging zwischen gematchten Usern

Kafka verteilt Events **zwischen** Komponenten/Services (statt REST-Calls). Echtzeit Browser ↔ Backend (Chat, Match-Benachrichtigung) läuft über **WebSockets**, Kafka liefert dahinter die Events.

## Entscheidungen (vom User getroffen)

| Thema | Entscheidung |
|---|---|
| Messaging | Im **selben** Backend-Service (als eigenes Modul, später herauslösbar) |
| Go-Recommendation-Service / Dating-Service (:3000) | **Entfällt.** Nur noch ein zentraler Service. Der Aufruf per RestTemplate ist bereits entfernt. |
| Like-Typen | `LIKE`, `SUPER_LIKE`, `COMPLIMENT` (Like mit Nachricht), `PASS`. Das Modell muss **leicht erweiterbar** sein (neue Typen ohne Umbau). |
| Standort | **Nur die Adressdaten, die der User einträgt** (PLZ/Stadt → Koordinaten). **Keine** Browser-Geolocation. |

## Zielarchitektur (modularer Monolith)

Schrittweise in fachliche Pakete umziehen, sobald ein Modul angefasst wird:

```
com.bjarne.datingrecommendationsuserservice
├── shared/        security (CurrentUser), error handling, kafka config
├── user/          Account, Profil, Präferenzen, Fotos, Standort
├── search/        Solr-Indexer (Kafka-Consumer) + Recommendation-Query
├── interaction/   Reactions (Like-Typen), Matches, Blocks
├── chat/          Nachrichten pro Match, REST + STOMP/WebSocket, Fan-out
├── music/         Spotify-Suche, Songs im Profil
├── games/         IGDB-Suche, Lieblings-Games im Profil
├── steam/         Steam-Import (Vorschläge aus der Bibliothek)
├── profile/       Gemeinsamkeiten zwischen zwei Usern (common, commonCount)
├── external/      Bausteine für externe APIs (Token-Cache, Rate-Limiter, HTTP-Client)
└── messaging/     Kafka-Producer (technisch, alle Module)
```

Kafka-Topics:

| Topic | Key | Producer | Consumer |
|---|---|---|---|
| `user-status-changed` (existiert) | userRef | user | search (aus Index entfernen) |
| `user-profile-updated` (existiert) | userRef | user | search (re-index) |
| `reaction-created` (existiert) | toRef | interaction | später notifications |
| `match-ended` (existiert) | matchRef | interaction | chat (WS-Push `ENDED`), später notifications |
| `match-created` (existiert) | matchRef | interaction | chat (WS-Push `CREATED`), später notifications |
| `chat-message-sent` (existiert) | matchRef | chat | chat (WS-Fan-out), später notifications |
| `chat-messages-read` (existiert) | matchRef | chat | chat (WS-Push Lesebestätigung) |
| `notification-created` (existiert) | userRef | notification | notification (WS-Push `/user/queue/notifications`) |

Grundsatz: Zustand (Like, Match, Nachricht) wird **synchron in Postgres** gespeichert. Kafka transportiert nur die Folgewirkungen. Events werden per `@TransactionalEventListener(AFTER_COMMIT)` publiziert (siehe `UserStatusChangedProducer`).

---

## Phase 0 – Fundament ✅ erledigt

- Kafka: `spring-boot-starter-kafka` (Boot-4-Autokonfiguration) statt eigener Producer-Beans. Jackson-3-Serializer `JacksonJsonSerializer`/`JacksonJsonDeserializer`. `KafkaConfiguration` enthält nur noch Topics.
- DTOs statt Entities in allen Responses (Passwort-Hash wurde vorher ausgeliefert). Request-DTOs statt Entity im Body (vorher Mass-Assignment, z.B. `id` → Übernahme fremder Accounts möglich).
- Auth: JWT-`sub` = `referenceId` (Claim `email` zusätzlich). Endpunkte für den eingeloggten User liegen unter `/api/me` und nutzen `@CurrentUser String referenceId` (`security/CurrentUser.java`). Gesperrte (`BLOCKED`) oder gelöschte (`MARKED_FOR_DELETION`) Accounts können sich nicht einloggen.
- Update-Bug behoben (vorher neue `referenceId` bei jedem Update). Updates nur noch per `PATCH /api/me` (Teil-Update) und `PUT /api/me/password`.
- Fehlerbehandlung: `exception/GlobalExceptionHandler` liefert `ProblemDetail`. Dazu `NotFoundException` (404), `ConflictException` (409), `BadRequestException` (400).
- Flyway: `V1__initial_schema.sql` (bisheriges Hibernate-Schema) und `V2__unique_email_and_contact_index.sql`. `ddl-auto=validate`. Bestehende DBs werden auf V1 gebaselined.
- Tests: `UserServiceTest` (Unit) und `UserApiIntegrationTest` (Testcontainers: Postgres + Kafka, `disabledWithoutDocker`). Testprofil `application-test.properties`.
- Frontend: Login über `/api/auth/login` → Token → `GET /api/me`. `src/api.js` (`apiFetch`) schickt den Bearer-Token mit. Stores und Views sind auf die neuen Endpunkte umgestellt.
- Frontend-Config aufgeräumt: `src/config.js` und die Variablen `VITE_API_URL_*` sind entfernt, ebenso der Proxy `/users` (alter Dating-Service :3000). Der Vite-Proxy liest `API_PROXY_TARGET` (ohne `VITE_`-Prefix, also nicht im Bundle; Standard `http://localhost:8080`) und leitet `/api` sowie `/ws` (`ws: true`, für Phase 4) weiter. Der User-Store übernimmt den Ablauf der gespeicherten Session aus dem JWT-Claim `exp`.
- Dev-Profil: `CorsConfiguration` hat die App mit `dev` nicht starten lassen (Bean-Name doppelt, Overriding ist in Boot verboten). Behoben, die Bean heißt jetzt `corsConfigurer`. Erlaubt ist `http://localhost:5173`, das wird auch für E2E gebraucht.
- Solr: Dokumente haben jetzt `id = referenceId`. Neu-Indexieren ersetzt das Dokument, statt (über den UUID-Prozessor des `_default`-Configsets) ein Duplikat anzulegen. Nicht aktive User werden beim Indexieren entfernt. Ältere Dokumente ohne diese `id` bleiben als Duplikate im Index, bis zum Re-Index in Phase 2.

### Aktuelle API

| Methode & Pfad | Auth | Zweck |
|---|---|---|
| `POST /api/user` | – | Registrierung (`RegistrationRequest`) → `MyProfileResponse` (201) |
| `POST /api/auth/login` | – | `{username, password}` → `access_token`, `refresh_token` |
| `POST /api/auth/refresh`, `POST /api/auth/logout` | – | `{refresh_token}`: neues Token-Paar (Rotation) bzw. abmelden (Features) |
| `GET /api/me` / `PATCH /api/me` | ✔ | eigenes Profil lesen / teilweise ändern |
| `PUT /api/me/password` | ✔ | Passwort ändern (meldet andere Geräte ab) |
| `PUT /api/me/privacy` | ✔ | `{showOnlineStatus}` (Features) |
| `GET /api/me/notifications`, `/unread-count`, `POST …/read` | ✔ | Benachrichtigungen und Badge-Zähler (Features) |
| `PATCH /api/me/status` | ✔ | `ACTIVE`/`INACTIVE`/`MARKED_FOR_DELETION` (nicht `BLOCKED`) → Kafka-Event |
| `DELETE /api/me` | ✔ | Konto löschen mit `{password}` (Sicherheit) |
| `POST /api/me/reports` | ✔ | User melden `{referenceId, reason, comment?, block?}` (Sicherheit) |
| `GET /api/admin/reports?status=` | Admin | Meldungen (Standard `OPEN`, älteste zuerst) (Sicherheit) |
| `POST /api/admin/reports/{referenceId}/resolve` | Admin | `{action: DISMISS|BLOCK_USER, note?}` (Sicherheit) |
| `PUT /api/admin/users/{referenceId}/status`, `/role` | Admin | Status `ACTIVE`/`INACTIVE`/`BLOCKED` bzw. Rolle `USER`/`ADMIN` (Sicherheit) |
| `POST /api/admin/search/reindex` | Admin | Solr-Index aus der DB neu aufbauen (Sicherheit) |
| `GET/PUT /api/me/preferences` | ✔ | Suchpräferenzen (Phase 1) |
| `GET /api/tags?category=INTEREST\|HOBBY` | – | Tag-Katalog (Phase 1) |
| `POST /api/me/reactions` | ✔ | Reaktion (`LIKE`, `SUPER_LIKE`, `COMPLIMENT`, `PASS`), Antwort enthält ggf. den neuen Match (Phase 3) |
| `GET /api/me/reactions/types` | ✔ | Typen mit Regeln und Rest-Kontingent für heute (Phase 3) |
| `GET /api/me/likes-received` | ✔ | offene positive Reaktionen an mich (Phase 3) |
| `GET /api/me/matches`, `DELETE /api/me/matches/{referenceId}` | ✔ | aktive Matches, Unmatch (Phase 3) |
| `GET/POST /api/me/blocks`, `DELETE /api/me/blocks/{referenceId}` | ✔ | Blocks (Phase 3) |
| `GET /api/me/conversations` | ✔ | Chats (= aktive Matches) mit letzter Nachricht und Ungelesen-Zähler (Phase 4) |
| `GET/POST /api/me/conversations/{matchReferenceId}/messages` | ✔ | Verlauf (`?before=&size=`), Nachricht senden (Phase 4) |
| `POST /api/me/conversations/{matchReferenceId}/read` | ✔ | alles Empfangene als gelesen markieren (Phase 4) |
| `/ws` (STOMP) | JWT im CONNECT | Echtzeit-Chat (Phase 4), siehe dort |
| `GET /api/me/recommendations?cursor=&size=` | ✔ | Vorschläge als `ProfileCard`s mit Cursor (Phase 2) |
| `POST /api/me/photo` | ✔ | Profilbild-Upload (nur `image/*`) |
| `GET /api/user?referenceId=` | ✔ | öffentliches Profil (`UserProfileResponse`, nur aktive User) inkl. `songs`, `games` und `common` (Musik & Games) |
| `GET /api/music/search?q=` | ✔ | Spotify-Suche, max. 10 Treffer (Musik & Games) |
| `PUT /api/me/songs` | ✔ | Songs im Profil ersetzen, max. 4, höchstens 1 `anthem` (Musik & Games) |
| `GET /api/games/search?q=` | ✔ | IGDB-Suche, max. 10 Treffer (Musik & Games) |
| `PUT /api/me/games` | ✔ | Lieblings-Games ersetzen, max. 6 (Musik & Games) |
| `POST /api/me/games/steam-suggestions` | ✔ | Spiele aus einer Steam-Bibliothek vorschlagen, speichert nichts (Musik & Games) |
| `GET /api/photos/{referenceId}` | ✔ | Redirect auf das Profilbild |

### Bekannte Restpunkte aus Phase 0
- V2 legt einen eindeutigen Index auf `lower(email)` an. Hat die lokale DB doppelte E-Mails, schlägt die Migration fehl. Dann die Duplikate löschen oder die DB neu aufsetzen.
- ~~Solr synchron im `UserService`~~ → in Phase 2 auf Kafka umgestellt.
- Ein End-to-End-Test im Browser mit dem kompletten Stack (Solr, SeaweedFS) steht noch aus.

---

## Phase 1 – Profil & Standort ✅ Backend erledigt, Frontend offen

Umgesetzt im Backend:
- Profil: `bio`, `birthDate` (ersetzt `age`, Alter wird berechnet und in Responses als `age` mitgeliefert, Mindestalter 18). `latitude`/`longitude` werden **nur serverseitig** aus der Adresse ermittelt.
- Präferenzen (`SearchPreferences`, Embeddable + Tabelle `user_seeking_genders`): `seekingGenders`, `minAge`, `maxAge`, `maxDistanceKm`. Standard: alle Geschlechter, 18–99, 50 km. Optional bei der Registrierung, sonst `PUT /api/me/preferences`.
- Tag-Katalog: Tabelle `tags` (Kategorie `INTEREST`/`HOBBY`, `slug`, `label`), Seed in `V4`, öffentlicher Endpoint `GET /api/tags`. Eingaben werden zu Slugs normalisiert (`"PLAYING_VIDEO_GAMES"`/`"Playing Video Games"` → `playing_video_games`), unbekannte Tags → 400. Neue Tags: neue Migration.
- Geocoding (`GeocodingService`): Lookup erst über die PLZ (bei mehreren Orten entscheidet die Stadt), Fallback über den Stadtnamen. Nicht auffindbar → 400. Das Land wird zu einem ISO-Code normalisiert („Germany“/„Deutschland“/„de“ → `DE`). Daten: `src/main/resources/geo/postal-codes-DE.tsv` (GeoNames, **CC BY 4.0, Quellenangabe nötig**). Die Datei ist **gitignored** und wird mit `scripts/prepare-postal-codes.sh [LAND]` erzeugt. `PostalCodeImporter` (ApplicationRunner) importiert beim Start jedes Land aus `geo/postal-codes-<LAND>.tsv`, solange es für dieses Land noch keine Zeilen gibt. Fehlt die Datei, loggt er eine Warnung, und Registrierungen schlagen mit 400 fehl. Ein weiteres Land: Skript mit dem Ländercode ausführen, kein Code nötig. Tests nutzen die kleine Fixture `src/test/resources/geo-test/postal-codes-DE.tsv` (Property `geo.postal-codes.location`).
- Fotos: in der DB steht nur der Object-Key (`photo_key`, Dateiname UUID). `ProfileMapper` signiert die URLs bei jedem Lesen (`storage.url-expiry`, Standard 12h). Beim Austausch wird das alte Bild gelöscht.
- `DevDataSeedRunner`: echte PLZ und Orte (ohne Großkunden), Tags aus dem Katalog, Geburtsdatum, Bio. Die Präferenzen sind für alle gleich weit gefasst (alle Geschlechter, 18–99, 1000 km), damit sich alle Fake-User gegenseitig sehen (siehe E2E-Testdaten).
- Migrationstest `MigrationUpgradeTest`: prüft den Weg von einer V2-DB mit Daten auf den neuesten Stand (`age` → `birth_date`, Foto-URL → Key, Standard-Präferenzen).

**Offen – Frontend-Anpassungen (Breaking Changes der API):**
- Registrierung: `age` → `birthDate` (ISO-Datum), `bio` ergänzen, `status` nicht mehr senden. Gender-Wert `DIVERS` → `DIVERSE` (war vorher schon ein Bug, das Backend kennt nur `DIVERSE`).
- Hobbies/Interessen-Optionen aus `GET /api/tags` laden statt fest im Code. Das Backend liefert Slugs zurück (`hiking`), Labels kommen aus dem Katalog.
- Adresse: PLZ, Stadt und Land sind Pflicht, die Straße ist optional. Ein 400 mit `detail` anzeigen, wenn die Adresse nicht gefunden wird.
- Neue Seite oder neuer Bereich „Suchpräferenzen“ (`GET/PUT /api/me/preferences`).
- Profil: `photo` ist eine signierte URL, die abläuft (Standard 12h). Den Avatar nicht dauerhaft cachen, sondern über `GET /api/me` neu laden. `bio`, `age` und `city` anzeigen.

Bekannte Punkte:
- Bestehende Interessen und Hobbies in alten Dev-Daten (Faker-Werte) sind nicht im Katalog. Sie bleiben stehen, bis der User sie ändert. Für saubere Testdaten die lokale DB neu aufsetzen, dann seedet der `DevDataSeedRunner` neu.
- Bestehende User ohne Koordinaten bekommen sie erst beim nächsten Adress-Update. Für Phase 2 ggf. ein einmaliges Backfill (Job oder Endpoint) über `GeocodingService`.

## E2E-Testdaten

Feste Test-User für die End-to-End-Tests des Frontends (Playwright im FE-Repo).

**Start:** `./gradlew bootRun --args='--spring.profiles.active=dev,e2e'`

- Profil `dev`: CORS für `http://localhost:5173`, Compose-Stack, `DevDataSeedRunner` (100 Faker-User, nur bei leerer DB).
- Profil `e2e`: `E2eDataSeedRunner` (`config/e2e`, `@Order(30)`, läuft nach PLZ-Import und Dev-Seeder). Er setzt **bei jedem Start** die festen User komplett zurück: Profil, Passwort, Status, Präferenzen, Tags, Adresse, Koordinaten, kein Profilbild. Alle Kontakte, an denen sie beteiligt sind, werden gelöscht und die festen Beziehungen neu angelegt. Danach werden sie neu in Solr indexiert.
- Die Werte stehen zentral in `config/e2e/E2eUsers.java`, Backend-Tests verwenden sie wieder. Die E-Mails, referenceIds und das Passwort stehen zusätzlich in `application-e2e.properties` (`e2e.seed.*`). Weichen die Properties vom Code ab, startet die App nicht.
- Es gibt **keinen** Reset- oder Test-Endpunkt. Zurückgesetzt wird durch einen Neustart mit `e2e`. Eine komplett frische DB gibt es mit `docker compose down -v`.
- Dev-Daten sind deterministisch: `Faker(Locale.GERMANY, new Random(42))`, ein gemeinsamer `Random`, stabile Reihenfolge der Orte und ein festes Bezugsdatum für Geburtsdaten. Jede neu angelegte DB enthält dieselben 100 User. Die Faker-User wohnen nie in Berlin und bekommen nie die reservierten E2E-Tags (`science`, `animals`, `climbing`, `board_games`). Deshalb stehen bob und carol bei alice immer auf den ersten beiden Plätzen. Danach können Faker-User aus dem Umland folgen, die alle Filter erfüllen, aber keine gemeinsamen Tags haben. E2E-Tests sollten also „die ersten zwei sind bob und carol“ prüfen, nicht „genau zwei Vorschläge“.
- **Alle Fake-User sehen sich gegenseitig:** Sie suchen alle Geschlechter, 18–99 Jahre, bis 1000 km (mehr als jede Entfernung in Deutschland). Mit zufälligen Präferenzen sah vorher fast niemand jemanden (nur 10 von 4950 Paaren erfüllten Geschlecht, Alter und Entfernung in beide Richtungen, 84 der 100 User sahen niemanden), weil die Filter in beide Richtungen gelten. `E2eDataSeedIntegrationTest.allFakeUsersCanSeeEachOther` sichert das ab. Ein frisch registrierter User mit Standard-Präferenzen (50 km) sieht trotzdem nur Fake-User im Umkreis von 50 km, denn sein eigener Radius gilt weiter. Auch wenn die Dev-Daten geändert werden: bestehende DBs behalten die alten Präferenzen, für die neuen Daten die DB neu aufsetzen (`docker compose down -v`).

Passwort für alle: `e2e-password`

**Songs und Games (Musik & Games):** Der Seeder schreibt feste Kopien direkt in die DB, ohne Aufrufe an Spotify oder IGDB (Quelle: `E2eUsers`, dort stehen auch die Track-IDs). Die Track-IDs und IGDB-IDs sind nicht gegen die Dienste geprüft. Es gibt keine Bilder (`imageUrl`/`coverUrl` sind `null`), wie bei den Usern ohne Profilfoto. Der Reset löscht und legt die Einträge bei jedem Start neu an.

| User | Songs (Reihenfolge = Position) | Games |
|---|---|---|
| alice | **Mr. Brightside** (The Killers, `anthem`), Blinding Lights (The Weeknd), Knights of Cydonia (Muse) | The Witcher 3 (`1942`), Hades (`113112`), Hollow Knight (`14593`) |
| bob | **Somebody Told Me** (The Killers, `anthem`), Blinding Lights (The Weeknd) | The Witcher 3 (`1942`), Hollow Knight (`14593`), Elden Ring (`119133`) |
| carol | **Smooth Criminal** (Michael Jackson, `anthem`) | Stardew Valley (`17000`), Portal 2 (`72`) |
| blocked | – | – |
| admin | – | – |

Überschneidungen für „Das habt ihr gemeinsam“: alice und bob teilen die Artists `The Killers` und `The Weeknd` (der Song Blinding Lights ist identisch) sowie die Games `1942` und `14593`. Mit carol gibt es nur Tags, keine Artists oder Games. Bei alice enthält die Karte von bob `commonCount` = 7 (2 Interessen, 1 Hobby, 2 Artists, 2 Games), die von carol 3.

| User | referenceId | E-Mail | Zweck |
|---|---|---|---|
| alice | `00000000-0000-0000-0000-00000000a11c` | `e2e-alice@test.local` | Haupt-User, weiblich, Berlin 10115, sucht m/w 25–40 im Umkreis von 25 km. bob und carol sind ihre ersten beiden Vorschläge. |
| bob | `00000000-0000-0000-0000-000000000b0b` | `e2e-bob@test.local` | männlich, gleiche Stadt, überlappende Tags |
| carol | `00000000-0000-0000-0000-00000000ca01` | `e2e-carol@test.local` | weiblich, gleiche Stadt, hat alice bereits gelikt (`LIKE`-Reaction carol → alice): Ein Like von alice erzeugt sofort einen Match, und carol steht bei alice unter `likes-received`. alice hat dazu eine ungelesene `NEW_LIKE`-Benachrichtigung. |
| blocked | `00000000-0000-0000-0000-00000000b10c` | `e2e-blocked@test.local` | Status `BLOCKED`, Login schlägt mit 401 fehl, nicht im Suchindex |
| admin | `00000000-0000-0000-0000-00000000ad01` | `e2e-admin@test.local` | Rolle `ADMIN`: Meldungen prüfen, User sperren, Rollen vergeben, Re-Index (`/api/admin/**`). Wohnt in München, taucht also nicht in alice' Vorschlägen auf. Alle anderen E2E-User werden beim Reset auf Rolle `USER` gesetzt, und alle Meldungen, an denen E2E-User beteiligt sind, werden gelöscht. |

Hinweis für Tests: **Das Alter immer aus `birthDate` berechnen, nicht fest eintragen** (die Geburtsdaten sind fest, das Alter ändert sich mit der Zeit).

Bei neuen Phasen erweitern: Der Reset löscht Reactions, Matches und Blocks der E2E-User. Nachrichten verschwinden mit ihren Matches (`on delete cascade`). Neue Tabellen, die an User hängen, müssen hier ebenfalls zurückgesetzt werden.

## Phase 2 – Recommendations v2 (Solr) ✅ Backend erledigt, Frontend offen

Umgesetzt im Backend (neues Paket `search`):
- **Indexierung nur noch über Kafka:** `UserService` publiziert `UserProfileUpdatedEvent` (Topic `user-profile-updated`) bei Registrierung, Profil- und Präferenzänderung, nach dem Commit. `SearchIndexConsumer` (Gruppe `user-service-search-indexer`) hört auf `user-profile-updated` und `user-status-changed`. Er lädt den aktuellen Stand aus der DB, indexiert aktive User und entfernt alle anderen. Das Event enthält nur die referenceId, deshalb ist die Reihenfolge der Events egal. Der Demo-Consumer `UserStatusChangedConsumer` ist entfernt.
- `SearchIndexService`: Solr-Dokument mit `id = referenceId`, `status`, `gender`, `birthDate`, `location`, `interests`, `hobbies` und den Präferenzen des Users (`seekingGenders`, `minAge`, `maxAge`, `maxDistanceKm`), damit in beide Richtungen gefiltert werden kann. `commitWithin` 1 s.
- `SearchIndexInitializer` (Start, `@Order(40)`): trägt fehlende Koordinaten nach (alte Accounts ohne lat/lon, über `GeocodingService`) und baut den Index komplett neu auf, wenn `recommender.solr.reindex-on-startup=true` (in `application.properties` für dev an). Ein Re-Index entfernt auch alte Dokumente ohne `id`. **Kein** Admin-Endpoint, weil es keine Rollen gibt.
- Schema (`SolrConfiguration`): neue Felder `status`, `birthDate` (pdate), `location` (location), `seekingGenders`, `minAge`, `maxAge`, `maxDistanceKm`. Felder werden nur ergänzt, nie geändert. Ein geänderter Typ braucht eine neue Collection. `compose.yaml` ist auf `solr:10.0.0` gepinnt (passend zu SolrJ 10, Cloud-Modus).
- **Query** (`RecommendationService`, edismax, POST):
  - Filter in beide Richtungen: Geschlecht (ich suche ihres, sie suchen meins), Alter (`birthDate` in meinem Bereich, mein Alter in ihrem `minAge`..`maxAge`), Distanz (`{!geofilt}` mit meinem Radius und `{!frange}sub(geodist(),maxDistanceKm)` mit ihrem).
  - Ausgeschlossen: ich selbst, alle, auf die ich schon reagiert habe (Reactions), und Blocks in beide Richtungen (`{!bool must_not=…}` mit `{!terms f=id}`).
  - Ranking: `bq` auf gemeinsame Interessen (Boost 4) und Hobbies (Boost 3), seltene Tags zählen mehr. Distanz als Multiplikator `recip(geodist(),1,100,100)` (100 km halbieren den Score). Gleichstand wird über `id` entschieden.
  - Pagination per Solr-`cursorMark`: `GET /api/me/recommendations?cursor=&size=` (size 1–50, Standard 10) → `{items: ProfileCard[], nextCursor}`. `nextCursor` ist `null`, wenn es keine weiteren Seiten gibt.
- `ProfileCard`: `referenceId`, `name`, `bio`, `age`, `gender`, `city`, `photo` (signiert), `interests`, `hobbies`, `distanceKm` (gerundet, Basis sind die PLZ-Mittelpunkte), `compatibility` (Anteil gemeinsamer Tags in %, Jaccard, Interessen und Hobbies getrennt gezählt).
- Tests: `RecommendationIntegrationTest` läuft gegen **echtes Solr 10** (Testcontainers) mit den E2E-Usern. Für jeden Filter gibt es einen User, der genau diesen Filter verletzt. Dazu Ranking, Distanz, Cursor, Ausschluss nach Bewertung/Block und Index-Updates über Kafka (Statuswechsel, Profiländerung).

**Offen – Frontend-Anpassungen (Breaking Change):**
- `GET /api/me/recommendations` liefert jetzt `{items, nextCursor}` mit fertigen Karten statt `recommendationUserIds`. Der zweite Call über `POST /api/user/search` in `MatchesView` entfällt.
- `ProfileTile.vue`: `distance` → `distanceKm`, `location` → `city`, `avatar` → `photo`. `compatibility` heißt gleich.
- Weitere Karten über `nextCursor` nachladen (Infinite Scroll oder „Mehr“).
- Nach Like oder Pass verschwindet der User beim nächsten Abruf automatisch, die Contacts müssen dafür nicht mehr im Frontend gefiltert werden.

Bekannte Punkte:
- Indexierung ist eventual consistent (Kafka + `commitWithin` 1 s): Eine Änderung ist nach etwa 1–2 s in den Vorschlägen sichtbar.
- Geht ein Event verloren (Kafka beim Commit nicht erreichbar), bleibt der Index veraltet, bis zum nächsten Re-Index beim Start. Wenn das stört: Transactional Outbox (Phase 5).
- `POST /api/user/search` ist entfernt (das Frontend nutzt ihn nicht mehr).
- **Behobener Bug (Okt. 2026): Registrierte User landeten nicht im Index.** Ursache: Der `HttpJdkSolrClient` nutzte HTTP/2 über Klartext-HTTP. Nach einer Leerlaufphase verwarf Solrs Jetty die Verbindung, das nächste `add` scheiterte mit `RST_STREAM: Protocol error` (oder hing). Spring Kafka wiederholte 9-mal ohne Pause auf derselben kaputten Verbindung und übersprang das Event danach. Ergebnis: Lag 0, aber kein Dokument. Fix: Der Solr-Client nutzt HTTP/1.1 (`SolrConfiguration`). Zusätzlich gilt für alle Listener eine Fehlerbehandlung mit Backoff (0,5/1/2/4/8 s), danach landet das Event im Dead-Letter-Topic `<topic>.dlt` statt verloren zu gehen. Abgesichert durch `SearchIndexingIntegrationTest`: Solr-Idle-Timeout auf 2 s, ohne Fix rot. Liegt etwas in einem `.dlt`-Topic, Ursache beheben und neu starten, der Re-Index beim Start holt den Stand nach.

## Phase 3 – Like-Typen & Matches ✅ Backend erledigt, Frontend offen

Umgesetzt im Backend (neues Paket `interaction`, Migration `V7`; V6 ist absichtlich übersprungen, siehe Hinweis in der Migration):
- **Reaktionstypen** (`ReactionType`): `LIKE`, `SUPER_LIKE`, `COMPLIMENT` (Like mit Pflicht-Nachricht, max. 500 Zeichen), `PASS`. Jeder Typ definiert, ob er positiv zählt und ob eine Nachricht verboten, optional oder Pflicht ist. Gespeichert als String, **ein neuer Typ ist nur eine neue Enum-Konstante, ohne Migration**. Tageslimits per Konfiguration (`interaction.daily-limits.SUPER_LIKE=3`, `COMPLIMENT=5`, Tagesgrenze in `interaction.time-zone`, Standard Europe/Berlin). Limit erreicht → 429.
- `Reaction`: höchstens eine pro Richtung, nicht änderbar (zweite Reaktion → 409). Nur aktive Profile dürfen reagieren. Ziel muss aktiv und nicht geblockt sein (sonst 404, ohne zu verraten, warum).
- **Match:** Ist eine Reaktion positiv und die Gegenseite hat schon positiv reagiert, entsteht in derselben Transaktion ein `Match` (Paar sortiert nach User-ID gespeichert, eindeutig). Beide User-Zeilen werden per `SELECT … FOR UPDATE` gesperrt, damit zwei gleichzeitige Likes genau einen Match erzeugen. Ein Test belegt das: Ohne Sperre entsteht gar keiner. Die Antwort auf die Reaktion enthält den neuen Match.
- **Unmatch:** Der Match wird `ENDED` und bleibt für die Historie. Die Reaktionen bleiben, das Paar kann also nicht erneut matchen.
- **Block:** wirkt in beide Richtungen. Keine Vorschläge, keine Reaktionen, das Profil ist über `GET /api/user` und `/search` nicht sichtbar, offene Likes werden ausgeblendet, ein aktiver Match endet. Blocken ist idempotent, Freigeben per `DELETE`.
- **Events** nach dem Commit: `reaction-created` (Key: Empfänger), `match-created`, `match-ended` (mit Grund `UNMATCHED`/`BLOCKED`). Consumer kommen in Phase 4 (Conversation anlegen/schließen) und Phase 5 (Benachrichtigungen).
- **Recommendations** schließen jetzt alle aus, auf die ich reagiert habe, und Blocks in beide Richtungen.
- **Contacts abgelöst:** `V7` überführt `ACCEPTED` → `LIKE`, `REJECTED` → `PASS`, `BLOCKED` → Block. Gegenseitige Likes werden zu Matches. Danach wird `contacts` gelöscht. `/api/me/contacts` gibt es nicht mehr.
- Tests: `InteractionIntegrationTest` (Match-Ablauf, PASS, Nachrichtenregeln, Tageslimit, Unmatch, Blocks, gleichzeitige Likes), der Migrationstest um alte Contact-Daten erweitert, E2E-Seeder und Recommendation-Test auf Reactions umgestellt.

**Offen – Frontend-Anpassungen (Breaking Change):**
- Like/Pass: `POST /api/me/reactions {targetReferenceId, type, message?}` statt `/api/me/contacts`. Bei `201` steht in `match` der neue Match, dann z.B. „It's a match!“ anzeigen.
- Neue Like-Typen anbieten: `GET /api/me/reactions/types` liefert Typen, Nachrichtenregel und `remainingToday` (Super-Like-Button deaktivieren bei 0, für Compliment ein Textfeld). 429 abfangen.
- `GET /api/me/likes-received` für eine „Wer mag mich“-Ansicht. Die Karte enthält `reaction.type` und bei Compliments `reaction.message`.
- Matches-Liste über `GET /api/me/matches` (z.B. als Quelle für „Neuer Chat“ in `ChatModal.vue` statt Contacts mit Status ACCEPTED). Unmatch über `DELETE /api/me/matches/{referenceId}`.
- Blocken/Freigeben: `POST /api/me/blocks {referenceId}`, `GET /api/me/blocks`, `DELETE /api/me/blocks/{referenceId}`.
- Contacts aus dem User-Store entfernen. Das Filtern bewerteter Profile übernimmt das Backend.

## Phase 4 – Messaging (im selben Service) ✅ Backend erledigt, Frontend offen

**Abweichung vom ursprünglichen Plan:** Es gibt keine eigene `Conversation`, die ein Kafka-Consumer asynchron nach dem Match anlegt. **Die Conversation ist der Match**, Nachrichten hängen direkt am Match. Grund: Das Frontend bekommt den Match sofort in der Like-Antwort. Eine asynchron angelegte Conversation gäbe es in dem Moment oft noch nicht („Chat öffnen“ → 404). So gibt es keine Race-Condition und keine Daten, die auseinanderlaufen können. Ein Unmatch oder Block beendet den Chat automatisch. Kafka wird dort genutzt, wo es echten Nutzen hat: beim Verteilen an die WebSocket-Sessions aller Instanzen.

Umgesetzt im Backend (neues Paket `chat`, Migration `V8`):
- `Message(referenceId, match, sender, content ≤ 2000, createdAt, readAt)`. `matches.last_message_at` dient zum Sortieren der Chat-Liste. Nachrichten werden mit ihrem Match gelöscht.
- Chatten können nur die beiden Teilnehmer eines **aktiven** Matches, wenn beide Profile aktiv sind. Alles andere sieht aus, als gäbe es den Chat nicht (404). Absender muss aktiv sein. Leere oder zu lange Nachrichten → 400.
- **REST** (`/api/me/conversations`):
  - `GET`: Chats mit `user`, `lastMessage`, `unreadCount`, `matchedAt`, nach letzter Aktivität sortiert.
  - `GET /{matchReferenceId}/messages?before=&size=`: Verlauf, älteste zuerst, `hasMore`. Ältere Nachrichten lädt man mit `before = items[0].referenceId`. size 1–100, Standard 30.
  - `POST /{matchReferenceId}/messages {content}` → 201. `POST /{matchReferenceId}/read` → 204.
- **WebSocket/STOMP** an `/ws` (Handshake ohne Login, Authentifizierung über `Authorization: Bearer <token>` im CONNECT-Frame, weil Browser beim Handshake keine Header setzen können):
  - Senden: `/app/chat.send {matchReferenceId, content}`, `/app/chat.read {matchReferenceId}`.
  - Empfangen (nur eigene `/user/queue/…`-Ziele erlaubt, alles andere beendet die Session):
    - `/user/queue/messages` (`MessageResponse`, an **beide** Teilnehmer, damit andere Tabs des Absenders mitlaufen)
    - `/user/queue/read-receipts` (`{matchReferenceId, readerReferenceId, readAt}`)
    - `/user/queue/conversations` (`{type: CREATED|ENDED, matchReferenceId}` bei neuem Match, Unmatch oder Block)
    - `/user/queue/errors` (Fehler von STOMP-Befehlen)
  - Erlaubte Origins: `app.websocket.allowed-origins` (Standard `http://localhost:5173`). Über den Vite-Proxy (`/ws`, `ws: true`) passt das.
- **Ablauf:** Die Nachricht wird in Postgres gespeichert, nach dem Commit geht `chat-message-sent` raus (Key: Match, nur IDs, **kein Nachrichtentext in Kafka**). `ChatFanoutConsumer` läuft in jeder Instanz mit eigener zufälliger Consumer-Gruppe (`auto.offset.reset=latest`), lädt die Nachricht und pusht sie an die verbundenen Sessions. Genauso laufen `chat-messages-read`, `match-created` und `match-ended`.
- Tests: `ChatIntegrationTest` mit echtem STOMP-Client gegen den Server auf zufälligem Port: Verlauf und Pagination, Lesestatus, Zugriffsregeln, Push von Nachrichten, Lesebestätigungen und Match-Events, Fehler-Queue, Ablehnung ohne Token oder bei fremden Zielen. Per Gegenprobe ist belegt, dass die Tests ohne die Prüfungen fehlschlagen.

**Offen – Frontend-Anpassungen:**
- `ChatMenu.vue`: Mock-Daten durch `GET /api/me/conversations` ersetzen (Name, Foto, `lastMessage.content`, `unreadCount`). `isOnline` gibt es noch nicht (Phase 5).
- `Chat.vue`: Verlauf über `GET …/messages`, beim Hochscrollen mit `before` nachladen. Senden über STOMP `/app/chat.send` oder REST. Beim Öffnen `…/read` bzw. `/app/chat.read` aufrufen. Eigene Nachrichten erkennt man an `senderReferenceId === user.referenceId`.
- STOMP-Client (z.B. `@stomp/stompjs`) einmal nach dem Login verbinden (`brokerURL: ws(s)://<host>/ws`, `connectHeaders: {Authorization: 'Bearer ' + token}`), die vier Queues abonnieren und neu verbinden, wenn das Token erneuert wird.
- `ChatModal.vue` („Neuer Chat“) entfällt: Jeder Match ist automatisch ein Chat. Den Call auf `localhost:8081/create-chat` entfernen.

Bekannte Punkte:
- Ein abgelaufenes JWT beendet eine bestehende WebSocket-Session nicht. Das Frontend sollte nach einem Token-Refresh neu verbinden.
- Jede Instanz erzeugt beim Start eine neue Kafka-Consumer-Gruppe für den Fan-out. Kafka räumt verwaiste Gruppen nach `offsets.retention.minutes` (Standard 7 Tage) selbst auf.
- Nachrichten, die während eines Verbindungsabbruchs per Push verpasst werden, holt das Frontend nach dem Reconnect über `GET /api/me/conversations` und den Verlauf.

## Musik & Lieblings-Games im Profil ✅ Backend erledigt, Frontend offen

Das Frontend baut gegen diesen Vertrag. Umgesetzt in den Paketen `music`, `games`, `steam`, `profile` und `external`, Migration `V9`.

### Grundsatz

- Externe APIs (Spotify, IGDB, Steam) werden **nur zum Suchen und Auswählen** benutzt. Ausgewählte Songs und Games speichert das Backend als **Kopie** in Postgres (Name, Cover-URL usw.). **Profile anzeigen ruft nie eine externe API auf**, die Tests belegen das (Dienst "down" und trotzdem vollständige Profile).
- Zugangsdaten nur per Umgebungsvariable bzw. Property, nie im Repo (Tabelle unten). Ohne Zugangsdaten startet die App normal, die Such-Endpunkte antworten dann 503.
- Fällt ein Dienst aus, ist das Kontingent erschöpft oder fehlt die Konfiguration, antwortet der betroffene Endpunkt mit **503 als ProblemDetail**. Der Rest der App läuft weiter. Das Frontend zeigt dann die Meldung aus `detail` und lässt die Suche ausgeblendet oder wiederholbar.

| Endpunkt | `detail` bei 503 |
|---|---|
| `GET /api/music/search`, `PUT /api/me/songs` (nur wenn ein neuer Track nachgeschlagen werden muss) | `Music search is unavailable right now` |
| `GET /api/games/search`, `PUT /api/me/games` (nur bei neuen Games), `POST …/steam-suggestions` (wenn IGDB ausfällt) | `Game search is unavailable right now` |
| `POST /api/me/games/steam-suggestions` (wenn Steam ausfällt) | `Steam is unavailable right now` |

Beispiel: `{"type":"about:blank","title":"Service Unavailable","status":503,"detail":"Music search is unavailable right now","instance":"/api/music/search"}`

### Antwortformen (überall gleich)

```jsonc
// Song (in MyProfileResponse.songs, UserProfileResponse.songs, Antwort von PUT /api/me/songs)
{ "spotifyTrackId": "003vvx7Niy0yvhvHt4a68B", "name": "Mr. Brightside", "artists": ["The Killers"],
  "album": "Hot Fuss", "imageUrl": "https://i.scdn.co/image/…", "spotifyUrl": "https://open.spotify.com/track/003vvx7Niy0yvhvHt4a68B",
  "anthem": true }

// Game (in MyProfileResponse.games, UserProfileResponse.games, Antwort von PUT /api/me/games)
{ "igdbId": 1942, "name": "The Witcher 3: Wild Hunt",
  "coverUrl": "https://images.igdb.com/igdb/image/upload/t_cover_big/co1wyy.jpg",
  "year": 2015, "platforms": ["PC", "PS4", "Switch"], "steamAppId": 292030 }
```

`imageUrl`, `coverUrl`, `year` und `steamAppId` können `null` sein. `spotifyUrl` wird aus der Track-ID gebildet. Die Reihenfolge der Listen ist die gespeicherte Position.

### Spotify

- `GET /api/music/search?q=` (✔ Auth) → `[{ spotifyTrackId, name, artists: [string], album, imageUrl, spotifyUrl }]` (ohne `anthem`). `q` mindestens 2, höchstens 100 Zeichen (sonst 400), höchstens 10 Treffer. Treffer werden 10 Minuten gecacht (Schlüssel: Query ohne Rand-/Doppel-Leerzeichen, klein geschrieben), das Frontend kann also bedenkenlos pro Tastendruck (mit Debounce) suchen.
- `PUT /api/me/songs` Body `[{ "spotifyTrackId": "…", "anthem": false }]` → **ersetzt die Liste komplett**, Antwort `200` mit der gespeicherten Liste (Song-Form). Die Reihenfolge im Body ist die Position. Ein leeres Array entfernt alle Songs.
  - Höchstens **4** Songs, höchstens **1** mit `anthem: true` (Profilsong), keine doppelten Track-IDs, jede ID muss die Form einer Spotify-ID haben (22 Zeichen, Buchstaben/Ziffern). Verstöße → **400**.
  - Unbekannte Track-ID → **400** (`detail`: `Unknown Spotify track: <id>`). Das Backend holt die Metadaten selbst; das Frontend schickt nur ID und `anthem`. Tracks, die schon im Profil oder in einer aktuellen Suche vorkommen, brauchen keinen Spotify-Aufruf.
  - Fehlgeschlagene Requests ändern nichts (alles oder nichts).
- Tabelle `user_songs` (`user_id`, `position`, `spotify_track_id`, `name`, `artists`, `album`, `image_url`, `anthem`); eindeutig pro User: Position, Track und (per Teilindex) der Anthem.
- Technik: Client-Credentials-Flow (kein User-OAuth), Token wird gecacht und 5 Minuten vor Ablauf erneuert; ein abgelehntes Token (401) wird einmal ersetzt. Bei **429** (laut Doku `"reason": "QUOTA_EXCEEDED"`) sendet das Backend bis zum `Retry-After` (höchstens 1 Stunde, Standard 30 s) gar keine Anfragen mehr und antwortet 503, um das Kontingent zu schonen.

**Stand der Spotify-Doku (geprüft am 11.10.2026 gegen developer.spotify.com):**
- Development Mode: höchstens **5 autorisierte User** (betrifft uns nicht, es gibt keinen User-Login), der App-Besitzer braucht **Premium**. Der Extended Quota Mode ist laut Doku nur für registrierte Organisationen mit laufendem Dienst und mindestens 250k MAU zu bekommen, ist also für uns keine Option. Wir bleiben im Development Mode und schonen das Kontingent über Caching.
- Die Kontingente sind pro Developer-Account in "Buckets" gruppiert; Zahlen nennt die Doku nicht. Überschreitung → 429 mit `QUOTA_EXCEEDED`.
- Februar-2026-Änderungen: `GET /search` liefert höchstens **10** (Standard 5) Treffer pro Typ. `GET /tracks` (mehrere Tracks) ist entfernt, **`GET /tracks/{id}` ist weiter verfügbar** und wird zur Validierung genutzt. Der oEmbed-Endpunkt als Ersatz ist deshalb nicht nötig. Aus dem Track-Objekt sind `available_markets`, `linked_from` und `popularity` entfernt; das Backend nutzt nur `id`, `name`, `artists[].name`, `album.name`, `album.images`.

### Games (IGDB)

- `GET /api/games/search?q=` (✔ Auth) → `[{ igdbId, name, coverUrl, year, platforms, steamAppId }]`, `q` 2–100 Zeichen, höchstens 10 Treffer, 10 Minuten Cache. `coverUrl` = `https://images.igdb.com/igdb/image/upload/t_cover_big/{image_id}.jpg` (`null` ohne Cover), `platforms` als IGDB-Kürzel (`PC`, `PS5`, `Switch` …, ersatzweise der Plattformname), `steamAppId` aus IGDBs `external_games` mit Quelle Steam (`external_game_source = 1`), sonst `null`.
- `PUT /api/me/games` Body `[{ "igdbId": 1942 }]` → ersetzt die Liste komplett, Antwort `200` mit der Liste (Game-Form). Höchstens **6** Games, keine doppelten, `igdbId` Pflicht und größer 0; Verstöße und **unbekannte IDs → 400** (`Unknown game: <id>`).
- Tabelle `user_games` (`user_id`, `position`, `igdb_id`, `name`, `cover_url`, `year`, `platforms`, `steam_app_id`); eindeutig pro User: Position und Game.
- Technik: Twitch-Client-Credentials, das Token (~60 Tage) wird gecacht. **Rate-Limit 4 Anfragen pro Sekunde** (Doku; zusätzlich höchstens 8 offene Anfragen gleichzeitig): ein Limiter (`RequestRateLimiter`) verteilt alle Aufrufe über alle Threads gleichmäßig und lehnt Anfragen, die länger als 2 s warten müssten, mit 503 ab, statt sie aufzustauen. Antwortet IGDB mit 429, ebenfalls 503.

### Steam-Import (nur Vorschläge, kein automatisches Speichern)

- `POST /api/me/games/steam-suggestions` Body `{ "profile": "<Steam-Profil-URL, Vanity-Name oder SteamID64>" }` → `[{ igdbId, name, coverUrl, steamAppId, playtimeHours }]`, nach Spielzeit absteigend, höchstens **20**.
  - Akzeptiert: `https://steamcommunity.com/profiles/<SteamID64>`, `https://steamcommunity.com/id/<vanity>` (auch ohne `https://`), eine SteamID64 (17 Ziffern) oder ein Vanity-Name. Vanity-Namen löst das Backend per `ResolveVanityURL` auf. Alles andere → 400, unbekannter Vanity-Name → **404** (`Steam profile not found`).
  - Quelle ist `GetOwnedGames` (mit `include_appinfo`, inklusive Free-to-play), nur wenn die Bibliothek leer ist `GetRecentlyPlayedGames`. Jede Steam-App wird mit **einer** IGDB-Anfrage (`external_games`) auf ein IGDB-Spiel abgebildet.
  - `igdbId` ist `null`, wenn IGDB die App nicht kennt. Dann ist `name` der Steam-Name und `coverUrl` = `https://cdn.cloudflare.steamstatic.com/steam/apps/{appid}/header.jpg`. Kennt IGDB das Spiel, aber ohne Cover, wird ebenfalls das Steam-Bild genutzt. Mehrere Steam-Apps desselben IGDB-Spiels (Demo, regionale Version) erscheinen nur einmal, die meistgespielte zuerst.
  - `playtimeHours` = Gesamtspielzeit in Stunden, auf eine Nachkommastelle gerundet.
  - Privates Profil → **422** mit `Your Steam game details are private. Set 'Game details' to public in your Steam privacy settings and try again.` Öffentliche Details, aber keine Spiele → 422 `No games are visible on your Steam profile`.
  - Die SteamID wird **nicht gespeichert und nicht geloggt**. Der API-Key steht in der Steam-URL, deshalb loggt der Client nie Exception-Meldungen oder URLs.
- Der User wählt aus den Vorschlägen. Gespeichert wird über `PUT /api/me/games`, **nur Einträge mit `igdbId`** (das Frontend blendet "Übernehmen" bei `igdbId: null` aus).

### Profile erweitern

- `MyProfileResponse` und `UserProfileResponse` haben zusätzlich `songs` (Song-Form) und `games` (Game-Form), immer als Liste (leer, nie `null`). `UserProfileResponse` steckt auch in Matches, Likes, Blocks und Chats, dort sind die Listen ebenfalls enthalten.
- `GET /api/user?referenceId=` liefert zusätzlich `common`, die Gemeinsamkeiten mit dem eingeloggten User (für "Das habt ihr gemeinsam" und Kompliment-Vorschläge):

  ```json
  "common": { "interests": ["music", "art"], "hobbies": ["reading"], "artists": ["The Killers"], "games": [1942, 14593] }
  ```

  Alle vier Listen sind immer vorhanden (gegebenenfalls leer). Interessen/Hobbies sind Tag-Slugs, `artists` sind Namen (Vergleich ohne Groß-/Kleinschreibung, alle Artists aller Songs, also auch bei unterschiedlichen Tracks), `games` sind IGDB-IDs. Die Reihenfolge folgt dem eingeloggten User. In allen anderen Antworten, die `UserProfileResponse` enthalten, **fehlt** das Feld `common`.
- `ProfileCard` (Recommendations) hat zusätzlich `anthem` (Song-Form oder `null`) und `commonCount` (Anzahl gemeinsamer Interessen, Hobbies, Artists und Games). `compatibility` bleibt unverändert (Jaccard über Tags).
- Nach jeder Änderung an Songs oder Games wird `user-profile-updated` publiziert (wie bei Profiländerungen).

### Konfiguration (nur Umgebung/Properties)

| Variable | Zweck | Bezug |
|---|---|---|
| `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET` | Spotify-App, Client-Credentials-Flow | https://developer.spotify.com/dashboard (Besitzer braucht Premium) |
| `TWITCH_CLIENT_ID`, `TWITCH_CLIENT_SECRET` | Twitch-App für IGDB | https://dev.twitch.tv/console/apps |
| `STEAM_API_KEY` | Steam Web API | https://steamcommunity.com/dev/apikey |

Properties: `spotify.client-id|client-secret|api-base-url|token-url`, `igdb.client-id|client-secret|api-base-url|token-url|requests-per-second`, `steam.api-key|api-base-url`. Die Basis-URLs sind nur für Tests überschreibbar. Die Zugangsdaten gehören in die Shell/IDE-Run-Konfiguration oder den Secret-Store, **nicht** in `application*.properties`.

### Tests

- Spotify, IGDB und Steam stehen hinter Interfaces (`SpotifyClient`, `IgdbClient`, `SteamClient`). Die Integrationstests (`MusicAndGamesIntegrationTest`, Postgres + Kafka per Testcontainers) nutzen Fakes (`ExternalClientsTestConfiguration`) und prüfen: Limits (4 Songs, 1 Anthem, 6 Games), 400 bei unbekannten/doppelten/ungültigen IDs, 503 bei Ausfall, 422 bei privatem Steam-Profil, Ersetzen als Ganzes, dass Profile ohne externe Aufrufe angezeigt werden, `common` und das Publizieren von `user-profile-updated`.
- Die echten HTTP-Clients sind mit `MockRestServiceServer` getestet (kein WireMock nötig): Token holen/cachen/erneuern, 401-Wiederholung, 429/QUOTA_EXCEEDED mit Pause, Query-Aufbau und Escaping bei IGDB, Mapping von Cover/Jahr/Plattformen/Steam-ID, privates Steam-Profil, Steam-Key taucht nirgends auf. Dazu Tests für Token-Cache, Rate-Limiter und `CommonResponse`.
- `RecommendationIntegrationTest` (echtes Solr) prüft `anthem` und `commonCount` an den E2E-Usern, `E2eDataSeedIntegrationTest` den Reset von Songs und Games.

### Offene Punkte

- **IGDB, kommerzielle Nutzung klären:** Die IGDB-API ist laut Doku (Stand 11.10.2026) für nicht-kommerzielle Nutzung unter den Twitch Developer Service Agreement kostenlos. Für kommerzielle Nutzung gibt es eine Partnerschaft (partner@igdb.com), laut FAQ mit sichtbarer Quellenangabe "IGDB.com" im Produkt. Vor dem Echtbetrieb klären, ob wir als kommerziell gelten, und dann die Quellenangabe im Frontend ergänzen.
- **Spotify-Bedingungen (nicht geprüft):** Die Spotify Developer Terms und die Developer Policy wurden bei dieser Umsetzung **nicht** gelesen. Üblich sind Vorgaben zur Attribution (Link zurück zu Spotify) und zum Zwischenspeichern von Spotify-Inhalten. Die Kopien (Name, Artists, Album, Cover-URL) liegen dauerhaft in unserer DB, das ist vor dem Echtbetrieb gegen die Bedingungen zu prüfen. Cover-URLs (`i.scdn.co`) können sich ändern, ein Re-Sync gibt es nicht.
- **Spotify-Kontingent:** Im Development Mode gilt ein gemeinsames Kontingent für alle unsere User. Bei Erschöpfung ist die Suche bis zum `Retry-After` aus (503), vorhandene Profile bleiben unberührt. Eine Überwachung/Metrik fehlt noch.
- **Solr-Boost nicht umgesetzt (optional im Auftrag):** Artist-Namen und IGDB-IDs werden noch nicht indexiert und beim Score nicht geboostet. Dafür neue Solr-Felder (`artists`, `gameIds`, nur ergänzen), `SearchIndexService.toDocument` und `bq` in `RecommendationService` erweitern. Die Events dafür werden schon publiziert.
- ~~Rate-Limiting pro User fehlt~~ → umgesetzt (Phase 5, Sicherheit): `external-search` gilt für Spotify-, IGDB- und Steam-Endpunkte. Außerdem gemessen: Der `RequestRateLimiterTest` war rot, weil er die Zeit erst nach dem ersten AssertJ-Aufruf nahm. Das Laden der AssertJ-Klassen verzögerte einzelne Threads, der Test misst jetzt direkt nach `tryAcquire()`. Der Limiter wartet zusätzlich in einer Schleife bis zur Frist, weil `parkNanos` laut Javadoc früher zurückkehren darf. Das war für den Test nicht nötig, ist aber vertragskonform.
- **N+1:** `ProfileMapper` lädt Songs und Games pro Profil (je eine Query). In Listen (Matches, Likes, Chats) entstehen so zwei Queries je Eintrag. Bei Bedarf wie bei `CommonalityService.forCards` batchen (dort sind es vier Queries pro Seite).
- Dev-Seeder (`DevDataSeedRunner`) vergibt keine Songs/Games, nur die E2E-User haben welche.

### Offen – Frontend

- Profil bearbeiten: Suchfelder für Songs und Games (Debounce, ab 2 Zeichen) gegen `/api/music/search` und `/api/games/search`; Auswahl als Liste mit Reihenfolge (max. 4 Songs, 1 als Profilsong markierbar, max. 6 Games), Speichern per `PUT` mit der **ganzen** Liste. 503 abfangen und `detail` anzeigen.
- Steam-Import: Eingabe von URL/Name/ID, Vorschläge anzeigen, Mehrfachauswahl, danach `PUT /api/me/games` mit den bisherigen Games plus Auswahl (nur mit `igdbId`, insgesamt höchstens 6). 422 (privat) und 404 (unbekannt) mit `detail` anzeigen.
- Profilseite: Songs (Profilsong hervorheben, `spotifyUrl` als Link), Games (Cover), Abschnitt "Das habt ihr gemeinsam" aus `common`.
- Recommendation-Karte: `anthem` (Mini-Player oder Titel) und `commonCount` ("3 Gemeinsamkeiten").

## Phase 5 – Feinschliff ⏭ als Nächstes

Bestandsaufnahme vom Oktober 2026 (im Code geprüft). Erledigtes ist abgehakt.

**Schnelle Gewinne – erledigt**
- [x] JWT-Secret ohne Default: Ohne `JWT_SECRET` (mindestens 32 Bytes) startet die App nicht. `dev` und `test` bringen eigene Schlüssel mit (`application-dev.properties`, `application-test.properties`).
- [x] Actuator: Nur `/actuator/health` ist freigegeben (öffentlich, ohne Details; im Profil `dev` mit Details), inklusive Liveness/Readiness. Eigene Health-Indikatoren für Solr (Ping) und Kafka (Cluster erreichbar), dazu die DB von Spring Boot.
- [x] Images gepinnt: `postgres:18.6`, `apache/kafka:4.3.1` (die vorherigen `latest`-Stände, kompatibel zu bestehenden lokalen Daten). Die Testcontainers nutzen dieselben Versionen (`postgres:18.6-alpine`).
- [x] CI: `.github/workflows/ci.yml` führt bei Push auf `main` und bei Pull Requests `./gradlew test` aus. `gradlew` ist im Git als ausführbar markiert.
- [x] Aufgeräumt: `spring-boot-starter-data-jdbc` und die veraltete `hibernate.dialect`-Property entfernt. `POST /api/user/search` entfernt.

**Sicherheit für den Echtbetrieb – erledigt** (Migration `V10`, Paket `moderation`, `security`)
- [x] **Rollen:** `users.role` (`USER`/`ADMIN`), im JWT als Claim `roles` → `ROLE_ADMIN`. Der `scope` im Token wird vom Client gewählt und deshalb nie für Rechte verwendet. Erste Admins kommen über `app.security.admin-emails` (beim Start, das Konto muss existieren), weitere über `PUT /api/admin/users/{id}/role`. Eine Rollenänderung wirkt mit dem nächsten Login. Admins können ihren eigenen Status und ihre eigene Rolle nicht ändern.
- [x] **Sofortige Sperre:** `AccountStatusFilter` (nach der Token-Prüfung) und der STOMP-`CONNECT` lehnen gültige Tokens von `BLOCKED`- oder `MARKED_FOR_DELETION`-Konten mit 401 ab (`detail: "Account is blocked or deleted"`). Der Status ist 30 s gecacht und wird bei Änderungen auf dieser Instanz sofort verworfen. Andere Instanzen sehen ihn spätestens nach 30 s. Bestehende WebSocket-Sessions werden nicht aktiv getrennt, Senden scheitert aber, weil `ChatService` einen aktiven Absender verlangt.
- [x] **Melden:** `POST /api/me/reports {referenceId, reason, comment?, block?}`. Gründe `SPAM`, `FAKE_PROFILE`, `HARASSMENT`, `INAPPROPRIATE_CONTENT`, `UNDERAGE`, `SCAM`, `OTHER` (braucht `comment`). Pro Paar gibt es höchstens eine offene Meldung (409). `block: true` blockiert sofort mit. Der Meldende sieht das Ergebnis der Prüfung nicht.
- [x] **Moderation** (`/api/admin/**`, nur `ROLE_ADMIN`): Die Queue zeigt die älteste Meldung zuerst, mit öffentlichem Profil, E-Mail, Status und Anzahl offener Meldungen. `DISMISS` schließt nur diese Meldung. `BLOCK_USER` sperrt den User (Kafka-Event → aus dem Index) und schließt alle seine offenen Meldungen. Status setzen (`ACTIVE`/`INACTIVE`/`BLOCKED`, nicht `MARKED_FOR_DELETION`), Rollen vergeben, Re-Index auslösen. Jede Admin-Aktion wird mit Admin-ID geloggt.
- [x] **Kontolöschung (DSGVO):** `DELETE /api/me {password}` (oder `PATCH /api/me/status` → `MARKED_FOR_DELETION`) setzt `deletion_requested_at`. Ab da gibt es keinen Login mehr, das Profil ist unsichtbar, und der User fliegt aus dem Index. `AccountPurgeService` (stündlich, `app.account-deletion.purge-cron`) löscht nach `app.account-deletion.grace-period` (Standard 7 Tage) die Zeile. Die DB löscht per `on delete cascade` Präferenzen, Reaktionen, Matches mit Nachrichten, Blocks, Songs, Games und Meldungen gegen den User. Selbst erstellte Meldungen bleiben ohne Absender. Danach werden Profilbild und Solr-Dokument entfernt, die E-Mail ist wieder frei. Setzt ein Admin das Konto in der Frist auf `ACTIVE`, wird es nicht gelöscht.
- [x] **Rate-Limiting** (`RateLimiter`, feste Zeitfenster im Speicher je Instanz, 429 mit `Retry-After`): `login-ip` 30/5 min, `login-email` 10/5 min (zählt auch richtige Passwörter, bremst Angriffe von vielen IPs), `registration-ip` 10/h, `password-change` 10/h (auch für Konto löschen), `reactions` 120/min, `chat-messages` 60/min (REST und STOMP), `reports` 10/Tag, `external-search` 30/min (Spotify, IGDB, Steam). Konfigurierbar über `app.rate-limits.<name>=<anzahl>/<ISO-Dauer>`. In `dev`, `e2e` und `test` sind die IP-Grenzen hoch, weil dort alle Anfragen von einer Adresse kommen.
- [ ] Rate-Limits über mehrere Instanzen hinweg (gemeinsamer Speicher, z.B. Redis). Hinter einem Reverse-Proxy die echte Client-IP verwenden (`server.forward-headers-strategy` nur mit vertrauenswürdigem Proxy).
- [ ] Bestehende WebSocket-Sessions bei Sperre oder Löschung aktiv trennen.

**Offen – Frontend (Sicherheit)**
- „Melden“ am Profil, in der Karte und im Chat: Grund-Auswahl, optionaler Kommentar (Pflicht bei „Sonstiges“), Checkbox „Auch blockieren“ (vorausgewählt).
- „Konto löschen“ in den Einstellungen mit Passwortabfrage und Hinweis auf die Frist, danach ausloggen.
- 429 mit `Retry-After` freundlich anzeigen („Zu viele Versuche, bitte in N Sekunden erneut“). 401 mit `detail: "Account is blocked or deleted"` → ausloggen.
- Admin-Bereich (nur bei `roles` enthält `ADMIN` im JWT): Meldungs-Queue mit Profilansicht, Buttons „Abweisen“ und „Sperren“ mit Notiz, User entsperren. E2E-Admin: `e2e-admin@test.local`.

**Features – erledigt** (Migrationen `V11`–`V13`, Pakete `notification`, `presence`, `security`)
- [x] **OpenAPI/Swagger** (springdoc 3.1.1): Im Profil `dev` gibt es `http://localhost:8080/swagger-ui.html` und `/v3/api-docs`. Sonst sind beide aus (`springdoc.*.enabled=false`), damit die API-Struktur nicht öffentlich ist. Anmelden in der Swagger-UI über „Authorize“ mit dem `access_token`. Der WebSocket/STOMP-Teil steht nicht im Dokument (siehe Phase 4).
- [x] **Token-Refresh:** Login liefert zusätzlich `refresh_token` und `refresh_expires_in` (Standard 30 Tage, `app.security.refresh-token-ttl`).
  - `POST /api/auth/refresh {refresh_token}` gibt ein neues Paar zurück. Das alte Refresh-Token wird ungültig (Rotation), und eine geänderte Rolle wirkt ab hier.
  - Wird ein bereits rotiertes Token erneut benutzt, wurde es vermutlich kopiert. Dann wird die ganze Familie dieses Logins widerrufen, andere Logins bleiben unberührt.
  - `POST /api/auth/logout {refresh_token}` widerruft die Familie. Passwortänderung, Sperre und Löschanfrage widerrufen alle Refresh-Tokens des Kontos.
  - In der DB steht nur der SHA-256-Hash. Abgelaufene Tokens löscht ein nächtlicher Job.
  - Das Access-Token bleibt 1 h gültig. Sobald das Frontend refresht, sollte `app.security.jwt.token-ttl` auf etwa 15 min sinken.
- [x] **Online-Status:** Jede authentifizierte Anfrage und jede offene WebSocket-Verbindung (minütlich aufgefrischt) setzt `users.last_active_at`, höchstens einmal pro Minute. Online heißt aktiv in den letzten 2 Minuten (`app.presence.*`). Weil der Wert in der DB steht, funktioniert das über mehrere Instanzen.
  - `online` steht im öffentlichen Profil (damit in Matches, Chats, Likes, Blocks und Benachrichtigungen) und in den Vorschlagskarten: `true`/`false`, oder `null`, wenn der User den Status verbirgt.
  - `PUT /api/me/privacy {showOnlineStatus}`, die Einstellung steht als `showOnlineStatus` in `GET /api/me`.
  - Ein genaues „zuletzt gesehen“ wird bewusst nicht herausgegeben, weil es Aktivitätsmuster verraten würde.
- [x] **Benachrichtigungen:** `NEW_LIKE` (positive Reaktion mit `reactionType`, nicht wenn sie direkt einen Match erzeugt) und `NEW_MATCH` (mit `matchReferenceId`). Neue Chat-Nachrichten erzeugen keine eigene Benachrichtigung, das decken die Ungelesen-Zähler ab.
  - **Ablauf:** `NotificationConsumer` schreibt aus `reaction-created` und `match-created` (eine Gruppe `notification-writer`, also genau einmal). Doppelt zugestellte Events erzeugen nichts Neues (`source_key` eindeutig pro Empfänger). Nach dem Commit geht `notification-created` raus, jede Instanz pusht an `/user/queue/notifications`.
  - **Sichtbarkeit:** Nichts über inaktive oder geblockte Personen. Wird jemand später geblockt oder inaktiv, verschwinden seine Benachrichtigungen aus Liste und Zähler.
  - **Endpunkte:**
    - `GET /api/me/notifications?before=&size=`: neueste zuerst, `hasMore`, ältere mit `before = referenceId` des letzten Eintrags.
    - `GET /api/me/notifications/unread-count` → `{notifications, messages}`, `messages` sind die ungelesenen Chat-Nachrichten.
    - `POST /api/me/notifications/read` (alle) und `POST /api/me/notifications/{referenceId}/read` (eine).
  - Gelesene Benachrichtigungen werden nach 90 Tagen gelöscht (`app.notifications.*`).

**Offen – Frontend (Features)**
- Login-Antwort: `refresh_token` speichern. Vor Ablauf von `expires_in` oder bei einem 401 einmal `POST /api/auth/refresh` aufrufen und die Anfrage wiederholen. Bei 401 auf den Refresh ausloggen. Beim Logout `POST /api/auth/logout`. Nach jedem Refresh die STOMP-Verbindung mit dem neuen Token neu aufbauen.
- `online` in `ProfileTile`, `ChatMenu` und im Chat-Header anzeigen (ersetzt `isOnline`). `null` heißt „nicht sichtbar“, also keinen Punkt anzeigen.
- Einstellung „Online-Status anzeigen“ → `PUT /api/me/privacy`.
- Glocke mit Badge aus `unread-count` (Benachrichtigungen, separat Chat-Nachrichten). Liste mit „Alle gelesen“. Neue Einträge live über `/user/queue/notifications`. Ein Klick auf `NEW_MATCH` öffnet den Chat (`matchReferenceId`), ein Klick auf `NEW_LIKE` das Profil oder „Wer mag mich“.
- E2E: alice hat nach jedem Reset genau eine ungelesene `NEW_LIKE`-Benachrichtigung von carol.

**Technische Schulden**
- [ ] Transactional Outbox, damit Events nicht verloren gehen, wenn Kafka beim Commit nicht erreichbar ist.
- [x] Re-Index außerhalb von `dev`: `POST /api/admin/search/reindex`.
- [ ] N+1 beim Laden der Präferenzen (`@ElementCollection(fetch = EAGER)`) in Listen.
- [ ] MinIO-Java-SDK (Projekt eingestellt) durch AWS SDK v2 ersetzen.
- [ ] End-to-End-Test im Browser mit komplettem Stack.

---

## Konventionen & Hinweise für Agents

- Responses immer als DTO-Record, nie Entities. User-Profile über `ProfileMapper` (signiert Foto-URLs), einfache DTOs mit statischem `from(...)`. Requests als validierte Records.
- Der eingeloggte User kommt immer aus `@CurrentUser`, nie aus Pfad oder Body.
- Fehler über die Exceptions in `exception/` werfen, nicht `IllegalArgumentException` → 500.
- Schemaänderungen nur per neuer Flyway-Migration (`V3__…`), bestehende Migrationen nie ändern.
- Spring Boot 4 ist modular: Autokonfiguration braucht den passenden Starter (`spring-boot-starter-kafka`, `-flyway`, Tests: `spring-boot-starter-webmvc-test`). Jackson 3 (`tools.jackson`), Annotationen weiter `com.fasterxml.jackson.annotation`.
- Testcontainers 2.x: `org.testcontainers.postgresql.PostgreSQLContainer`, `org.testcontainers.kafka.KafkaContainer`.
- Einmalig nach dem Klonen: `bash scripts/prepare-postal-codes.sh` (PLZ-Daten für das Geocoding, gitignored).
- Tests: `./gradlew test` (Integrationstests brauchen laufendes Docker Desktop, in der CI automatisch). Lokal starten: `./gradlew bootRun --args='--spring.profiles.active=dev'` (startet `compose.yaml` automatisch). **Ohne Profil `dev` braucht die App `JWT_SECRET`.** Für Frontend-E2E: `dev,e2e` (siehe „E2E-Testdaten“).
- Auf dem Entwicklerrechner (Windows) ist kein Python installiert. Für Skripte Bash oder PowerShell nutzen.
- Objektspeicher: **SeaweedFS** (`chrislusf/seaweedfs:4.48`, S3-API auf Port 8333) statt MinIO. MinIO veröffentlicht keine Community-Images mehr, `minio/minio` wurde im September 2026 von Docker Hub entfernt. Die Zugangsdaten (`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` in `compose.yaml`) aktivieren die Authentifizierung, ohne sie akzeptiert SeaweedFS beliebige Schlüssel. Konfiguration unter `storage.*` (`endpoint`, `access-key`, `secret-key`, `bucket-name`, `url-expiry`, `region`). Der Code nutzt das MinIO-Java-SDK nur als allgemeinen S3-Client. Ein späterer Wechsel auf das AWS SDK v2 ist möglich, wenn das SDK nicht mehr gepflegt wird. Test: `ProfilePictureStorageIntegrationTest` läuft gegen denselben Container wie Compose.
- Git Bash wandelt Pfade in Argumenten um (`-dir=/data` → `C:/Program Files/Git/data`). Bei manuellen `docker run`-Befehlen `MSYS_NO_PATHCONV=1` setzen.
- Kafka-Fehlerbehandlung (`KafkaConfiguration`): Alle Listener werden mit Backoff wiederholt, danach geht das Event nach `<topic>.dlt`. Ein neuer Listener braucht dafür nichts zu tun. Ein neues Topic in `KafkaConfiguration.TOPICS` eintragen, damit sein DLT-Topic angelegt wird.
- Solr-Client immer mit `useHttp1_1(true)` bauen (siehe Bugfix in Phase 2).
- Externe APIs (Spotify, IGDB, Steam): immer hinter einem Interface, Zugangsdaten nur per Env/Properties, HTTP über `ExternalHttp.builder()` (kurze Timeouts), Ausfälle als `ExternalServiceUnavailableException` (→ 503), nie Exception-Meldungen mit URLs loggen (Steam-Key). Tests nutzen Fakes (`ExternalClientsTestConfiguration`) bzw. `MockRestServiceServer`, nie die echten Dienste. Externe Daten werden beim Auswählen kopiert, Profile lesen nur die DB.
- Neue Tabellen, die an User hängen, müssen im E2E-Seeder zurückgesetzt werden (`user_songs` und `user_games` sind dort schon enthalten).
- Neue Endpunkte unter `/api/me/**` brauchen nichts extra: Sie sind authentifiziert, und gesperrte oder gelöschte Konten sind durch den `AccountStatusFilter` ausgeschlossen. Admin-Endpunkte gehören unter `/api/admin/**`. Für teure oder missbrauchbare Aktionen eine Grenze in `RateLimiter` ergänzen (Konstante plus `app.rate-limits.<name>` in `application.properties`, in `application-test.properties` hoch setzen).
- Jackson 3 lehnt fehlende Werte für primitive Felder ab (`boolean`, `int` → 400). Optionale Request-Felder daher als Wrapper-Typ (`Boolean`, `Integer`) deklarieren.
- Neue Benachrichtigungstypen: Konstante in `NotificationType`, eine Fabrikmethode in `Notification` mit eindeutigem `sourceKey` und ein Aufruf in `NotificationService` (meist aus einem Kafka-Event in `NotificationConsumer`).
- Profil-DTOs werden nur in `ProfileMapper` gebaut. Neue Felder dort ergänzen. `online` kommt aus `PresenceService`.
