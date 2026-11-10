# CODING.md — standardy kodu

Proste zasady, których się trzymamy. Krótko, bez teorii. Nowa zasada = jedna linijka.

## Architektura
- **Pakiety po funkcjonalności** (`game/`, `card/`, `user/`, `security/`, `stats/`, `web/`), nie po warstwach.
- Warstwy: **kontroler → serwis → repozytorium**. Kontroler nie woła repozytorium bezpośrednio.
- **Kontroler jest cienki**: odczytaj wejście, zawołaj serwis, wybierz widok. Zero logiki gry w kontrolerach i szablonach.
- **Logika domenowa w encjach**, jeśli dotyczy tylko ich stanu (np. `Game.nextLeader()`, `Game.activePlayers()`). Serwis orkiestruje, encja pilnuje swoich reguł.
- Widok dostaje gotowy model (`GameViewModel`), a nie encje. Szablon niczego nie liczy.

## SOLID w praktyce
- **S** — jedna klasa, jedna odpowiedzialność. Gdy serwis puchnie (>~400 linii) albo miesza tematy, wydziel nowy (np. rozdawanie kart, punktacja).
- **O** — nowe zachowanie przez nową klasę/strategię, nie przez kolejny `if` na typie (np. tryby gry, rodzaje pakietów).
- **L** — implementacja interfejsu nie zmienia jego kontraktu (nie rzuca nagle wyjątku, nie ignoruje parametru).
- **I** — małe interfejsy pod konkretną potrzebę zamiast jednego „do wszystkiego”.
- **D** — zależności przez konstruktor, od abstrakcji tam, gdzie realnie są dwie implementacje (np. przyszły operator płatności). Nie twórz interfejsu „na zapas”.

## Spring
- **Wstrzykiwanie tylko przez konstruktor**, pola `private final`. Bez `@Autowired` na polach (wyjątek: testy).
- Konstruktor DI przez Lombok `@RequiredArgsConstructor` (nie pisz go ręcznie) — wymaga explicit `annotationProcessorPaths` w `pom.xml` (implicitne wykrywanie procesora z classpath nie działa na tym toolchainie).
- `@Transactional` na serwisach; odczyty `@Transactional(readOnly = true)`. `open-in-view=false` zostaje — wszystko, co widok potrzebuje, ładuj w serwisie.
- Konfiguracja w `application.yaml`, sekrety przez zmienne środowiskowe — nigdy w repo.
- Reguły gry (liczba graczy, rozmiar ręki, liczba rund, timeouty, długość nicku/kodu gry, interwał odświeżania) w jednym miejscu: `GameProperties` (`@ConfigurationProperties(prefix="game")`, sekcja `game:` w `application.yaml`), wstrzykiwane przez konstruktor — nie rozsiane magiczne liczby, nie duplikować domyślnych wartości w kodzie (także w szablonach — `th:maxlength` z modelu, nie literał w HTML).

## Lombok
- `@RequiredArgsConstructor` zamiast ręcznego konstruktora wszędzie, gdzie jest tylko wstrzykiwanie zależności (serwisy, kontrolery, komponenty). Pola bez inicjalizatora = parametr konstruktora; pole z inicjalizatorem (np. `private final Random random = new Random()`) Lombok pomija — tak ma być.
- `@Slf4j` zamiast `private static final Logger log = LoggerFactory.getLogger(X.class)`.
- `@Getter`/`@Setter` **na polu**, nie na klasie (nigdy `@Data` na encji JPA — ryzyko z `equals`/`hashCode`/leniwym ładowaniem). Dodawaj `@Setter` tylko tam, gdzie pole ma być mutowalne z zewnątrz — kolekcje/pola bez settera (np. `Game.players`, `Game.usedAnswerCardIds`) mają zostać tylko do odczytu, mutacja przez metody domenowe.
- Metody z logiką (nie czysty pass-through) zostają ręcznie napisane — Lombok tylko na boilerplate.

## Błędy
- Zła akcja gracza → `InvalidGameActionException` z komunikatem po polsku dla użytkownika.
- Brak zasobu → dedykowany wyjątek (`GameNotFoundException`), nie `null` i nie `Optional.get()` bez sprawdzenia.
- Custom wyjątki: `InvalidGameActionException`, `GameNotFoundException` (`game/`), `InvalidRegistrationException`, `UsernameTakenException` (`user/`) — wszystkie `RuntimeException`.
- **Brak globalnego `@ControllerAdvice`** — każdy kontroler łapie swoje wyjątki lokalnie (`GameController.withErrorHandling`, `try/catch` w `JoinController`/`AuthController`), bo cel przekierowania różni się per akcja (np. `/` vs `/games/{id}` vs `/register`). Łap konkretne typy (`InvalidGameActionException`, `GameNotFoundException`), nigdy gołe `RuntimeException`/`Exception`.
- Nie łap `Exception` ogólnie. Nie połykaj wyjątków bez logu — nawet "to oczekiwane, ignoruję" dostaje `log.debug` z powodem.
- `createGame` + pierwszy `join` (założyciel) są w jednej metodzie serwisu (`GameService.createGameAndJoin`), żeby nieprawidłowy nick nie zostawiał osieroconej, pustej gry w bazie (dwa osobne wywołania = dwie transakcje = brak rollbacku między nimi).

## JPA
- `@Version` na agregatach, które edytuje kilku graczy naraz.
- Enumy `@Enumerated(STRING)`; zmiana nazwy wartości wymaga migracji danych (README → „Znana pułapka”).
- Uważaj na kaskady i `orphanRemoval` w jednym flushu z `@ElementCollection` (README / `GameServiceMultiTransactionTest`).
- Bez zapytań w pętli (N+1) — dociągaj potrzebne relacje jednym zapytaniem.

## Nazewnictwo i styl
- Kod, nazwy klas i metod **po angielsku**; komunikaty dla użytkownika i logi **po polsku**.
- Metody krótkie, nazwa mówi, co robią (`submitAnswer`, `dealUpToHandSize`). Komentarz tylko „dlaczego”, nie „co”.
- Logi: `info` dla zmian stanu gry (z kodem gry), `debug` dla szczegółów. Bez danych osobowych w logach.

## Testy
- Każda nowa reguła gry = test w `GameServiceTest`.
- Zmiany w rozdawaniu kart / kaskadach → dodatkowo `GameServiceMultiTransactionTest` (prawdziwe commity).
- Testy na H2 in-memory, nigdy na plikowej bazie.
- Przed oddaniem zmian: `./mvnw test` przechodzi.

## Bezpieczeństwo
- Każda akcja sprawdzana po stronie serwera: faza gry + rola (gracz / prowadzący / admin).
- Nigdy nie wysyłaj do widoku cudzych kart na ręce.
- Konsola H2 tylko w profilu `dev`.
