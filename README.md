# Karciana Gra

Przeglądarkowa gra towarzyska typu pytanie–odpowiedź z rotującym prowadzącym do grania w tej samej sieci Wi-Fi z telefonów. Spring Boot + Thymeleaf, bez JS po stronie gry — cała logika i stan żyją na backendzie.

Standardy kodu: [`CODING.md`](CODING.md) · Prawo, licencje, etapy: [`FORMAL.md`](FORMAL.md) · Mechanika gry: [`MECHANICS.md`](MECHANICS.md) · Zarabianie: [`BIZNES.md`](BIZNES.md) · Roadmapa: [`ROADMAP.md`](ROADMAP.md) · Instrukcje dla Claude: [`CLAUDE.md`](CLAUDE.md)

## 1. Jak działa gra (przepływ)

1. **Tworzenie gry** — `POST /games`. Osoba tworząca podaje nick i wybiera pakiety kart; dostaje losowy 5-znakowy kod gry (`GameCodeGenerator`) i automatycznie dołącza jako pierwszy gracz.
2. **Lobby** — inni dołączają kodem (`POST /games/join`). Min. 3, max. 5 graczy (`game.players.min` / `game.players.max` w `application.yaml`, bindowane przez `GameProperties`).
3. **Start** — **każdy** gracz w lobby może kliknąć "Start gry" (nie tylko założyciel) — `GameController.start` nie sprawdza, kto klika, tylko czy jest wystarczająco graczy.
4. **20 rund** (`game.total-rounds`), każda ma 3 fazy:
   - `ANSWERING` — prowadzący dostaje czarną kartę-pytanie (`CardType.QUESTION`); wszyscy pozostali gracze wybierają po jednej karcie-odpowiedzi (`CardType.ANSWER`) z ręki (7 kart, `game.hand-size`). Limit czasu: `game.timeouts.answer-seconds` (60s) — kto nie zdąży, dostaje losową kartę z ręki.
   - `RANKING` — prowadzący wybiera **podium** z zebranych (potasowanych, anonimowych) odpowiedzi: miejsca wg `game.scoring.podium-size` (domyślnie 3, mniej jeśli jest mniej odpowiedzi) — `Round.podiumSize()`. Limit czasu: `game.timeouts.ranking-seconds` (45s) — po nim runda odkrywa się z tym, co prowadzący zdążył przyznać; nieprzyznane miejsca i odpowiedzi poza podium dostają 0 pkt.
   - `REVEALED` — wyniki rundy: kto napisał co i ile punktów to dało. Dowolny gracz klika "Następna runda".
   - **Ręka gracza trwa przez całą grę.** Na starcie każdy dostaje 7 kart-odpowiedzi i trzyma je z rundy na rundę. W każdej rundzie „zrzuca” dokładnie jedną (tę, którą zagrał, albo losową przy timeoucie), a przed następną rundą dobiera jedną nową z talii (`GameService.dealUpToHandSize`) — ręka zawsze wraca do 7. Prowadzący w swojej rundzie nie zrzuca nic, jego ręka czeka nietknięta. Zagrane karty nie wracają do talii w tej grze (`Game.usedAnswerCardIds`).
5. **Prowadzący rotuje** — `Game.nextLeader()` idzie po kolei po `joinOrder` (kto pierwszy dołączył, ten pierwszy prowadzi), pętla z zawijaniem, przeskakując graczy którzy wyszli.
6. **Koniec gry** — po 20 rundach (`GameStatus.FINISHED`) albo wcześniej, jeśli ktoś kliknie "Zakończ grę".

### Punktacja (podium, nie zgadywanie autorstwa)

