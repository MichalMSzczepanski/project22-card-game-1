# CLAUDE.md

Towarzyska gra karciana typu pytanie–odpowiedź: telefony graczy + wspólny ekran TV.

## Pliki projektu (czytaj tylko to, czego potrzebujesz)
| Plik | Co zawiera | Kiedy czytać |
|---|---|---|
| `README.md` | stan techniczny: przepływ, encje, błędy, pułapki (1–3), TODO (4), spec. roli admina gry (5) | zmiany w kodzie, planowanie zadań |
| `MECHANICS.md` | zasady gry — źródło prawdy dla reguł (⚠️ = planowane) | zmiany logiki gry |
| `CODING.md` | standardy kodu | **zawsze przed pisaniem lub zmianą kodu** |
| `FORMAL.md` | prawo, licencje, anonimowość, etapy (darmowe MVP → freemium) | karty, nazwa, wygląd, zakupy, dane osobowe |
| `BIZNES.md` | zarabianie, walidacja, pakiety okolicznościowe | pakiety, płatności, metryki |
| `ROADMAP.md` | wersje i funkcje ze statusem | planowanie; **aktualizuj status po skończeniu funkcji** |
| `IDEAS.md` | brain-dump pomysłów (wszystkie kategorie) + research konkurencji, nieposegregowane do roadmapy | szukanie inspiracji, planowanie nowych funkcji |
| `DEPLOY.md` | plan wdrożenia: VPS + Cloudflare Tunnel krok po kroku, rate limiting, decyzja o Dockerze, Postgres, sesja gracza | deployment, infrastruktura, bezpieczeństwo produkcyjne |
| `CLAUDE.md` | ten plik | zawsze (wczytywany automatycznie) |

Nowy plik `.md` w repo → dopisz go do tej tabeli.
„Admin gry” (rola w grze, README sekcja 5) ≠ „panel admina aplikacji” (`/admin`, operator serwisu, README TODO).

## Stack
Java 17, Spring Boot 4.1.2, Maven · Thymeleaf **bez JS** (meta-refresh co 3 s) · JPA + H2 plikowe (`ddl-auto=create-drop` **tymczasowo** — baza czyści się przy każdym starcie, patrz `application.yaml`) · Spring Security + Spring Session JDBC.

## Komendy
- `./mvnw spring-boot:run` (konsola H2: `-Dspring-boot.run.profiles=dev`)
- `./mvnw test`

## Niezmienniki (nie łam)
- Cały stan gry w bazie; w sesji tylko `playerId`/`gameId`.
- Logika tylko na serwerze; akcje walidowane po fazie i roli.
- Gracz nie widzi cudzych kart; TV tylko informacje publiczne.
- Brak schedulera: timeouty i rozłączenia leniwie przy każdym wejściu na stronę.

## Decyzje
- Etap 1: zakupy wyłączone (404), karty wyłącznie własne, do 10 graczy, dołączanie w trakcie gry. Konta/logowanie gracza **wróciły** (`AuthController`, `/login`/`/register`) — wcześniej niż planowano, bo wymaga ich „Propozycje kart od graczy” (README). OAuth działa tylko gdy skonfigurowane (env var), dziś nieaktywne. ⚠️ FORMAL.md: polityka prywatności jest teraz zaległością, nie czymś na etap 2.
- Rola admina — zrobiona w całości, spec. README sekcja 5: `Game.adminPlayerId` (`isHost()` usunięty), `GameService.requireAdmin`/`kickPlayer`/`takeOverAdmin`/`transferAdmin` (dobrowolne przekazanie), `forceRevealRound` („Pomiń rundę”), `rematch` („Zagraj jeszcze raz”, idempotentne).
- Rozgrywka rozszerzona: pusta karta i wymiana ręki (raz na grę), tryb „do X punktów” jako alternatywa dla liczby rund, per-gra nadpisanie timeoutów odpowiedzi/rankingu, kod QR do dołączania na TV. Szczegóły reguł: `MECHANICS.md`, metody: README.
- Panel admina aplikacji (`/admin`, operator serwisu) — osobna tożsamość od graczy (`Operator`), bootstrap tylko z env var. Zrobione: dashboard + gry na żywo, karty i pakiety (CRUD + import tekstowy), zgłoszone karty (kolejka + usuń/popraw/odrzuć), ustawienia serwisu bez redeployu (tryb serwisowy, baner ogłoszeń, limit aktywnych gier, przełączniki pustej karty/„wkrótce” — nowy pakiet `settings`). Easter eggi tekstowe z MECHANICS.md też zrobione (`EasterEggService`).
- Rate limiting (Bucket4j, per-IP) na `POST /games`/`/games/join` + `server.forward-headers-strategy=framework`.
- Deployment: VPS ze stałą ceną + Cloudflare Tunnel; **żadnych usług pay-per-use**.
- Docelowo Postgres + Flyway. Komunikacja zostaje polling (bez WebSocketów).
- Bez danych osobowych autora w kodzie i konfiguracji.

## Oszczędzanie tokenów
- Nie czytaj całego README ani wszystkich plików naraz — tylko sekcje/pliki dotyczące zadania.
- Odpowiadaj zwięźle; nie powtarzaj treści plików w odpowiedzi.
- Odpowiadaj dokładnie na zadane pytanie/polecenie. Nie dodawaj nieproszonych fragmentów, list czy kontekstu, o które nikt nie pytał.
- Gdy nie jesteś pewien zakresu albo intencji — zadaj pytanie, zamiast zgadywać i zwracać ścianę tekstu.
