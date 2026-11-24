# Karciana Gra

Przeglądarkowa gra towarzyska typu pytanie–odpowiedź z rotującym prowadzącym do grania w tej samej sieci Wi-Fi z telefonów. Spring Boot + Thymeleaf, bez JS po stronie gry — cała logika i stan żyją na backendzie.

Standardy kodu: [`CODING.md`](CODING.md) · Prawo, licencje, etapy: [`FORMAL.md`](FORMAL.md) · Mechanika gry: [`MECHANICS.md`](MECHANICS.md) · Zarabianie: [`BIZNES.md`](BIZNES.md) · Roadmapa: [`ROADMAP.md`](ROADMAP.md) · Instrukcje dla Claude: [`CLAUDE.md`](CLAUDE.md)

## 1. Jak działa gra (przepływ)

1. **Tworzenie gry** — `POST /games`. Osoba tworząca podaje nick, wybiera pakiety kart i **liczbę rund** (1..`game.max-total-rounds`, domyślnie max 20 — formularz podsuwa 10); dostaje losowy 5-znakowy kod gry (`GameCodeGenerator`), automatycznie dołącza jako pierwszy gracz i tym samym staje się **adminem** (`Game.adminPlayerId`, `Game.addPlayer`, `GameService.createGameAndJoin`) — pełna specyfikacja roli admina: sekcja 5.
2. **Lobby** — inni dołączają kodem (`POST /games/join`). Min. 3, max. 5 graczy (`game.players.min` / `game.players.max` w `application.yaml`, bindowane przez `GameProperties`).
3. **Start** — **tylko admin** może kliknąć "Start gry" — `GameService.startGame(gameId, playerId)` odrzuca innych z komunikatem "Tylko admin może to zrobić." Gdy admin ucieknie (wyjdzie/rozłączy się), rola **nie** przechodzi automatycznie — gra zostaje bez admina, dopóki ktoś nie kliknie „Przejmij admina" (`GameService.takeOverAdmin`).
4. **Tyle rund, ile wybrał admin przy tworzeniu gry** (`Game.totalRounds`, nie globalny `game.max-total-rounds` — to tylko sufit), każda ma 3 fazy:
   - `ANSWERING` — prowadzący dostaje czarną kartę-pytanie (`CardType.QUESTION`); wszyscy pozostali gracze wybierają po jednej karcie-odpowiedzi (`CardType.ANSWER`) z ręki (7 kart, `game.hand-size`). Limit czasu: `game.timeouts.answer-seconds` (60s) — kto nie zdąży, dostaje losową kartę z ręki.
   - `RANKING` — prowadzący wybiera **podium** z zebranych (potasowanych, anonimowych) odpowiedzi: miejsca wg `game.scoring.podium-size` (domyślnie 3, mniej jeśli jest mniej odpowiedzi) — `Round.podiumSize()`. Limit czasu: `game.timeouts.ranking-seconds` (45s) — po nim runda odkrywa się z tym, co prowadzący zdążył przyznać; nieprzyznane miejsca i odpowiedzi poza podium dostają 0 pkt.
   - `REVEALED` — wyniki rundy: kto napisał co i ile punktów to dało. Pierwsze `game.timeouts.reveal-seconds` (30s) kliknąć "Następna runda" może tylko prowadzący tej rundy (`round.getLeader()`); po tym czasie — każdy (`GameService.nextRound(gameId, playerId)`, `GameViewModel.canAdvanceRound()`).
   - **Ręka gracza trwa przez całą grę.** Na starcie każdy dostaje 7 kart-odpowiedzi i trzyma je z rundy na rundę. W każdej rundzie „zrzuca” dokładnie jedną (tę, którą zagrał, albo losową przy timeoucie), a przed następną rundą dobiera jedną nową z talii (`GameService.dealUpToHandSize`) — ręka zawsze wraca do 7. Prowadzący w swojej rundzie nie zrzuca nic, jego ręka czeka nietknięta. Zagrane karty nie wracają do talii w tej grze (`Game.usedAnswerCardIds`).
5. **Prowadzący rotuje** — `Game.nextLeader()` idzie po kolei po `joinOrder` (kto pierwszy dołączył, ten pierwszy prowadzi), pętla z zawijaniem, przeskakując graczy którzy wyszli.
6. **Koniec gry** — po `Game.totalRounds` rundach (`GameStatus.FINISHED`) albo wcześniej, jeśli ktoś kliknie "Zakończ grę".

### Punktacja (podium, nie zgadywanie autorstwa)

Prowadzący przyznaje tylko miejsca podium — domyślnie 3: 1. = 3 pkt, 2. = 2 pkt, 3. = 1 pkt, reszta 0 pkt (`game.scoring.podium-size`/`podium-points` w `application.yaml`, `Round.pointsForRank`). Jeśli jest mniej odpowiedzi niż rozmiar podium, podium ma tyle miejsc, ile jest odpowiedzi (`Round.podiumSize()`). Punkty dostaje **autor karty**, nie prowadzący; prowadzący nie zdobywa punktów w danej rundzie. Runda przechodzi do `REVEALED` automatycznie w momencie obsadzenia ostatniego miejsca podium (`GameService.scoreRound`/`submitRanking`) — nieobsadzone odpowiedzi poza podium zostają z 0 pkt. Szczegóły reguły: [`MECHANICS.md`](MECHANICS.md) → Punktacja.

