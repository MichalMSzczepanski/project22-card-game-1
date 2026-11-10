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
| `CLAUDE.md` | ten plik | zawsze (wczytywany automatycznie) |

Nowy plik `.md` w repo → dopisz go do tej tabeli.
„Admin gry” (rola w grze, README sekcja 5) ≠ „panel admina aplikacji” (`/admin`, operator serwisu, README TODO).

## Stack
Java 17, Spring Boot 4.1.2, Maven · Thymeleaf **bez JS** (meta-refresh co 3 s) · JPA + H2 plikowe (`ddl-auto=update`) · Spring Security + Spring Session JDBC.

## Komendy
- `./mvnw spring-boot:run` (konsola H2: `-Dspring-boot.run.profiles=dev`)
- `./mvnw test`

## Niezmienniki (nie łam)
- Cały stan gry w bazie; w sesji tylko `playerId`/`gameId`.
- Logika tylko na serwerze; akcje walidowane po fazie i roli.
- Gracz nie widzi cudzych kart; TV tylko informacje publiczne.
- Brak schedulera: timeouty i rozłączenia leniwie przy każdym wejściu na stronę.

## Decyzje
- Rola admina — w planach, spec. README sekcja 5.
- Deployment: VPS ze stałą ceną + Cloudflare Tunnel; **żadnych usług pay-per-use**.
- Docelowo Postgres + Flyway. Komunikacja zostaje polling (bez WebSocketów).
- Bez danych osobowych autora w kodzie i konfiguracji.
- Etap 1: darmowe MVP — bez zakupów i kont; karty wyłącznie własne (nie z innych gier).

## Oszczędzanie tokenów
- Nie czytaj całego README ani wszystkich plików naraz — tylko sekcje/pliki dotyczące zadania.
- Odpowiadaj zwięźle; nie powtarzaj treści plików w odpowiedzi.
