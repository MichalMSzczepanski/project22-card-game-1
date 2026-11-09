# Karciana Gra

Przeglądarkowa gra towarzyska (styl Cards Against Humanity / Apples to Apples) do grania w tej samej sieci Wi-Fi z telefonów. Spring Boot + Thymeleaf, bez JS po stronie gry — cała logika i stan żyją na backendzie.

## 1. Jak działa gra (przepływ)

1. **Tworzenie gry** — `POST /games`. Osoba tworząca podaje nick i wybiera pakiety kart; dostaje losowy 5-znakowy kod gry (`GameCodeGenerator`) i automatycznie dołącza jako pierwszy gracz.
2. **Lobby** — inni dołączają kodem (`POST /games/join`). Min. 3, max. 5 graczy (`Game.MIN_PLAYERS_TO_START` / `Game.MAX_PLAYERS`).
3. **Start** — **każdy** gracz w lobby może kliknąć "Start gry" (nie tylko założyciel) — `GameController.start` nie sprawdza, kto klika, tylko czy jest wystarczająco graczy.
4. **20 rund** (`Game.TOTAL_ROUNDS`), każda ma 3 fazy:
   - `ANSWERING` — prowadzący dostaje czarną kartę-pytanie (`CardType.QUESTION`); wszyscy pozostali gracze wybierają po jednej karcie-odpowiedzi (`CardType.ANSWER`) z ręki (7 kart, `Game.HAND_SIZE`). Limit czasu: `Game.ANSWER_TIMEOUT_SECONDS` (60s) — kto nie zdąży, dostaje losową kartę z ręki.
   - `RANKING` — prowadzący układa zebrane (potasowane, anonimowe) odpowiedzi od najlepszej do najsłabszej, przyznając pozycje 1..N (N = liczba odpowiadających). Limit czasu: `Game.RANKING_TIMEOUT_SECONDS` (45s) — po nim runda odkrywa się z tym, co prowadzący zdążył poukładać.
   - `REVEALED` — wyniki rundy: kto napisał co i ile punktów to dało. Dowolny gracz klika "Następna runda".
5. **Prowadzący rotuje** — `Game.nextLeader()` idzie po kolei po `joinOrder` (kto pierwszy dołączył, ten pierwszy prowadzi), pętla z zawijaniem, przeskakując graczy którzy wyszli.
6. **Koniec gry** — po 20 rundach (`GameStatus.FINISHED`) albo wcześniej, jeśli ktoś kliknie "Zakończ grę".

### Punktacja (ranking, nie zgadywanie autorstwa)

Przy N odpowiadających: pozycja #1 (najlepsza) = N punktów, pozycja #2 = N-1, …, pozycja #N (najsłabsza) = 1 punkt. Dostaje je **autor karty**, nie prowadzący. Prowadzący nie zdobywa punktów w danej rundzie (`GameService.scoreRound`).

### Tryb rodzinny i pakiety kart

- "Tryb rodzinny" (`setFamilyMode`) na żywo odfiltrowuje karty `PackAudience.ADULT` z rąk graczy i z dobierania.
- Pakiety (`CardPack`): darmowe ("Generyczny", "Dla dzieci") i płatne (Pikantna, Biurowa, Pop-kultura, Sylwestrowa — sezonowa, aktywna tylko 15.12–15.01).

### Wychodzenie z gry / wykrywanie rozłączenia