### Tryb rodzinny i pakiety kart

- "Tryb rodzinny" (`setFamilyMode`) na żywo odfiltrowuje karty `PackAudience.ADULT` z rąk graczy i z dobierania.
- Pakiety (`CardPack`): darmowe ("Generyczny", "Dla dzieci") i płatne (Pikantna, Biurowa, Pop-kultura, Sylwestrowa — sezonowa, aktywna tylko 15.12–15.01).

### Wychodzenie z gry / wykrywanie rozłączenia

- Przycisk "Opuść grę" (`POST /games/{id}/leave`) — natychmiastowe wyjście.
- **Bez JS**: każde zwykłe odpytanie `/games/{id}` co 3s (meta-refresh, `game.poll-interval-seconds`) odświeża "ostatnio widziano" (`Player.lastSeenAt`) tego gracza. Przy każdym żądaniu serwer sprawdza też (`pruneDisconnectedPlayers`), czy ktoś INNY nie ucichł dłużej niż `game.timeouts.disconnect-seconds` (60s — świadomie wysoko, żeby zgaszony/zablokowany ekran telefonu nie wyrzucał z gry) — jeśli tak, traktuje go jak zamkniętą kartę i usuwa. Gracz, na którego akurat czeka runda (jeszcze nie odpowiedział / jeszcze ranguje), jest z tego wyłączony — jego ekran z założenia nie odświeża się, więc cisza nie znaczy "wyszedł".
- W lobby wyjście usuwa gracza od razu; w trakcie gry zostaje tylko oznaczony `left=true` (stare `Submission`/`Ranking` wciąż na niego wskazują) i wyfiltrowany z `Game.activePlayers()` (scoreboard, kolejka prowadzącego, dobieranie kart).
- Gdy **ostatni aktywny** gracz wyjdzie — cała gra jest usuwana z bazy. Pozostałym graczom (i ekranowi TV) pokazuje się baner "X opuścił(a) grę" na `game.timeouts.leave-banner-seconds` (10s).
- **Limit tego podejścia**: to czysto reaktywne na żądania — jeśli *nikt* już nigdy nie odwiedzi URL-a gry (zamknięte karty, nikt nie otworzył ekranu TV), wpis w bazie fizycznie zostaje (nic go nie posprząta w tle — nie ma schedulera/`@Scheduled`).

### Ekran TV

`GET /games/{id}/tv` — osobny widok bez akcji, do wyświetlenia na wspólnym ekranie. Też odpytywany co `game.poll-interval-seconds` (3s), też wywołuje `checkTimeout`/`pruneDisconnectedPlayers`.

## 2. Co i gdzie jest zapisywane

Baza: **H2, plikowa** (`./data/carddb.mv.db`), nie w pamięci. **Tymczasowo** `spring.jpa.hibernate.ddl-auto=create-drop` — baza czyści się przy **każdym starcie aplikacji** (dane NIE przeżywają restartu), na czas częstych zmian schematu; patrz TODO → docker-compose + Postgres, żeby to wróciło. Brak Flyway/Liquibase — schemat trzyma się tak, jak Hibernate go wygeneruje przy starcie (patrz gotcha w sekcji błędów, dziś nieaktywna przy `create-drop`, wróci przy `update`/`validate`).

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

> Dziś nieaktywna: lokalnie działamy na `ddl-auto=create-drop` (baza czyści się przy starcie), więc to się nie zdarzy. Wraca, gdy ktoś przełączy na `update` (albo na Postgres+Flyway w `validate`, inną drogą — przez migracje).

H2 mapuje `@Enumerated(EnumType.STRING)` na natywny typ `ENUM('STARA_WARTOSC', ...)` **zapisany w definicji kolumny**, nie na zwykły `VARCHAR`. `ddl-auto=update` nigdy nie poszerza/zmienia tego ograniczenia na istniejącej kolumnie — tylko dodaje nowe kolumny/tabele. Efekt: zmiana nazwy wartości enuma w Javie (np. `CardType.BLACK/WHITE` → `QUESTION/ANSWER`, albo wcześniej `GameStatus.GUESSING` → `RANKING`) **nie jest błędem kompilacji, ale wywala 500 na żywej bazie**, dopóki ktoś ręcznie nie zmigruje danych:

```sql
ALTER TABLE card ALTER COLUMN type ENUM('BLACK','WHITE','QUESTION','ANSWER'); -- poszerz
UPDATE card SET type='QUESTION' WHERE type='BLACK';
UPDATE card SET type='ANSWER' WHERE type='WHITE';
ALTER TABLE card ALTER COLUMN type ENUM('QUESTION','ANSWER');                -- zwęż do finalnej
```