Prowadzący przyznaje tylko miejsca podium — domyślnie 3: 1. = 3 pkt, 2. = 2 pkt, 3. = 1 pkt, reszta 0 pkt (`game.scoring.podium-size`/`podium-points` w `application.yaml`, `Round.pointsForRank`). Jeśli jest mniej odpowiedzi niż rozmiar podium, podium ma tyle miejsc, ile jest odpowiedzi (`Round.podiumSize()`). Punkty dostaje **autor karty**, nie prowadzący; prowadzący nie zdobywa punktów w danej rundzie. Runda przechodzi do `REVEALED` automatycznie w momencie obsadzenia ostatniego miejsca podium (`GameService.scoreRound`/`submitRanking`) — nieobsadzone odpowiedzi poza podium zostają z 0 pkt. Szczegóły reguły: [`MECHANICS.md`](MECHANICS.md) → Punktacja.

### Tryb rodzinny i pakiety kart

- "Tryb rodzinny" (`setFamilyMode`) na żywo odfiltrowuje karty `PackAudience.ADULT` z rąk graczy i z dobierania.
- Pakiety (`CardPack`): darmowe ("Generyczny", "Dla dzieci") i płatne (Pikantna, Biurowa, Pop-kultura, Sylwestrowa — sezonowa, aktywna tylko 15.12–15.01).

### Wychodzenie z gry / wykrywanie rozłączenia

- Przycisk "Opuść grę" (`POST /games/{id}/leave`) — natychmiastowe wyjście.
- **Bez JS**: każde zwykłe odpytanie `/games/{id}` co 3s (meta-refresh, `game.poll-interval-seconds`) odświeża "ostatnio widziano" (`Player.lastSeenAt`) tego gracza. Przy każdym żądaniu serwer sprawdza też (`pruneDisconnectedPlayers`), czy ktoś INNY nie ucichł dłużej niż `game.timeouts.disconnect-seconds` (15s) — jeśli tak, traktuje go jak zamkniętą kartę i usuwa. Gracz, na którego akurat czeka runda (jeszcze nie odpowiedział / jeszcze ranguje), jest z tego wyłączony — jego ekran z założenia nie odświeża się, więc cisza nie znaczy "wyszedł".
- W lobby wyjście usuwa gracza od razu; w trakcie gry zostaje tylko oznaczony `left=true` (stare `Submission`/`Ranking` wciąż na niego wskazują) i wyfiltrowany z `Game.activePlayers()` (scoreboard, kolejka prowadzącego, dobieranie kart).
- Gdy **ostatni aktywny** gracz wyjdzie — cała gra jest usuwana z bazy. Pozostałym graczom (i ekranowi TV) pokazuje się baner "X opuścił(a) grę" na `game.timeouts.leave-banner-seconds` (10s).
- **Limit tego podejścia**: to czysto reaktywne na żądania — jeśli *nikt* już nigdy nie odwiedzi URL-a gry (zamknięte karty, nikt nie otworzył ekranu TV), wpis w bazie fizycznie zostaje (nic go nie posprząta w tle — nie ma schedulera/`@Scheduled`).

### Ekran TV

`GET /games/{id}/tv` — osobny widok bez akcji, do wyświetlenia na wspólnym ekranie. Też odpytywany co `game.poll-interval-seconds` (3s), też wywołuje `checkTimeout`/`pruneDisconnectedPlayers`.

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

To samo dotyczy każdej zmiany nazwy kolumny/tabeli powiązanej z takim enumem — stare nazwy (np. `black_card_id`, `game_used_black_cards`) zostają w bazie jako osierocone, nieużywane artefakty po `ddl-auto=update`; to nieszkodliwe, ale warte wiedzy przy czytaniu schematu. Przy przejściu na Postgres (patrz komentarz w `application.yaml`) ten cały problem znika razem z wprowadzeniem Flyway/Liquibase.

