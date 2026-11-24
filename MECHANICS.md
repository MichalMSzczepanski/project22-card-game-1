# MECHANICS.md — mechanika gry

> Zasady gry z perspektywy gracza. Implementacja i stan techniczny: README sekcje 1–3. Rola admina gry: README sekcja 5.
> ⚠️ = zmiana planowana, jeszcze nie w kodzie.

## Karty
- **Karta-pytanie** — zdanie z luką albo pytanie, odczytywane przez prowadzącego.
- **Karta-odpowiedź** — krótki tekst, który gracz dopasowuje do pytania.
- Karty pochodzą z pakietów wybranych przy zakładaniu gry. Tryb rodzinny ukrywa karty 18+.

## Gracze i role
- **3–10 graczy**.
- Dołączenie **w trakcie gry** (nie tylko w lobby) jest możliwe, dopóki gra nie jest `FINISHED` — nowy gracz dostaje pełną rękę, ale siedzi z boku dokładnie jedną (tę, w której dołączył) rundę, zanim zacznie normalnie odpowiadać.
- **Prowadzący** — zmienia się co rundę, po kolei według kolejności dołączenia (wyszli gracze są pomijani). W swojej rundzie nie odpowiada i nie zdobywa punktów. Jeśli prowadzący zniknie (rozłączenie) w trakcie rundy: w fazie odpowiedzi nikt na niego nie czeka, w fazie wyboru najlepszych wynik i tak odkrywa się po timeoucie, a admin może to przyspieszyć przyciskiem „Pomiń rundę” — w każdym razie prowadzenie w kolejnej rundzie przechodzi dalej (pomijając tych, co wyszli).
- **Admin** — kto założył grę (dołączył pierwszy) automatycznie zostaje adminem; zarządza graczami (**wyrzucenie gracza**), trybem rodzinnym, zakończeniem gry, pomijaniem zawieszonej rundy i rematchem po końcu gry; tylko on klika „Start gry”. Może **dobrowolnie przekazać** rolę innej osobie bez wychodzenia z gry. Gdy ucieknie (opuści grę / zostanie uznany za rozłączonego), rola **nie przechodzi automatycznie** — gra zostaje bez admina, dopóki ktoś nie kliknie „Przejmij admina” (kto pierwszy, ten admin). Pełna specyfikacja: README sekcja 5.
- **Ekran TV** — wspólny podgląd przebiegu gry, bez akcji. W lobby pokazuje też kod QR do dołączenia (obok kodu tekstowego).

## Ręka
- Na starcie każdy dostaje **7 kart-odpowiedzi** i trzyma je **przez całą grę**.
- W każdej rundzie gracz **zrzuca dokładnie jedną** kartę — tę, którą zagrał.
- Przed kolejną rundą dobiera jedną nową — ręka zawsze wraca do 7.
- Prowadzący w swojej rundzie nic nie zrzuca.
- Zagrana karta nie wraca do talii do końca gry (brak powtórek).
- **Pusta karta** — raz na całą grę, zamiast karty z ręki gracz może zagrać własną, wpisaną odpowiedź. Na rankingu wygląda i liczy się jak każda inna — anonimowo, bez oznaczenia.
- **Wymiana ręki** — raz na całą grę gracz może zrzucić całą rękę (gdy nic nie pasuje) i dobrać nową; zrzucone karty nie wracają do talii.

## Przebieg rundy
1. **Pytanie** — prowadzący dostaje kartę-pytanie, widzą ją wszyscy (także TV).
2. **Odpowiedzi** — każdy gracz poza prowadzącym wybiera jedną kartę z ręki (albo zagra pustą kartę/wymieni rękę, patrz „Ręka”). Limit **60 s** (domyślny — twórca gry może dla całej gry ustawić inny); kto nie zdąży, zagrywa losową kartę z ręki.
3. **Wybór najlepszych** — prowadzący widzi odpowiedzi potasowane i **anonimowe**, wybiera najlepsze (patrz „Punktacja”). Limit **45 s** (domyślny, też nadpisywalny per gra).
4. **Wyniki** — odsłonięcie, kto zagrał co i ile punktów zdobył. Przejście do następnej rundy: pierwsze **30 s** może kliknąć tylko prowadzący tej rundy; jeśli nie kliknie w tym czasie, przycisk odblokowuje się dla każdego (admin zawsze może, nawet w tym oknie). Admin może też w każdej chwili w fazie odpowiedzi/wyboru kliknąć „Pomiń rundę”, żeby natychmiast odkryć wyniki bez czekania na timeout.

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
- Twórca gry wybiera przy tworzeniu gry **albo** liczbę rund (1–20) **albo**, jako alternatywę, tryb „**do X punktów**” — gra kończy się, gdy ktoś pierwszy osiągnie wybrany próg (liczba rund nadal działa pod spodem jako twardy limit bezpieczeństwa, żeby gra bez szans na próg nie trwała wiecznie). Admin może też zawsze zakończyć grę wcześniej przyciskiem „Zakończ grę”.
- Wygrywa gracz z największą liczbą punktów. Remis = współzwycięstwo.
- **„Zagraj jeszcze raz”** — po końcu gry admin może jednym kliknięciem założyć nową grę z tymi samymi (wciąż aktywnymi) graczami i pakietami, bez ponownego wpisywania kodu; otwarte karty resztki graczy same ich tam przenoszą.