To samo dotyczy każdej zmiany nazwy kolumny/tabeli powiązanej z takim enumem — stare nazwy (np. `black_card_id`, `game_used_black_cards`) zostają w bazie jako osierocone, nieużywane artefakty po `ddl-auto=update`; to nieszkodliwe, ale warte wiedzy przy czytaniu schematu. Przy przejściu na Postgres (patrz komentarz w `application.yaml`) ten cały problem znika razem z wprowadzeniem Flyway/Liquibase.

**To dotyczy każdego enuma mapowanego `@Enumerated(STRING)`, nie tylko `card.type`** — przy zmianie `GameStatus.GUESSING` → `RANKING` ten sam problem wystąpił osobno na kolumnie `game.status` (`ENUM('ANSWERING','FINISHED','GUESSING','LOBBY','REVEALED')` — stare ograniczenie nie zawierało `RANKING`). Przy każdej zmianie nazwy wartości enuma: sprawdź WSZYSTKIE kolumny tego typu w bazie, nie tylko tę, przez którą akurat przeszedł request —
```sql
-- znajdź wszystkie kolumny ENUM i ich aktualne dozwolone wartości:
SCRIPT NOPASSWORDS NOSETTINGS;   -- albo: SELECT * FROM INFORMATION_SCHEMA.COLUMNS gdzie DATA_TYPE='ENUM'
```
a potem dla każdej: poszerz → zaktualizuj dane → zwęż, tak jak wyżej.

## 4. TODO

### Etap 1 — darmowe MVP (patrz `FORMAL.md`)
- [x] Wyłączyć zakupy (`PacksController.purchase`); płatne pakiety jako „Tematyczne zestawy — wkrótce” (bez dat i cen).
- [ ] Przycisk „Chcę ten zestaw!” przy każdym zestawie + licznik kliknięć (bez danych osobowych, ograniczenie wielokrotnego klikania np. per sesja).
- [x] Wyłączyć rejestrację / logowanie / OAuth i historię wyników — same nicki. `AuthController`/`StatsController` usunięte, `/register`/`/login`/`/stats`/`/oauth2/**` i `POST /packs/{id}/purchase` zwracają 404 (`AccountsDisabledE2ETest`). Encje/serwisy kont (`user`/`security`) zostają w repo nieużywane, odłączone od `SecurityConfig` — gotowe na etap 2 (ROADMAP.md), nie usunięte.
- [x] Strona „O grze” (`/o-grze`, `AboutController`): darmowa, nie zbiera danych osobowych, treści 18+ poza trybem rodzinnym. Link z `/`.
- [ ] Wszystkie karty własne — przejrzeć `CardSeeder` pod kątem tekstów z innych gier.
- [ ] Własna kolorystyka i font kart (nie czarno-białe w stylu CAH).

### Rozgrywka
- [x] **Punktacja podium 3-2-1**: prowadzący wybiera tylko 3 najlepsze odpowiedzi (zamiast układać wszystkie); mniej odpowiedzi → mniej miejsc; timeout → punkty tylko za przyznane miejsca (`GameService.scoreRound`, `submitRanking`, `Round.podiumSize`/`pointsForRank`). Spec: `MECHANICS.md`.
- [x] **Rola admina** — tylko admin zarządza grą i graczami; gdy admin ucieknie, dowolny aktywny gracz może go przejąć przyciskiem „Przejmij admina”. Specyfikacja: sekcja 5. `Game.adminPlayerId` zastąpił `Game.isHost()` (usunięty) — founder = admin (`Game.addPlayer`), bez auto-przejęcia po joinOrder.
  - [x] Założyciel gry automatycznie zostaje adminem (`Game.addPlayer`); tylko admin kliknie „Start gry”.
  - [x] Pozostałe akcje admina (następna runda poza oknem priorytetu prowadzącego, tryb rodzinny, zakończ) zablokowane dla reszty (`GameService.requireAdmin`); przyciski skryte w `game.html` (`view.isAdmin()`).
  - [x] Nowa akcja „Wyrzuć gracza” (`GameService.kickPlayer`, `POST /games/{id}/kick`) — wyrzucony może wrócić kodem (nie pamiętamy wyrzuconych, jak zapisano w Decyzjach niżej).
  - [x] Gra bez admina + przycisk „Przejmij admina” (`GameService.takeOverAdmin`, `POST /games/{id}/take-over-admin`, kto pierwszy, ten admin) — działa tak samo w lobby i w trakcie gry.
  - [x] TV pokazuje aktualnego admina / „Brak admina” (`tv.html`).
  - [x] Dobrowolne przekazanie roli admina konkretnej osobie bez wychodzenia z gry (`GameService.transferAdmin`, `POST /games/{id}/transfer-admin`) — wcześniej poza zakresem MVP, teraz zrobione.