- Przycisk "Opuść grę" (`POST /games/{id}/leave`) — natychmiastowe wyjście.
- **Bez JS**: każde zwykłe odpytanie `/games/{id}` co 3s (meta-refresh) odświeża "ostatnio widziano" (`Player.lastSeenAt`) tego gracza. Przy każdym żądaniu serwer sprawdza też (`pruneDisconnectedPlayers`), czy ktoś INNY nie ucichł dłużej niż `Game.DISCONNECT_TIMEOUT_SECONDS` (15s) — jeśli tak, traktuje go jak zamkniętą kartę i usuwa. Gracz, na którego akurat czeka runda (jeszcze nie odpowiedział / jeszcze ranguje), jest z tego wyłączony — jego ekran z założenia nie odświeża się, więc cisza nie znaczy "wyszedł".
- W lobby wyjście usuwa gracza od razu; w trakcie gry zostaje tylko oznaczony `left=true` (stare `Submission`/`Ranking` wciąż na niego wskazują) i wyfiltrowany z `Game.activePlayers()` (scoreboard, kolejka prowadzącego, dobieranie kart).
- Gdy **ostatni aktywny** gracz wyjdzie — cała gra jest usuwana z bazy. Pozostałym graczom (i ekranowi TV) pokazuje się baner "X opuścił(a) grę" na `Game.LEAVE_BANNER_SECONDS` (10s).
- **Limit tego podejścia**: to czysto reaktywne na żądania — jeśli *nikt* już nigdy nie odwiedzi URL-a gry (zamknięte karty, nikt nie otworzył ekranu TV), wpis w bazie fizycznie zostaje (nic go nie posprząta w tle — nie ma schedulera/`@Scheduled`).

### Ekran TV

`GET /games/{id}/tv` — osobny widok bez akcji, do wyświetlenia na wspólnym ekranie. Też odpytywany co 3s, też wywołuje `checkTimeout`/`pruneDisconnectedPlayers`.

## 2. Co i gdzie jest zapisywane

Baza: **H2, plikowa** (`./data/carddb.mv.db`), nie w pamięci — `spring.jpa.hibernate.ddl-auto=update`. Dane przeżywają restart aplikacji. Brak Flyway/Liquibase — schemat trzyma się tak, jak Hibernate go dociągnie przy starcie (patrz gotcha w sekcji błędów).

| Co | Encja | Uwagi |
|---|---|---|
| Stan gry, lista graczy, aktywne pakiety, użyte karty | `Game` | jeden `@OneToOne` do aktualnej `Round`, kaskada usuwa wszystko poniżej gdy gra się kończy/usuwa |
| Gracz | `Player` | `points`, `hand` (karty na ręce), `left`/`lastSeenAt` (patrz wyżej), opcjonalny `appUserId` jeśli zalogowany |
| Runda | `Round` | pytanie (`questionCard`), prowadzący, lista `Submission` i `Ranking` |
| Odpowiedź gracza | `Submission` | karta + autor, anonimizowana (potasowana) na czas `RANKING` |
| Pozycja przyznana przez prowadzącego | `Ranking` | runda + submission + rank (1..N) |
| Katalog kart | `Card` / `CardPack` | seedowane raz przy starcie przez `CardSeeder` (`ApplicationRunner`), **hardkodowane w Javie**, nie w CSV/SQL |
| Konto / logowanie | `AppUser` | hash bcrypt, opcjonalny provider OAuth2 (Google itp.) |
| Historia wyników (zalogowani) | `GameScoreRecord` | zapisywane tylko dla graczy z `appUserId`, przy końcu gry |
| Zakupione pakiety | `Purchase` | |
| Sesja HTTP (`playerId` w `HttpSession`) | tabele Spring Session (`SPRING_SESSION*`) | `@EnableJdbcHttpSession`, TTL 8h (`spring.session.timeout`), schemat z `session-schema-h2.sql` (idempotentny, żeby restart nie wywalał się na "table already exists") |

Nic o stanie gry nie jest trzymane tylko w pamięci/sesji — `playerId`/`gameId` w sesji to tylko "kto to jest", cały stan gry (`Game`/`Round`/...) żyje w bazie i jest ładowany na nowo przy każdym żądaniu.

## 3. Jak łapane są błędy

