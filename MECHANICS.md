# MECHANICS.md — mechanika gry

> Zasady gry z perspektywy gracza. Implementacja i stan techniczny: README sekcje 1–3. Rola admina gry: README sekcja 5.
> ⚠️ = zmiana planowana, jeszcze nie w kodzie.

## Karty
- **Karta-pytanie** — zdanie z luką albo pytanie, odczytywane przez prowadzącego.
- **Karta-odpowiedź** — krótki tekst, który gracz dopasowuje do pytania.
- Karty pochodzą z pakietów wybranych przy zakładaniu gry. Tryb rodzinny ukrywa karty 18+.

## Gracze i role
- **3–5 graczy** (⚠️ docelowo do 10).
- **Prowadzący** — zmienia się co rundę, po kolei według kolejności dołączenia (wyszli gracze są pomijani). W swojej rundzie nie odpowiada i nie zdobywa punktów.
- **Admin gry** ⚠️ — założyciel; zarządza grą i graczami, normalnie gra (README sekcja 5).
- **Ekran TV** — wspólny podgląd przebiegu gry, bez akcji.

## Ręka
- Na starcie każdy dostaje **7 kart-odpowiedzi** i trzyma je **przez całą grę**.
- W każdej rundzie gracz **zrzuca dokładnie jedną** kartę — tę, którą zagrał.
- Przed kolejną rundą dobiera jedną nową — ręka zawsze wraca do 7.
- Prowadzący w swojej rundzie nic nie zrzuca.
- Zagrana karta nie wraca do talii do końca gry (brak powtórek).

## Przebieg rundy
1. **Pytanie** — prowadzący dostaje kartę-pytanie, widzą ją wszyscy (także TV).
2. **Odpowiedzi** — każdy gracz poza prowadzącym wybiera jedną kartę z ręki. Limit **60 s**; kto nie zdąży, zagrywa losową kartę z ręki.
3. **Wybór najlepszych** — prowadzący widzi odpowiedzi potasowane i **anonimowe**, wybiera najlepsze (patrz „Punktacja”). Limit **45 s**.
4. **Wyniki** — odsłonięcie, kto zagrał co i ile punktów zdobył. Przejście do następnej rundy klika (⚠️ tylko admin; dziś dowolny gracz).

## Punktacja
Prowadzący wybiera **tylko 3 najlepsze odpowiedzi** — podium:

| Miejsce | Punkty |
|---|---|
| 🥇 1. | **3 pkt** |
| 🥈 2. | **2 pkt** |
| 🥉 3. | **1 pkt** |
| pozostałe | 0 pkt |

- Punkty dostaje **autor** karty, nie prowadzący.
- **Mniej niż 3 odpowiedzi** (np. przy 3 graczach są tylko 2 odpowiadające osoby): prowadzący wybiera tyle miejsc, ile jest odpowiedzi — 1. miejsce 3 pkt, 2. miejsce 2 pkt.
- **Timeout wyboru**: punkty dostają tylko miejsca, które prowadzący zdążył przyznać; reszta 0.
- Każde miejsce można przyznać tylko raz; jedna odpowiedź = najwyżej jedno miejsce.

Rozmiar podium i punkty za miejsce są konfigurowalne (`game.scoring.podium-size` / `game.scoring.podium-points` w `application.yaml`, `GameProperties.Scoring`), domyślnie 3 miejsca / 3-2-1 pkt. Zaimplementowane w `Round.podiumSize()`/`Round.pointsForRank()` i `GameService.scoreRound`. Runda odkrywa się automatycznie, gdy prowadzący przyzna ostatnie wolne miejsce podium (nie czeka na pozostałe, nieobsadzone odpowiedzi).

## Koniec gry
- Po **20 rundach** (⚠️ docelowo do ustawienia przez admina: liczba rund albo „do X punktów”) albo wcześniej przyciskiem „Zakończ grę”.
- Wygrywa gracz z największą liczbą punktów. Remis = współzwycięstwo.

## Wyjście i rozłączenie
- „Opuść grę” — natychmiastowe wyjście. Brak aktywności przez **15 s** = rozłączenie (nie dotyczy gracza, na którego właśnie czeka runda).
- Gracz, który wyszedł, wypada z kolejki prowadzących; jego zdobyte punkty i zagrane karty zostają w historii rundy.
- Gdy wyjdzie ostatni gracz, gra jest usuwana.

## Planowane rozszerzenia mechaniki
Szczegóły i kolejność: README → TODO, [`ROADMAP.md`](ROADMAP.md).
- Pusta karta (raz na grę własna odpowiedź), wymiana całej ręki raz na grę, „Zagraj jeszcze raz”, głos publiczności.