- [x] **Do 10 graczy** — `game.players.max` w `application.yaml` (nie osobny `Game.MAX_PLAYERS`, konfiguracja już to obsługiwała). Sprawdzone: darmowe pakiety (Generyczny + Dla dzieci) mają tylko 45 kart-odpowiedzi, czyli przy 10 graczach (70 kart na start) recykling (`GameService.drawCards`, już istniejący mechanizm) włącza się od razu przy pierwszym rozdaniu, nie gdzieś w 15. rundzie — nic się nie wywala, ale powtórki są bardziej widoczne. Realne rozwiązanie to więcej kart w pakietach (BIZNES.md/treść), nie kod.
- [x] Gracz dołączający w trakcie gry wchodzi od następnej rundy. `GameService.join` nie blokuje już dołączania poza LOBBY (tylko `FINISHED`); `Player.joinedAtRound` wyklucza nowego gracza z `Game.answerersInOrder()` na tę jedną rundę (brak ręki, nie czeka się na jego odpowiedź) — dostaje pełną rękę i wchodzi normalnie od następnej. `game.html` pokazuje mu „Dołączyłeś w trakcie tej rundy — zagrasz od następnej".
- [x] Prowadzący znika w trakcie rundy → automatyczna część już działała wcześniej (`Game.currentlyExpectedToAct` nie czeka na prowadzącego w ANSWERING, `checkTimeout`/RANKING wymusza odkrycie po 45s, `Game.nextLeader()` pomija `left=true`); nowa jest jawna akcja admina „Pomiń rundę” (`GameService.forceRevealRound`, `POST /games/{id}/skip-round`) — natychmiastowe odkrycie wyników bez czekania na timeout, przycisk widoczny w `game.html` tylko dla admina w ANSWERING/RANKING.
- [x] Ekran TV: kod QR do dołączania na `/games/{id}/tv` — generowany lokalnie przez ZXing (`com.google.zxing:core`/`javase`), `GET /games/{id}/qr.png`, koduje `/?code={id}` (absolutny URL budowany z żądania, respektuje `forward-headers-strategy`). `index.html` wstępnie wypełnia pole kodu z `${param.code}` po zeskanowaniu — bez JS.
- [x] **Liczba rund wybierana przy tworzeniu gry** (1..`game.max-total-rounds`, formularz podsuwa 10) — `Game.totalRounds`, `GameService.createGame`. Dziś przy tworzeniu, nie w panelu ustawień lobby.
- [x] **Tryb „do X punktów”** jako alternatywa dla liczby rund (`Game.targetScore`, opcjonalny; `totalRounds` nadal działa jako twardy limit bezpieczeństwa pod spodem) — sprawdzane w `GameService.nextRound` zamiast/obok licznika rund. Timeouty odpowiedzi/rankingu per-gra: `Game.answerSecondsOverride`/`rankingSecondsOverride` (opcjonalne, `null` = globalny domyślny z `application.yaml`), `Game.effectiveAnswerSeconds`/`effectiveRankingSeconds` czyta jedno z dwóch. Wszystko ustawiane opcjonalnie przy tworzeniu gry (`index.html`, sekcja „Ustawienia zaawansowane”), walidowane w `GameService.createGame` (progi w `game.max-target-score`, `game.timeouts.min/max-custom-seconds`), przenoszone też do rematchu.
- [x] **Pusta karta**: raz na grę gracz może zagrać własną, wpisaną odpowiedź (zamiast karty z ręki) — `Player.usedBlankCard`, `GameService.submitBlankAnswer`, `POST /games/{id}/blank-answer`. `Submission.customText` (nullable) obok `card` (też nullable); `Submission.text()` zwraca jedno z nich — reveal/ranking nie wiedzą, że to nie jest zwykła karta (anonimowo jak każda inna).
- [x] **Wymiana ręki**: raz na grę gracz może zrzucić całą rękę i dobrać nowe (`Player.usedHandSwap`, `GameService.swapHand`, `POST /games/{id}/swap-hand`). Zrzucone karty nie wracają do talii — to już działało: zagranie karty (w `drawCards`) oznacza ją jako użytą na zawsze, usunięcie z ręki tego nie zmienia.
- [x] **„Zagraj jeszcze raz”**: po końcu gry admin jednym kliknięciem zakłada nową grę z tymi samymi (aktywnymi) graczami i pakietami — `GameService.rematch`, `POST /games/{id}/rematch`, idempotentne (drugi klik nie tworzy drugiej gry). `Game.nextGameId` + `Player.nextPlayerId` niosą mapowanie starego gracza na jego wiersz w nowej grze; `GameController.view()`/`tv()` przekierowują tam automatycznie przy kolejnym pollu — stąd FINISHED musiało zostać dopisane do `autoRefresh` (wcześniej gra zakończona nie odpytywała wcale), żeby otwarte karty reszty graczy też same trafiły do nowej gry, bez wpisywania kodu.
- [ ] Talia przy pełnym stole: 10 graczy × 7 kart na start + 9 zrzutów × 20 rund ≈ 250 kart-odpowiedzi bez powtórek — wybrane pakiety muszą to pokryć (albo ograniczyć liczbę rund / ostrzec admina).
- [x] **Easter eggi tekstowe** (hat trick, zero bohater, zmartwychwstanie, bratnie dusze, sucha seria, zgaszony ekran w idealnym momencie) — bez wpływu na punkty, `EasterEggService` + `Game.showEasterEgg`/`Player.winStreak`/`scorelessStreak`/`lastAtMidpoint`, baner w `game.html`/`tv.html` (`view.easterEggText()`). Mechaniki z punktami (underdog bonus, runda-bonus, Rando...) wciąż niezrobione — lista z przykładowymi tekstami: [`MECHANICS.md`](MECHANICS.md) → „Easter eggi i uatrakcyjnienia”.