- **Złe akcje w grze** (np. zgadywanie nie w swojej turze, zła faza) → `InvalidGameActionException`, łapane w `GameController.withErrorHandling`, pokazywane jako `error` flash na tej samej stronie.
- **Równoczesna edycja** (dwóch graczy klika w tej samej chwili) → `ObjectOptimisticLockingFailureException` (pole `@Version` na `Game`) → komunikat "Ktoś inny właśnie coś zmienił w grze — spróbuj jeszcze raz."
- **Gra zniknęła** (usunięta, bo wszyscy wyszli) → `GameNotFoundException`, łapane tylko w `GET /games/{id}` i `/tv` → przekierowanie na `/` z komunikatem.
- **Zablokowana runda** (ktoś nie odpowiedział/nie porankował w czasie) → samoleczenie bez schedulera: `GameService.checkTimeout` jest wywoływane przy **każdym** wejściu na stronę gry (gracza albo TV) i porównuje `Round.phaseStartedAt` z limitem fazy.
- **Rozłączony gracz** → analogicznie, `pruneDisconnectedPlayers` przy każdym wejściu (patrz sekcja 1).
- Logi: `GameService` loguje każdą istotną zmianę stanu (`log.info`/`log.debug`) po polsku, z kodem gry — to pierwsze miejsce do grep'owania przy diagnozie.

### Znana pułapka (i jak jej unikać) — zmiana enuma przy `ddl-auto=update`

H2 mapuje `@Enumerated(EnumType.STRING)` na natywny typ `ENUM('STARA_WARTOSC', ...)` **zapisany w definicji kolumny**, nie na zwykły `VARCHAR`. `ddl-auto=update` nigdy nie poszerza/zmienia tego ograniczenia na istniejącej kolumnie — tylko dodaje nowe kolumny/tabele. Efekt: zmiana nazwy wartości enuma w Javie (np. `CardType.BLACK/WHITE` → `QUESTION/ANSWER`, albo wcześniej `GameStatus.GUESSING` → `RANKING`) **nie jest błędem kompilacji, ale wywala 500 na żywej bazie**, dopóki ktoś ręcznie nie zmigruje danych:

```sql
ALTER TABLE card ALTER COLUMN type ENUM('BLACK','WHITE','QUESTION','ANSWER'); -- poszerz
UPDATE card SET type='QUESTION' WHERE type='BLACK';
UPDATE card SET type='ANSWER' WHERE type='WHITE';
ALTER TABLE card ALTER COLUMN type ENUM('QUESTION','ANSWER');                -- zwęż do finalnej
```

To samo dotyczy każdej zmiany nazwy kolumny/tabeli powiązanej z takim enumem — stare nazwy (np. `black_card_id`, `game_used_black_cards`) zostają w bazie jako osierocone, nieużywane artefakty po `ddl-auto=update`; to nieszkodliwe, ale warte wiedzy przy czytaniu schematu. Przy przejściu na Postgres (patrz komentarz w `application.properties`) ten cały problem znika razem z wprowadzeniem Flyway/Liquibase.

**To dotyczy każdego enuma mapowanego `@Enumerated(STRING)`, nie tylko `card.type`** — przy zmianie `GameStatus.GUESSING` → `RANKING` ten sam problem wystąpił osobno na kolumnie `game.status` (`ENUM('ANSWERING','FINISHED','GUESSING','LOBBY','REVEALED')` — stare ograniczenie nie zawierało `RANKING`). Przy każdej zmianie nazwy wartości enuma: sprawdź WSZYSTKIE kolumny tego typu w bazie, nie tylko tę, przez którą akurat przeszedł request —
```sql
-- znajdź wszystkie kolumny ENUM i ich aktualne dozwolone wartości:
SCRIPT NOPASSWORDS NOSETTINGS;   -- albo: SELECT * FROM INFORMATION_SCHEMA.COLUMNS gdzie DATA_TYPE='ENUM'
```
a potem dla każdej: poszerz → zaktualizuj dane → zwęż, tak jak wyżej.