**To dotyczy każdego enuma mapowanego `@Enumerated(STRING)`, nie tylko `card.type`** — przy zmianie `GameStatus.GUESSING` → `RANKING` ten sam problem wystąpił osobno na kolumnie `game.status` (`ENUM('ANSWERING','FINISHED','GUESSING','LOBBY','REVEALED')` — stare ograniczenie nie zawierało `RANKING`). Przy każdej zmianie nazwy wartości enuma: sprawdź WSZYSTKIE kolumny tego typu w bazie, nie tylko tę, przez którą akurat przeszedł request —
```sql
-- znajdź wszystkie kolumny ENUM i ich aktualne dozwolone wartości:
SCRIPT NOPASSWORDS NOSETTINGS;   -- albo: SELECT * FROM INFORMATION_SCHEMA.COLUMNS gdzie DATA_TYPE='ENUM'
```
a potem dla każdej: poszerz → zaktualizuj dane → zwęż, tak jak wyżej.

## 4. TODO

### Etap 1 — darmowe MVP (patrz `FORMAL.md`)
- [ ] Wyłączyć zakupy (`PacksController.purchase`); płatne pakiety jako „Tematyczne zestawy — wkrótce” (bez dat i cen).
- [ ] Przycisk „Chcę ten zestaw!” przy każdym zestawie + licznik kliknięć (bez danych osobowych, ograniczenie wielokrotnego klikania np. per sesja).
- [ ] Wyłączyć rejestrację / logowanie / OAuth i historię wyników — same nicki.
- [ ] Strona „O grze”: darmowa, nie zbiera danych osobowych, treści 18+ poza trybem rodzinnym.
- [ ] Wszystkie karty własne — przejrzeć `CardSeeder` pod kątem tekstów z innych gier.
- [ ] Własna kolorystyka i font kart (nie czarno-białe w stylu CAH).

### Rozgrywka
- [x] **Punktacja podium 3-2-1**: prowadzący wybiera tylko 3 najlepsze odpowiedzi (zamiast układać wszystkie); mniej odpowiedzi → mniej miejsc; timeout → punkty tylko za przyznane miejsca (`GameService.scoreRound`, `submitRanking`, `Round.podiumSize`/`pointsForRank`). Spec: `MECHANICS.md`.
- [ ] **Rola admina** — tylko admin zarządza grą i graczami; gdy admin ucieknie, dowolny gracz może go przejąć przyciskiem „Przejmij admina”. Specyfikacja: sekcja 5.
  - [ ] Założyciel = admin; akcje admina (start, następna runda, tryb rodzinny, zakończ) zablokowane dla reszty.
  - [ ] Nowa akcja „Wyrzuć gracza” (wyrzucony może wrócić kodem).
  - [ ] Gra bez admina + przycisk „Przejmij admina” (kto pierwszy, ten admin); wyniki rundy czekają na przejęcie.
  - [ ] TV pokazuje aktualnego admina / „Brak admina”.
  - [ ] Później (poza MVP): dobrowolne przekazanie roli admina konkretnej osobie.
- [ ] **Do 10 graczy** (+ prowadzący) — podnieść `Game.MAX_PLAYERS`; sprawdzić, czy talia/pakiety starczą na 20 rund przy pełnym stole (`HAND_SIZE` × gracze + dobieranie).
- [ ] Gracz dołączający w trakcie gry wchodzi od następnej rundy.
- [ ] Prowadzący znika w trakcie rundy → po timeoucie lub decyzją ownera runda pominięta, prowadzenie przechodzi dalej.
- [ ] Ekran TV: kod QR do dołączania na `/games/{id}/tv`.
- [ ] **Ustawienia admina przed startem**: liczba rund (np. 10 / 20) albo tryb „do X punktów”, długość timeoutów odpowiedzi i rankingu. Przy 10 graczach 20 rund to bardzo długa gra.
- [ ] **Pusta karta**: raz na grę gracz może zagrać własną, wpisaną odpowiedź (zamiast karty z ręki). Na rankingu wygląda jak każda inna — anonimowo.
- [ ] **Wymiana ręki**: raz na grę gracz może zrzucić całą rękę i dobrać 7 nowych (gdy nic nie pasuje). Zrzucone karty nie wracają do talii.
- [ ] **„Zagraj jeszcze raz”**: po końcu gry admin jednym kliknięciem zakłada nową grę z tymi samymi graczami i pakietami — bez ponownego wpisywania kodu.
- [ ] Talia przy pełnym stole: 10 graczy × 7 kart na start + 9 zrzutów × 20 rund ≈ 250 kart-odpowiedzi bez powtórek — wybrane pakiety muszą to pokryć (albo ograniczyć liczbę rund / ostrzec admina).