### Panel admina aplikacji (operator serwisu — nie mylić z adminem gry)
Osobna część pod `/admin`, tylko dla właściciela serwisu. Dostęp: rola `OPERATOR` (encja `Operator`, zero związku z kontami graczy) **i** docelowo Cloudflare Access (darmowy, logowanie przed wejściem na stronę, `DEPLOY.md`) — panel niewidoczny dla świata.
- [x] **Minimalna wersja zrobiona** — `/admin` (`AdminController`, `AdminDashboardService`), osobna tożsamość operatora: encja `Operator` + `OperatorUserDetailsService`, własny filter chain w `SecurityConfig` (`DaoAuthenticationProvider` scoped tylko do `/admin/**`, zero związku z `AppUser`). Konto tworzone tylko przez `OperatorSeeder` z env var (`OPERATOR_USERNAME`/`OPERATOR_PASSWORD`), nigdy przez formularz — puste domyślnie, brak konta lokalnie.
- [x] **Dashboard** (częściowo): liczba aktywnych/zakończonych gier. Reszta (dziś/w tygodniu, gracze online, średnie, % dokończonych) — niezrobione, czeka na Postgres (dane nie przeżywają restartu).
- [x] **Gry na żywo** (częściowo): lista aktywnych gier (kod, faza, runda, liczba graczy, czy ma admina) + akcja „zakończ” (`GameService.forceEndGame`, bez wymogu bycia adminem w grze). „Usuń porzuconą”/„sprzątnij teraz wszystkie stare” — niezrobione.
- [x] **Karty i pakiety** — `/admin/cards` (`AdminCardController`, `CardAdminService`, `CardAdminView`). CRUD pakietów (`CardPack.update`: nazwa, darmowy/cena, audience 18+/rodzinne/dzieci, daty sezonowe) i kart (`Card.update`, tekst/typ/pakiet); „wyłączanie” karty (`Card.enable`/`disable`, pole `enabled`) zamiast usuwania — bezpieczne wobec istniejących rąk/Submissions, które wciąż ją referencją; `CardRepository.findByTypeAndEnabledTrueAndPack_IdIn` wyklucza wyłączone z rozdawania. Import „CSV" = plik tekstowy, jedna karta na linię, dla wybranego pakietu+typu (bez zależności do parsowania CSV — tekst kart zawiera przecinki, prawdziwy CSV wymagałby cytowania). Nie zastępuje `CardSeeder` (seed startowy zostaje), tylko dodaje karty/pakiety ponad niego.
- [x] **Zgłoszone karty** — przycisk „Zgłoś” u gracza (karta na ręce, pytanie rundy, odkryta odpowiedź po REVEALED — nigdy przed odkryciem, żeby nie zdradzić autorstwa) woła `GameController.reportCard` → `CardReportService.reportCard` (encja `CardReport`, bez referencji do `Player`/`game`, tylko snapshot nicku). Kolejka na `/admin/reports` (`AdminReportController`): **usuń** = wyłącza kartę (`CardAdminService.setCardEnabled`, nie hard-delete — karta może wciąż być w czyjejś ręce w innej grze), **popraw** = edytuje tekst w miejscu, **odrzuć** = zamyka tylko to jedno zgłoszenie. Usuń/popraw czyszczą wszystkie zgłoszenia dla tej karty naraz.
- [ ] **Propozycje kart od graczy** (etap 2 — wymaga kont, patrz FORMAL.md/ROADMAP.md): poza samą rozgrywką, zalogowany gracz ma własny panel (nie w grze) z formularzem „Zaproponuj kartę” (tekst + typ) i listą swoich propozycji ze statusem — odwrotność „Zgłoszone karty" (tam flaguje istniejącą kartę, tu proponuje nową). Limity antyspamowe: max 100 kart na gracza (łącznie) i max 100 propozycji dziennie. Trafia do kolejki do akceptacji w panelu admina (analogicznie do `/admin/reports`) — karta wchodzi do talii (`CardAdminService.createCard`) tylko po walidacji/akceptacji admina, nigdy automatycznie. Walidacja jak przy `/admin/cards` (długość tekstu, niepusty) plus ręczna ocena treści (FORMAL.md: zero treści skopiowanych/nieodpowiednich). Zaakceptowane propozycje tworzą osobną pulę kart „users proposed” — część oferty freemium (BIZNES.md).
- [ ] **Plusiki i ranking kart**: gracze mogą oznaczyć kartę (zwłaszcza z puli „users proposed”) jako „super” (+1); ranking kart wg liczby plusików — razem z „Propozycje kart od graczy” wyżej napędza tę samą pulę freemium.
- [ ] **Statystyki kart**: najczęściej wygrywające / przegrywające odpowiedzi, najlepsze pytania, karty nigdy niezagrane.
- [ ] **„Chcę ten zestaw!”**: ranking zestawów wg liczby kliknięć (decyzja, co wydać w etapie 2).
- [x] **Ustawienia serwisu bez redeployu** — `/admin/settings` (`AdminSettingsController`), nowy pakiet `settings` (`AppSettings` jedno-wierszowa encja singleton, `AppSettingsService`) — zależność tylko w jedną stronę: `game`/`web` czytają `settings`, nigdy odwrotnie. Tryb serwisowy (`GameService.createGame` blokuje też rewanż, bo woła tę samą metodę; baner na `/`). Baner ogłoszeń widoczny wszystkim: `/`, `game.html`, `tv.html`. Limit aktywnych gier: `AppSettingsService.effectiveMaxActiveGames` nadpisuje `game.max-active-games` z `application.yaml`, gdy ustawiony. Włącz/wyłącz: pusta karta (`GameService.submitBlankAnswer` odrzuca po stronie serwera, nie tylko chowa przycisk) i baner „wkrótce” na `/packs`.
- [ ] **Moderacja**: lista zakazanych słów w nickach i pustych kartach; podgląd odrzuconych prób; zablokowanie IP przy nadużyciach.
- [ ] **Zdrowie systemu**: zużycie pamięci/CPU, rozmiar bazy, liczba trafień w rate limit, ostatnie błędy (500).
- [ ] Etap 2: zakupy, zwroty, przychody per zestaw.
- [ ] Log akcji operatora (kto, co, kiedy) — dla własnej kontroli.