## Wyjście i rozłączenie
- „Opuść grę” — natychmiastowe wyjście. Brak aktywności przez **60 s** = rozłączenie (nie dotyczy gracza, na którego właśnie czeka runda — np. zgaszony ekran telefonu w tym czasie nie wyrzuca z gry).
- Admin może też **wyrzucić** gracza ręcznie (ten może wrócić tym samym kodem — nie pamiętamy wyrzuconych).
- Gracz, który wyszedł (sam, przez timeout albo wyrzucony), wypada z kolejki prowadzących; jego zdobyte punkty i zagrane karty zostają w historii rundy.
- Gdy wyjdzie ostatni gracz, gra jest usuwana.

## Easter eggi i uatrakcyjnienia
> Zasada numer jeden: **śmieszny tekst > nowa mechanika punktowa**. To gra towarzyska, nie ranking e-sportowy — każdy dodatek ma rozśmieszyć stół, nie skomplikować liczenie punktów. Jeśli coś z poniższego trzeba wybrać jako pierwsze — wybierz tekst, nie punkty.

### Easter eggi (zdarzenie → tekst na ekranie/TV, bez wpływu na punkty) ✅ zrobione
`EasterEggService` (pakiet `game`), wołany z `GameService` po punktacji rundy / starcie rundy / końcu gry / `pruneDisconnectedPlayers`. Komunikat trafia do `Game.lastEasterEggText` (`showEasterEgg`), widoczny jako baner w `game.html`/`tv.html` (`view.easterEggText()`) na `game.timeouts.leave-banner-seconds`. Tylko jeden komunikat na raz — kolejny nadpisuje poprzedni.
- **Hat trick** — ten sam gracz wygrywa (1. miejsce podium) 3 rundy z rzędu → „🔥 {nick} jest nie do zatrzymania! Ktoś niech go/ją sprawdzi pod kątem oszustwa.” (`Player.winStreak`)
- **Zero bohater** — gracz kończy całą grę z 0 punktami → „{nick}: liczy się udział. Serio. No prawie serio.”
- **Zmartwychwstanie** — gracz na ostatnim miejscu w połowie gry (`Player.lastAtMidpoint`, zapisywane w `totalRounds/2`) kończy na 1. miejscu → „Niespodzianka! {nick} wraca z martwych i bierze wszystko.”
- **Bratnie dusze** — remis na 1. miejscu na koniec gry → „{nick} i {nick2}: tytuł do podziału. Albo do rewanżu.”
- **Sucha seria** — gracz nie zdobywa punktów w 5 rundach z rzędu (ale wciąż gra) → „{nick} zbiera materiał na stand-up o porażkach.” (`Player.scorelessStreak`)
- **Zgaszony ekran w idealnym momencie** — gracz rozłączony (`pruneDisconnectedPlayers`) akurat gdy prowadził w punktach → „{nick} zniknął w (dosłownie) najlepszym momencie.”

### Mechaniki z innych gier — co by się tu sprawdziło, a co nie
Research: Jackbox Party Pack (Thriplash — potrójne punkty w finałowej rundzie; comeback-mechaniki dla przegrywających), Kahoot (bonus za serię — **ale** ich własne doświadczenie: nieograniczony bonus za streak zniechęca tych, którzy go stracili, dlatego Kahoot ograniczył górny limit), Cards Against Humanity house rule „Rando Cardrissian” (anonimowa karta „znikąd” w rozdaniu), Trivia Party (kostka zależna od pozycji w rankingu — ostatni dostaje lepszą kostkę, czyli szansę na doganianie).

- ⬜ **Runda-bonus** co kilka rund (zapowiedziana na TV, np. „Runda 7: PODWÓJNE PUNKTY 🎲”) — prosty mnożnik, duże napięcie, inspirowane Thriplashem z Jackboxa.
- ⬜ **Finałowa runda x3** — ostatnia runda gry daje potrójne punkty za podium, dramatyczny finisz niezależnie od tego, kto prowadzi przed nią.
- ⬜ **Underdog bonus** — gracz z najmniejszą liczbą punktów w danym momencie gry dostaje +1 pkt, jeśli trafi na podium tej rundy (catch-up mechanic, nie przechyla gry, tylko wyrównuje szanse w końcówce — jak kostka d10→d4 w Trivia Party).
- ⬜ **Rando (karta znikąd)** — serwer dokłada do rankingu jedną anonimową odpowiedź z talii, od nikogo; jeśli prowadzący wybierze ją na podium, te punkty **przepadają** (nikt ich nie dostaje) — zabawne, bo czasem wygrywa karta, której nikt nie zagrał.
- ⬜ **Streak bonus — tylko jeśli ograniczony z góry** (np. +1 pkt za 2+ zwycięstwa z rzędu, bez narastania) — zgodnie z lekcją od Kahoota: nieograniczony bonus za serię demotywuje resztę stołu, a to jest gra towarzyska, nie ranking.
- Odrzucone na razie: role ukryte / głosowanie-eliminacja (Mafia/Werewolf) — zupełnie inny rodzaj gry, zbyt duża zmiana formatu pytanie-odpowiedź.

Szczegóły wdrożenia i priorytet: [`ROADMAP.md`](ROADMAP.md) → „Później / pomysły”.