### Panel admina aplikacji (operator serwisu — nie mylić z adminem gry)
Osobna część pod `/admin`, tylko dla właściciela serwisu. Dostęp: rola `ADMIN` w aplikacji **i** Cloudflare Access (darmowy, logowanie przed wejściem na stronę) — panel niewidoczny dla świata.
- [ ] **Dashboard**: gry teraz / dziś / w tygodniu, gracze online, średnia liczba graczy na grę, % gier dokończonych do końca, porzucone gry, średni czas gry.
- [ ] **Gry na żywo**: lista aktywnych gier (kod, faza, runda, liczba graczy, ostatnia aktywność); podgląd stanu; akcje: zakończ grę, usuń porzuconą, „sprzątnij teraz” wszystkie stare.
- [ ] **Karty i pakiety**: dodawanie / edycja / wyłączanie kart, przypisanie do pakietu, oznaczenie 18+ / rodzinne, daty sezonowe pakietów; import z CSV. Docelowo zastępuje hardkod w `CardSeeder`.
- [ ] **Zgłoszone karty**: kolejka zgłoszeń „Zgłoś kartę” — akcje: usuń, popraw, odrzuć zgłoszenie.
- [ ] **Statystyki kart**: najczęściej wygrywające / przegrywające odpowiedzi, najlepsze pytania, karty nigdy niezagrane.
- [ ] **„Chcę ten zestaw!”**: ranking zestawów wg liczby kliknięć (decyzja, co wydać w etapie 2).
- [ ] **Ustawienia serwisu bez redeployu**: tryb serwisowy (baner „przerwa techniczna”, blokada nowych gier), baner ogłoszeń dla wszystkich, limit aktywnych gier, włącz/wyłącz funkcje (np. „wkrótce”, pusta karta).
- [ ] **Moderacja**: lista zakazanych słów w nickach i pustych kartach; podgląd odrzuconych prób; zablokowanie IP przy nadużyciach.
- [ ] **Zdrowie systemu**: zużycie pamięci/CPU, rozmiar bazy, liczba trafień w rate limit, ostatnie błędy (500).
- [ ] Etap 2: zakupy, zwroty, przychody per zestaw.
- [ ] Log akcji operatora (kto, co, kiedy) — dla własnej kontroli.

### Dane
- [ ] Przejście z H2 na **Postgres + Flyway** (`ddl-auto=validate`) — znika też pułapka z enumami.
- [ ] Sprzątanie porzuconych gier w tle (`@Scheduled`, np. brak aktywności > X h) — dziś wpis zostaje, jeśli nikt już nie odwiedzi gry.

### Bezpieczeństwo / odporność
- [ ] Rate limiting per IP (np. Bucket4j), szczególnie na `POST /games`, `POST /games/join` (zgadywanie 5-znakowych kodów), `/login`, `/register`.
- [ ] Za Cloudflare: prawdziwe IP klienta z `CF-Connecting-IP` (`server.forward-headers-strategy` / filtr) — potrzebne do rate limitu i logów.
- [ ] Limity na grę: max liczba aktywnych gier globalnie i per IP/użytkownik.
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
- [ ] Etap 2: prawdziwy operator płatności zamiast stubu (`PacksController.purchase`). W etapie 1 zakupy wyłączone.

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
- Admin **nie może** przekazać roli konkretnej osobie bez wychodzenia z gry — poza zakresem MVP.
- Wyniki rundy bez admina **czekają** na przejęcie admina (brak automatycznego przejścia dalej).