### Dane
- [ ] Przejście z H2 na **Postgres + Flyway** (`ddl-auto=validate`) — znika też pułapka z enumami.
- [ ] **`docker-compose.yml` lokalnie, tylko z Postgresem** (dev, osobno od pełnego deploymentu niżej) — żeby gry/konta przetrwały restart/ubicie aplikacji nawet na czas dev; dziś (H2 + `ddl-auto=create-drop`) baza czyści się przy każdym starcie, patrz `application.yaml`.
- [ ] Sprzątanie porzuconych gier w tle (`@Scheduled`, np. brak aktywności > X h) — dziś wpis zostaje, jeśli nikt już nie odwiedzi gry.

### Bezpieczeństwo / odporność
- [x] Rate limiting per IP (Bucket4j, in-memory) na `POST /games` i `POST /games/join` (zgadywanie 5-znakowych kodów) — `RateLimitFilter`, limity w `application.yaml` (`rate-limit.*`, domyślnie 10/min). `/login`/`/register` już nie istnieją (sekcja wyżej). Zwraca 429.
- [ ] `AuthenticationAuditListener` (logowanie do `/admin`) nie loguje IP źródłowego nieudanej próby — bez tego nie da się wykryć brute force z samych logów. Dodać `HttpServletRequest`/`WebAuthenticationDetails` do loga `onFailure`.
- [x] `server.forward-headers-strategy=framework` w `application.yaml` — prawdziwe IP klienta za Cloudflare (`X-Forwarded-For`) trafia do `request.getRemoteAddr()`, który czyta `RateLimitFilter`; też poprawia flagę `Secure` na cookie sesji (`DEPLOY.md` sekcja 2).
- [x] Limity na grę: max liczba aktywnych gier globalnie — `GameProperties.maxActiveGames` (domyślnie 50), sprawdzane w `GameService.createGame`. Per IP/użytkownik wciąż czeka na prawdziwe IP (`DEPLOY.md` sekcja 3) — zrobimy razem z rate limitingiem.
- [ ] Twarde limity zasobów: `-Xmx` dla JVM, `mem_limit`/`cpus` w docker compose.
- [ ] Polling co 3 s musi się mieścić w rate limicie (10 graczy + TV ≈ 4 req/s na grę).

### Deployment
- [ ] `Dockerfile` + `docker-compose.yml` (app + Postgres, wolumen na dane, backup bazy).
- [ ] **Tani VPS ze stałą ceną** (Hetzner / OVH / Mikrus) — **żadnych usług pay-per-use** (AWS/GCP/Azure), żeby atak nie mógł nabić rachunku.
- [ ] **Cloudflare (free)**: DNS + proxy (ochrona DDoS), Bot Fight Mode, reguła rate limit.
- [ ] **Cloudflare Tunnel** (`cloudflared`) — zero otwartych portów na VPS, IP serwera ukryte. Firewall: deny all inbound; SSH tylko po kluczu (Tailscale/tunel).
- [ ] Żadnych rekordów DNS bez proxy (szara chmurka) wskazujących na VPS; nie wysyłać maili bezpośrednio z serwera.
- [ ] Sekrety (OAuth, hasło DB) przez zmienne środowiskowe, nie w repo.

### Anonimowość
- [ ] Commity z adresu noreply GitHuba (`git config user.email <id>+<login>@users.noreply.github.com`), ewentualnie repo prywatne.
- [ ] Brak danych osobowych w stopce/stronie; osobny alias e-mail do kontaktu; domena z ukrytym WHOIS.
- [ ] Zbierać minimum danych o graczach (nick); przy kontach i płatnościach — RODO (polityka prywatności).

### Płatności
- [ ] Etap 2: prawdziwy operator płatności zamiast stubu (`PacksController.purchase`). W etapie 1 zakupy wyłączone. Specyfikacja: sekcja 6.

### Biznes i pakiety
- Monetyzacja, pakiety okolicznościowe i pomysły biznesowe → [`BIZNES.md`](BIZNES.md). Plan funkcji w czasie → [`ROADMAP.md`](ROADMAP.md).

## 5. Planowana zmiana (MVP): rola admina

### Problem
Dziś każdy gracz może wystartować grę, przejść do następnej rundy, przełączyć tryb rodzinny i zakończyć grę. Przy większym stole (do 10 osób) to chaos: ktoś przypadkiem kończy grę albo przeskakuje wyniki rundy, zanim reszta je zobaczy.

### Zasada
W każdej grze jest **dokładnie jeden admin** (albo chwilowo żaden — patrz „Ucieczka admina”). Tylko admin zarządza grą i graczami. Pozostali tylko grają.

Admin to **zwykły gracz z dodatkowymi uprawnieniami**: dostaje karty, odpowiada, zdobywa punkty i normalnie bywa prowadzącym rundy. Admin ≠ prowadzący — prowadzący dalej rotuje co rundę, admin się nie zmienia.

### Kto zostaje adminem
- Osoba, która **założyła grę**, automatycznie zostaje adminem.

### Co może tylko admin

| Akcja | Dziś | Po zmianie |
|---|---|---|
| Start gry (z lobby) | każdy gracz | tylko admin |
| „Następna runda” (po wynikach) | każdy gracz | tylko admin |
| Tryb rodzinny wł./wył. | każdy gracz | tylko admin |
| Zakończenie gry | każdy gracz | tylko admin |
| **Wyrzucenie gracza** | — (nie istnieje) | tylko admin, nowa akcja |

Co zostaje bez zmian, dla każdego gracza: dołączenie kodem, wybór odpowiedzi, ranking (tylko prowadzący rundy), „Opuść grę”.

Inni gracze **nie widzą** przycisków admina. Gdyby mimo to wysłali taką akcję, serwer ją odrzuca z komunikatem „Tylko admin może to zrobić.”

### Wyrzucanie gracza
- Admin widzi przy każdym graczu (poza sobą) przycisk „Wyrzuć”.
- Wyrzucony gracz jest traktowany tak samo jak ktoś, kto kliknął „Opuść grę”: w lobby znika z listy, w trakcie gry wypada z kolejki prowadzących, scoreboardu i dobierania kart.
- Wyrzucony gracz widzi komunikat „Admin usunął Cię z gry” i wraca na stronę główną. Pozostali widzą baner „X został(a) usunięty(a) z gry”.
- Jeśli wyrzucony był akurat prowadzącym albo jeszcze nie odpowiedział — runda zachowuje się tak, jakby wyszedł (ta sama ścieżka co dziś przy wyjściu gracza).

### Ucieczka admina
Admin „ucieka”, gdy:
- kliknie „Opuść grę”, albo
- zostanie uznany za rozłączonego (ten sam mechanizm co dla zwykłych graczy — brak aktywności dłużej niż `DISCONNECT_TIMEOUT_SECONDS`).

Wtedy gra **zostaje bez admina** i toczy się dalej (bieżąca runda nie jest przerywana), ale nikt nie może wykonać akcji admina, dopóki ktoś go nie przejmie.

### „Przejmij admina”
- Gdy gra nie ma admina, **każdy aktywny gracz** widzi przycisk **„Przejmij admina”** i baner „Admin opuścił grę — ktoś musi przejąć prowadzenie gry”.
- **Kto pierwszy kliknie, ten zostaje adminem.** Pozostałym przycisk znika; jeśli kliknęli w tej samej chwili, dostają komunikat „Ktoś właśnie przejął admina.”
- Wszyscy widzą baner „X jest teraz adminem”.
- Ekran TV pokazuje, kto jest adminem, albo „Brak admina” — żeby przy stole było jasne, kto ma kliknąć.
- Przejąć admina można tylko wtedy, gdy go nie ma. Nie da się „odebrać” admina komuś, kto jest aktywny.
- Działa tak samo w lobby i w trakcie gry.

### Powrót dawnego admina
Uciekinier nie odzyskuje roli automatycznie. Jeśli wróci do gry, a admin jest wolny — może kliknąć „Przejmij admina” jak każdy. Jeśli admin jest już zajęty — jest zwykłym graczem.

### Przypadki brzegowe
- **Gracz zostaje sam / gra pustoszeje** — bez zmian: gdy wyjdzie ostatni aktywny gracz, gra jest usuwana.
- **Admin wyrzuca wszystkich** — gra trwa w lobby z samym adminem; start i tak wymaga minimum graczy.
- **Wyniki rundy bez admina** — gracze widzą wyniki, ale „Następna runda” pojawi się dopiero, gdy ktoś przejmie admina. Baner podpowiada, co zrobić.
- **Lobby bez admina** — nikt nie wystartuje gry, dopóki ktoś nie przejmie admina.

### Kryteria akceptacji (MVP)
1. Założyciel gry jest adminem i widzi przyciski: Start, Następna runda, Tryb rodzinny, Zakończ grę, Wyrzuć (przy innych graczach).
2. Zwykły gracz nie widzi tych przycisków, a próba wykonania akcji admina kończy się komunikatem błędu i niczego nie zmienia.
3. Admin wyrzuca gracza → gracz znika z gry, dostaje komunikat, reszta widzi baner.
4. Admin klika „Opuść grę” albo się rozłącza → wszyscy pozostali widzą „Przejmij admina”.
5. Dwóch graczy klika „Przejmij admina” jednocześnie → adminem zostaje dokładnie jeden, drugi dostaje komunikat.
6. Po przejęciu nowy admin może wykonać wszystkie akcje admina; ekran TV pokazuje nowego admina.
7. Nikt nie może przejąć admina, gdy obecny admin jest aktywny.

### Decyzje (MVP)
- Wyrzucony gracz **może wrócić** kodem do tej samej gry (nie pamiętamy wyrzuconych).
- Admin **może** przekazać rolę konkretnej osobie bez wychodzenia z gry (`GameService.transferAdmin`) — zmiana względem pierwotnego MVP.
- Wyniki rundy bez admina **czekają** na przejęcie admina (brak automatycznego przejścia dalej).

## 6. Planowana integracja (etap 2): operator płatności

> Dziś `PacksController.purchase` to czysty stub — odblokowuje pakiet bez żadnej realnej płatności (`log.info("Zakup (symulowany)...")`), zgodnie z etapem 1 (`FORMAL.md`). Ta sekcja to plan na etap 2, nie coś do wdrożenia teraz.

### Decyzja: hostowany checkout dostawcy + webhook, nie Shopify
Shopify nie jest dobrym fitem — to platforma pod katalog produktów, a i tak trzeba by samemu napisać webhook + sprawdzanie entitlementu po stronie naszej appki (Shopify domyślnie tylko zmienia status zamówienia, nic nie odblokowuje automatycznie). Ta sama praca integracyjna, plus dodatkowy koszt i osobny katalog produktów równolegle z `CardPack`.

Dwie realne opcje:
- **Stripe Checkout** — my zostajemy sprzedawcą (merchant of record): sami rozliczamy VAT (Stripe Tax pomaga, odpowiedzialność jest nasza). Niższa opłata (~2.9% + 30¢/transakcję).
- **Paddle** — **Paddle jest prawnie sprzedawcą**, sam rozlicza i odprowadza VAT, sam wystawia faktury klientom. Wyższa opłata (~5% + 50¢/transakcję), mniej papierkowej roboty księgowej dla nas. *(Lemon Squeezy — podobny model — przejęte przez Stripe w 2024, w trakcie łączenia ze „Stripe Managed Payments"; dziś to efektywnie dwie niezależne opcje, nie trzy.)*

Merchant-of-record (Paddle) zdejmuje rozliczanie VAT, ale **nie zwalnia** z założenia jawnej spółki w etapie 2 (`FORMAL.md` jest jasne: przy sprzedaży anonimowość się kończy) — to mniej księgowości, nie mniej jawności. **Ostateczny wybór wymaga konsultacji prawnej/księgowej przed etapem 2**, zgodnie z notatką na górze `FORMAL.md`.

### Jak to wpada w istniejący kod
1. `PacksController.purchase` zamiast odblokowywać pakiet od razu, tworzy hostowaną sesję płatności (Stripe Checkout Session / Paddle Checkout) z metadanymi `appUserId` + `packId` i przekierowuje gracza tam — **karta nigdy nie trafia na nasz serwer** (PCI compliance po stronie dostawcy).
2. Nowy endpoint `POST /webhooks/payments` — dostawca wywołuje go po udanej płatności; weryfikacja podpisu (HMAC, sekret z env var, nigdy w repo, patrz TODO „Deployment"). Dopiero wtedy `Purchase` dostaje status `COMPLETED` i pakiet się odblokowuje.
3. Zgodne z już zaplanowanym deploymentem — webhook to zwykły ruch HTTPS na nasz publiczny hostname przez Cloudflare Tunnel, nie wymaga żadnego otwartego portu na VPS; żadnej zmiany w modelu „deny all inbound" (TODO „Deployment").
4. Refundy/chargebacki też przychodzą jako webhooki — obsłużyć jako unieważnienie `Purchase`.
5. `Purchase` nie powinien nigdy przechowywać danych karty — tylko ID transakcji dostawcy, status, `packId`, `appUserId`.
