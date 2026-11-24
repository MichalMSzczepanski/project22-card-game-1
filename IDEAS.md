# IDEAS.md — pomysły (brain-dump, nie roadmapa)

> Zbiór wszystkich pomysłów rozsianych po repo (`BIZNES.md`, `MECHANICS.md`, `ROADMAP.md`, `README.md`) w jednym miejscu, plus nowe pomysły z researchu konkurencji (sekcje oznaczone 🆕). To nie jest kolejność wdrażania ani zobowiązanie — priorytety i terminy ustala [`ROADMAP.md`](ROADMAP.md), prawo/licencje [`FORMAL.md`](FORMAL.md). Plan wdrożenia (VPS, Cloudflare Tunnel, Docker, Postgres) → [`DEPLOY.md`](DEPLOY.md).
>
> Każdy pomysł ma źródło w nawiasie: `(już: plik.md)` = przeniesiony z istniejącego dokumentu, `(🆕 research)` = nowy, z analizy konkurencji.
>
> **Korekta do „bez JS" (`CLAUDE.md`):** to nie jest dosłownie zero JS w całym kodzie — `game.html` (linie 206–221) ma już dziś jeden wyjątek: przycisk „Zapisz jako obrazek" na ekranie `FINISHED`, ładujący html2canvas z CDN w małym inline `<script>`, czysto kosmetyczny eksport, zero komunikacji z serwerem, zero wpływu na logikę/polling. Wszystkie „tarcia z niezmiennikiem bez JS" niżej (licznik czasu, kolejność kart, SSE) to ten sam, już precedensowy wzorzec — izolowany skrypt kosmetyczny, nie wyjątek od zera.

## Spis treści

1. Rozgrywka — rozszerzenia mechaniki
2. Easter eggi
3. Mechaniki bonusowe / punktowe
4. Nowe kierunki z researchu konkurencji
5. Biznes / monetyzacja
6. Pakiety okolicznościowe
7. Internacjonalizacja — PL + EN
8. Panel admina aplikacji
9. Deployment i infrastruktura
10. Czysto teoretyczne: „bardziej live" i niezależność od WiFi
11. Efektywniejszy i atrakcyjniejszy frontend
12. Konkurencja — profile, promocja, wyróżnienie
13. Czego może brakować — audyt operacyjny/UX/prawny
14. Później / bez konkretnego etapu
15. Źródła

## 1. Rozgrywka — rozszerzenia mechaniki

- **Pusta karta** — raz na grę własna, wpisana odpowiedź zamiast karty z ręki; na rankingu wygląda jak każda inna *(już: README/MECHANICS)*
- **Wymiana ręki** — raz na grę zrzucenie całej ręki i dobranie 7 nowych *(już: README/MECHANICS)*
- **„Zagraj jeszcze raz"** — nowa gra z tymi samymi graczami i pakietami, bez wpisywania kodu *(już: README)*
- **Gra „do X punktów"** jako alternatywa dla liczby rund *(już: README/MECHANICS)*
- **QR kod do dołączania na ekranie TV** *(już: README/ROADMAP)* — 🆕 propozycja implementacji, dziś **brak w kodzie** (sprawdzone: brak jakiejkolwiek generacji QR, brak dedykowanego linku do dołączania — gracz zawsze wpisuje 5-znakowy kod ręcznie na `/`):
  - **Dedykowany link per gra**: nowy endpoint `GET /j/{code}` (albo `/games/join/{code}`) renderujący formularz dołączenia z kodem gry już wypełnionym/schowanym — gracz wpisuje tylko nick. Kod gry (`GameCodeGenerator`) jest już publicznym identyfikatorem, więc link nie odkrywa niczego więcej niż samo podanie kodu głośno przy stole.
  - **Generacja QR po stronie serwera, zero JS**: nowy endpoint np. `GET /games/{id}/qr.png` zwracający obrazek PNG (`image/png`) zakodowanego linku `/j/{code}` — wygenerowany raz, przy każdym żądaniu, biblioteką typu [ZXing](https://github.com/zxing/zxing) (`com.google.zxing:core`+`javase`, lekka, bez wywołań sieciowych, działa offline — zgodne z zasadą „żadnych usług pay-per-use"). Widok TV (`tv.html`) wstawia to jako zwykły `<img src="/games/{id}/qr.png">` — żadnego JS, żadnej zmiany architektury pollingu.
  - **Nie wymaga zmian w `Game`/`GameService`** — kod gry już istnieje, QR to tylko inna reprezentacja tego, co dziś trzeba przepisać ręcznie; dedykowany link do dołączenia to nowy, bardzo mały kontroler (parsowanie kodu z URL-a → ten sam formularz co `index.html`, z polem kodu `readonly`/`hidden`).
  - Koszt wdrożenia: niski — jedna nowa zależność (ZXing), jeden nowy kontroler/endpoint obrazkowy, mały fragment w `tv.html`. Dobry kandydat na "szybki win" przed v1.1.
- **Podsumowanie gry na TV** — podium, najlepsze odpowiedzi *(już: ROADMAP)*
- **Głos publiczności** — QR z TV, +1 pkt dla widzów-faworyta *(już: ROADMAP)*
- 🆕 **Tryb „zgadnij autora"** — wariant rundy inspirowany Psych!: po ujawnieniu podium gracze zgadują, kto napisał zwycięską odpowiedź, zanim się ujawni; punkt bonusowy za trafne zgadnięcie. Czysto towarzyski dodatek, nie zmienia głównej punktacji podium.
- 🆕 **Gracz układa karty na ręce w swojej kolejności** — czysto kosmetyczna personalizacja (nie wpływa na reguły gry), ale ważna dla wygody przy 7 kartach na telefonie.
  - **Stan dziś:** `Player.hand` to `@ManyToMany List<Card>` **bez `@OrderColumn`** (`Player.java:63-68`) — JPA/Hibernate nie gwarantuje stabilnej kolejności odczytu takiej listy między odpytaniami; to, co gracz widzi teraz, może się „tasować" same z siebie przy kolejnym odświeżeniu (3s meta-refresh), nie tylko po dobraniu nowej karty.
  - **Decyzja: kolejność to sprawa frontendu, nie backendu.** Backend nie powinien jej w ogóle pamiętać — zmienić `Player.hand` z `List<Card>` na `Set<Card>` (wymaga poprawnego `equals`/`hashCode` na `Card`, po `id`), żeby model szczerze odzwierciedlał „serwera kolejność nie obchodzi", zamiast trzymać listę i udawać porządek, którego JPA i tak nie gwarantuje.
  - **UI całkowicie po stronie klienta:** karty renderowane przez serwer w dowolnym porządku (z `Set`); mały, izolowany skrypt JS przy ładowaniu strony czyta z `localStorage` (klucz per `gameId`+`playerId`) zapamiętaną listę ID kart w preferowanym porządku, sortuje według niej wyrenderowane karty i przestawia węzły DOM — nowe karty (np. dobrana po rundzie), których nie ma jeszcze w zapisanej liście, dokleja na koniec. Przeciąganie / przyciski ◀▶ aktualizują tylko `localStorage` i DOM, **zero zapytań do serwera** — więc wymiana kart między rundami (zrzut + dobór) nic nie psuje, bo porządek żyje tylko w przeglądarce tego gracza.
  - **Tarcie z niezmiennikiem „bez JS":** to wymaga małego, w pełni izolowanego skryptu — nie zmienia logiki gry, nie komunikuje się z serwerem, przy wyłączonym JS karty po prostu wracają do kolejności serwerowej (degradacja bez błędu). Podobny przypadek do licznika czasu wyżej — do rozstrzygnięcia razem jako jeden wyjątek od reguły, nie dwa osobne.
  - Koszt: zmiana typu kolekcji w `Player` (mała, ale dotyka mapowania JPA — do przetestowania) + jeden mały plik JS, bez nowych endpointów i bez migracji schematu (żadnej nowej kolumny).
- 🆕 **Widoczny licznik czasu dla gracza** — ile sekund zostało na odpowiedź (`ANSWERING`, 60s) / na ranking (`RANKING`, 45s). Dziś gracz nie widzi odliczania, tylko dowiaduje się po fakcie (losowa karta przy timeoucie). Napięcie czasowe jest jedną z głównych mechanik w Jackboxie/Quizado, a u nas jest całkowicie niewidoczne dla gracza.
  - **Tarcie z niezmiennikiem „bez JS":** żywy, płynnie tykający licznik wymaga JS (`setInterval`). Da się to zrobić zgodnie z obecną architekturą (serwer liczy `Round.phaseStartedAt` + limit fazy, renderuje statyczny tekst „Masz jeszcze ~Xs" przy każdym odpytaniu co 3s meta-refresh) — mniej płynne niż JS-owy timer, ale zero złamania niezmiennika. Do rozstrzygnięcia: czy akceptujemy "skokowe" odliczanie co 3s, czy to pierwszy dobry powód na wyjątek od reguły "bez JS" (np. mały inline `<script>` tylko do lokalnego odliczania, bez zmiany logiki/stanu).
- 🆕 **Runda typu „spektrum"** — inspirowana Wavelength: prowadzący zadaje pytanie typu „oceń X w skali 0–100" (np. „jak kontrowersyjna jest ta odpowiedź"), gracze strzelają liczbą, punkty za bliskość. Większa zmiana formatu niż inne pomysły z tej listy — do przemyślenia, czy nie rozwadnia głównej mechaniki pytanie-odpowiedź.

## 2. Easter eggi (tekst na ekranie, bez wpływu na punkty)

*(już: MECHANICS.md)* — zasada numer jeden: śmieszny tekst > nowa mechanika punktowa.

- **Hat trick** — ten sam gracz wygrywa 3 rundy z rzędu
- **Zero bohater** — gracz kończy grę z 0 punktami
- **Zmartwychwstanie** — z ostatniego miejsca w połowie gry na 1. miejsce na koniec
- **Bratnie dusze** — remis na 1. miejscu na koniec gry
- **Sucha seria** — 5 rund bez punktów, ale gracz wciąż gra
- **Zgaszony ekran w idealnym momencie** — gracz rozłączony, gdy akurat prowadził w punktach

## 3. Mechaniki bonusowe / punktowe (ostrożnie — zasada: nie przechylać gry za mocno)

*(już: MECHANICS.md, research: Jackbox Thriplash, Kahoot streak, CAH „Rando Cardrissian", Trivia Party catch-up dice)*

- **Runda-bonus** co kilka rund — zapowiedziane podwójne punkty (inspirowane Jackbox Thriplash)
- **Finałowa runda x3** — ostatnia runda daje potrójne punkty za podium
- **Underdog bonus** — gracz z najmniejszą liczbą punktów dostaje +1 pkt, jeśli trafi na podium (catch-up mechanic, jak kostka d10→d4 w Trivia Party)
- **Rando (karta znikąd)** — serwer dokłada anonimową odpowiedź z talii; jej punkty przepadają, jeśli wygra
- **Streak bonus — tylko ograniczony z góry** — lekcja z Kahoota: nielimitowany bonus za serię demotywuje resztę stołu
- 🆕 **Specjalna karta „odbierz punkty"** — jednorazowy efekt odejmujący punkty innemu graczowi. **Ryzyko projektowe, nie tylko punktowe:** to jest mechanika zero-sum/atakująca, nie catch-up jak underdog bonus czy streak cap wyżej — w grze towarzyskiej grywanej z przyjaciółmi przy stole, bezpośrednie „zabranie" punktów komuś może wywołać złe emocje, nie śmiech (inny rodzaj interakcji niż wszystko inne w tej sekcji, które *wyrównuje* szanse, nie *odbiera* je). Do rozważenia złagodzenie: mały, ograniczony efekt (np. -1 pkt, nie odebranie całego wyniku), możliwość odwetu/kontry, albo rzadkość (raz na grę, nie na rundę) — podobnie jak „pusta karta"/„wymiana ręki" z sekcji 1, które są rzadkie i jednorazowe, nie powtarzalne. Jeśli wdrażane — powiązać z pomysłem na karty specjalne/walutę w sekcji 5, nie z podstawową punktacją podium.
- Odrzucone: role ukryte / eliminacja (Mafia/Werewolf) — zbyt duża zmiana formatu

## 4. 🆕 Nowe kierunki z researchu konkurencji

Przegląd: Jackbox Party Pack (Fibbage, Quiplash, Drawful, YDKJ, Trivia Murder Party), Psych!, Evil Apples, Cards Against Originality, Massive Decks, Quizado, Wavelength, Spyfall, Monikers, Codenames.

- **Fibbage-style pytanie** — prowadzący/serwer zadaje pytanie z prawdziwą, zaskakującą odpowiedzią; gracze zmyślają swoją wersję, potem wszyscy głosują, która jest prawdziwa. Inny rodzaj napięcia niż podium (bluffing, nie tylko humor) — można rozważyć jako osobny typ pytania/pakiet, nie zamiennik głównej mechaniki.
- **Drawful-style minigame** — jeden gracz rysuje (na telefonie, bez JS to spory problem technologiczny — wymagałoby przełamania niezmiennika „bez JS"), reszta zgaduje. Odnotowane jako pomysł, ale koliduje z obecnym stackiem (meta-refresh, zero JS) — duży koszt wdrożenia.
- **Quiplash-style głosowanie publiczności** — gdy graczy jest więcej niż potrzeba do rundy (np. przy 10-osobowym stole), reszta/widzowie głosują na lepszą z dwóch odpowiedzi zamiast czekać bezczynnie — pokrywa się z już zapisanym „głosem publiczności".
- **Catch-up dice (Trivia Murder Party)** — potwierdza sens „underdog bonus" z sekcji 3, inna implementacja: zamiast bonusu punktowego, ostatni w rankingu dostaje w swojej turze dodatkową (lepszą) kartę-odpowiedź do wyboru.
- **Model bez apki, tylko kod + przeglądarka (Jackbox, Quizado)** — potwierdza nasz obecny kierunek (Thymeleaf + kod gry, bez instalacji). Quizado idzie dalej: QR kod na TV bez wpisywania kodu ręcznie — zgodne z już zapisanym TODO.
- **Massive Decks (open-source CAH clone, web, bez logowania)** — bliski model do naszego etapu 1 (brak kont, darmowe); warto sprawdzić ich sposób hostowania/kosztów jako punkt odniesienia dla VPS budżetu.
- **Evil Apples / Psych! — IAP za pakiety kart** — potwierdza nasz plan freemium (płatne zestawy tematyczne jako IAP, nie subskrypcja na start).

## 5. Biznes / monetyzacja

*(już: BIZNES.md)*

- **Pakiet admina działa dla całego stołu** — kupiony pakiet admina odblokowuje karty dla wszystkich graczy w jego grze; widoczne „Pakiet X dzięki uprzejmości {nick}" + link do sklepu — reszta widzi płatne karty w akcji
- **Zgłoś kartę** — oznaczanie słabych/niezrozumiałych/obraźliwych kart, kolejka do przeglądu
- **Statystyki kart** — które odpowiedzi najczęściej wygrywają/przegrywają, które pytania dają najwięcej zabawy

🆕 Dodatkowe pomysły z researchu modeli monetyzacji gier towarzyskich/casualowych:
- **Rewarded ads jako opcja w etapie 1** — „obejrzyj reklamę, odblokuj pakiet na tę jedną grę" zamiast zakupu; do rozważenia dopiero gdy reklamy nie kolidują z zasadą „etap 1 = zero kosztów-niespodzianek, minimalne dane" z `FORMAL.md` (reklamy ad-tech zwykle = tracking = RODO) — **wymaga konsultacji prawnej przed wdrożeniem, nie etap 1**.
- **Mikro-pakiety sezonowe bundlowane z większym zestawem** — wzorem „battle pass"/season content w innych grach casualowych; pasuje do istniejącego pomysłu pakietów okolicznościowych (np. dodatkowe 10 kart do „Wesela" jako mini-dodatek).
- Model jednorazowy vs. subskrypcja „wszystkie zestawy" — już w BIZNES.md „Do decyzji", potwierdzone jako standardowa alternatywa w branży (freemium + hybrid monetization jest normą rynkową).

### 🆕 Waluta wewnętrzna → karty specjalne (bezpieczna alternatywa do odrzuconego pomysłu z `FORMAL.md`)

Z dyskusji o graniu o prawdziwe pieniądze (odrzucone, ryzyko hazardu — patrz `FORMAL.md`): bezpieczny wariant to waluta **jednostronna, bez cash-outu** — kupowana za PLN i/lub zdobywana przez granie, wydawana wyłącznie w appce na **karty specjalne** (nowy typ karty, nie `QUESTION`/`ANSWER`) z jednorazowymi efektami w rundzie, np. efekt z sekcji 3 („odbierz punkty").

- **Dlaczego to prawdopodobnie jest bezpieczne:** nic, co „wygrywasz", nie ma wartości poza appką — nie ma nagrody pieniężnej/rzeczowej w rozumieniu ustawy hazardowej, bo nie da się tego spienić ani wymienić. To inny produkt niż oryginalny pomysł (gracze nie wygrywają realnych pieniędzy), bliżej mikro-pakietów/battle-passa wyżej niż hazardu — ale patrz zastrzeżenie w `FORMAL.md`: to wciąż nie jest porada prawna, warto to też wspomnieć przy konsultacji prawnej etapu 2, nie zakładać z góry, że „waluta = bezpieczne".
- **Nowe ryzyko: pay-to-win w grze towarzyskiej.** Zwykłe karty dziś są czysto humorystyczne (treść), nie wpływają na przewagę w grze poza samą odpowiedzią. Karty specjalne kupowane za prawdziwe pieniądze (przez walutę) **wpływają na rozgrywkę** (np. odbierają komuś punkty) — to jest pay-to-win, inny rodzaj problemu niż prawny: w grze towarzyskiej z przyjaciółmi przy stole, ktoś płacący za przewagę nad kolegami może być odczuwany jako nie fair, niezależnie od legalności. Do rozważenia: karty specjalne zdobywane tylko przez granie (darmowe, czasowe/losowe), nie kupowane za prawdziwe pieniądze — albo dostępne dla każdego w grze przez admina (jak „pakiet admina działa dla całego stołu" wyżej), nie per-gracz.
- Jeśli wdrażane: nowy `CardType` (np. `SPECIAL`) albo osobna encja efektu, rozdawana/kupowana osobno od zwykłej ręki 7 kart — nie miesza się z istniejącą mechaniką zrzut-dobór z README sekcji 1.

### 🆕 Obsługa płatności — gotowy dostawca, nie pisać tego samemu

*(rozwinięcie README TODO „Płatności": „Etap 2: prawdziwy operator płatności zamiast stubu". Dziś `PacksController.purchase` to czysty stub — odblokowuje pakiet bez żadnej realnej płatności, `log.info("Zakup (symulowany)...")` — zgodnie z etapem 1. Pełna spec wdrożenia: [`README.md`](README.md) sekcja 6.)*

**Shopify prawdopodobnie nie jest dobrym wyborem tutaj.** To platforma pod sklep/katalog produktów (fizycznych albo prosto-cyfrowych do ściągnięcia), nie pod odblokowywanie treści w istniejącej, własnej logice konta gracza. Nawet z Shopify i tak trzeba by samemu napisać webhook + sprawdzanie entitlementu po stronie naszej appki (Shopify domyślnie tylko zmienia status zamówienia na „Paid" — nic nie odblokowuje automatycznie) — czyli ta sama praca integracyjna co z każdym innym dostawcą, plus dodatkowy miesięczny koszt samego Shopify i osobny katalog produktów do utrzymania równolegle z `CardPack`.

**Lepszy fit: dostawca z hostowaną stroną płatności (checkout) + webhook, bez Shopify.** Dwie realne ścieżki:
- **Stripe Checkout** — my zostajemy sprzedawcą (merchant of record): sami odpowiadamy za VAT/fakturowanie (Stripe Tax pomaga, ale odpowiedzialność jest nasza). Niższa opłata (~2.9% + 30¢/transakcję).
- **Paddle** (merchant of record) — **Paddle jest prawnie sprzedawcą**, sam rozlicza i odprowadza VAT w 200+ jurysdykcjach, sam wystawia faktury klientom. Wyższa opłata (~5% + 50¢/transakcję), ale zdejmuje z nas obowiązek rozliczania VAT jako jednoosobowej/małej działalności. *(Uwaga: Lemon Squeezy — podobny model — został przejęty przez Stripe w 2024 i jest w trakcie łączenia z „Stripe Managed Payments", więc dziś to efektywnie dwie niezależne opcje: Stripe i Paddle, nie trzy.)*
- **Dlaczego to ma znaczenie przy naszej anonimowości (FORMAL.md):** merchant-of-record (Paddle) zdejmuje z nas prowadzenie rozliczeń VAT z klientami jako widoczny sprzedawca — ale **to nie zwalnia** z założenia własnej działalności/spółki w etapie 2 (FORMAL.md jest jasne: przy sprzedaży anonimowość się kończy, spółka jest jawna). To raczej mniej papierkowej roboty księgowej niż mniej jawności. **Finalna decyzja wymaga konsultacji prawnej/księgowej przed etapem 2** — zgodnie z notatką na górze `FORMAL.md`, to nie jest porada prawna.

**Jak by to wpasować w istniejący kod (bez przepisywania architektury):**
1. `PacksController.purchase` zamiast odblokowywać pakiet od razu, tworzy hostowaną sesję płatności (Stripe Checkout Session / Paddle Checkout) z metadanymi `appUserId` + `packId` i przekierowuje gracza tam — **karta nigdy nie trafia na nasz serwer** (PCI compliance zostaje po stronie dostawcy, zero dodatkowego ryzyka/obowiązków dla nas).
2. Nowy endpoint, np. `POST /webhooks/payments` — dostawca wywołuje go po udanej płatności; weryfikacja podpisu (HMAC, sekret z env var, nigdy w repo) jak dziś planowane dla innych sekretów (README TODO „Anonimowość"/[`DEPLOY.md`](DEPLOY.md)); dopiero wtedy `Purchase` dostaje status `COMPLETED` i pakiet się odblokowuje.
3. **Zgodne z już zaplanowanym deploymentem** — webhook to zwykły ruch HTTPS na nasz publiczny hostname przez Cloudflare Tunnel, nie wymaga żadnego otwartego portu na VPS; żadnej zmiany w modelu „deny all inbound" (patrz `DEPLOY.md`).
4. Refundy/chargebacki też przychodzą jako webhooki — obsłużyć jako unieważnienie `Purchase`.
5. `Purchase` nie potrzebuje i nie powinien nigdy przechowywać danych karty — tylko ID transakcji dostawcy, status, `packId`, `appUserId`.

## 6. Pakiety okolicznościowe (18+, płatne)

*(już: BIZNES.md)* — każdy w 3 wersjach pokoleniowych (Zetki / Milenialsi / Boomersi): Wieczór panieński, Wieczór kawalerski, Wesele + poprawiny, Pępkowe, Osiemnastka, Okrągłe urodziny, Sylwester, Firmowa impreza/integracja, Domówka/parapetówka, Grill/majówka/działka, Andrzejki, Studenckie, Mecz/wieczór kibica, Rocznica/jubileusz.

🆕 Dodatkowe propozycje tematów z obserwacji popularnych gier quizowych/towarzyskich (pamiętając o zasadzie z `FORMAL.md`: wszystkie karty własne, zero skopiowanych treści):
- **Serialowa/filmowa** — odniesienia do popularnych seriali/filmów (ryzyko: prawa autorskie do nazw — do konsultacji prawnej, nie kopiować cytatów)
- **Muzyczna** — pytania o teksty/klimat piosenek (podobne ryzyko praw autorskich jak wyżej)
- Obie pozycje wymagają większej ostrożności prawnej niż pakiety „sytuacyjne" (wesele, impreza) — niższy priorytet, do rozważenia dopiero w etapie 2 po konsultacji z `FORMAL.md`.

## 7. 🆕 Internacjonalizacja — PL + EN

> Dotyka wielu sekcji naraz (1. Rozgrywka — treści kart, 5. Biznes — kto pisze EN treści, 6. Pakiety — które mają sens po angielsku), więc osobna sekcja zamiast wpisywania w każdą z nich. **Dwa zupełnie różne tory pracy, nie jedno zadanie.**

### Tor 1 — UI (przyciski, etykiety, komunikaty błędów): czysto techniczne, względnie małe

- **`MessageSource`** (Spring Boot auto-konfiguruje `ResourceBundleMessageSource`) + pliki `messages.properties` (domyślny/PL) i `messages_en.properties`. W Thymeleaf: zamiana hardkodowanego tekstu na `#{klucz}`.
- **Sprawdzone w kodzie:** żaden z 7 szablonów (`index.html`, `game.html`, `tv.html`, `packs.html`, `login.html`, `register.html`, `stats.html`) nie używa dziś fragmentów/layoutu (`th:fragment`, `layout:decorate`) — każdy jest samodzielnym plikiem z wklejonym polskim tekstem. To dobry moment, żeby wprowadzić wspólny fragment (choćby pod przełącznik języka), zamiast kopiować go 7 razy.
- **Komunikaty błędów są trudniejsze niż etykiety** — `InvalidGameActionException` dziś niesie hardkodowany polski string (np. „Tylko admin może to zrobić.", „Prowadzący nie odpowiada w tej rundzie.") throw'owany prosto w `GameService`/kontrolerach. Żeby to przetłumaczyć: wyjątek musi nieść **klucz** (+ argumenty), a tłumaczenie klucz→tekst przez `MessageSource` dzieje się w `GameController.withErrorHandling` (miejsce renderowania), nie w miejscu `throw`. To dotyka każdego miejsca z `throw new InvalidGameActionException("...")` w całym `GameService` — realny refaktor, nie samo dodanie plików properties.
- **Logi `GameService` (po polsku, z kodem gry, README sekcja 3) — nie tłumaczyć.** To wewnętrzne dev-logi do grepowania przy diagnozie, niezależne od języka UI.
- **Wybór języka bez JS, bez kont:** `CookieLocaleResolver` + `LocaleChangeInterceptor` (standardowy mechanizm Springa — zwykły `GET ?lang=en`/`?lang=pl`, zero JS). To wybór **per przeglądarka/urządzenie**, nie per gra — każdy gracz przy stole może mieć inny język UI na swoim telefonie, niezależnie od innych i od TV. Nie łamie niezmiennika „cały stan gry w bazie" (`CLAUDE.md`) — to kosmetyka urządzenia, nie stan gry, ten sam wzorzec co lokalny wybór kolejności kart (sekcja 1 wyżej).

### Tor 2 — treść kart: to NIE jest tłumaczenie, to pisanie drugiego zestawu kart

- **`CardSeeder` hardkoduje polskie teksty w Javie** — przetłumaczenie UI (tor 1) w ogóle tego nie rusza, to osobna, dużo większa skala pracy.
- **Humor się nie tłumaczy 1:1.** `FORMAL.md` wymaga własnych treści (nie kopiowanych z innych gier) — żart/pytanie przetłumaczone literalnie z PL na EN często traci sens albo komizm. W praktyce to **pisanie drugiego, oryginalnego zestawu kart po angielsku**, nie przepuszczenie istniejących przez tłumacza.
- **Pakiety „sytuacyjne" polskie** (Andrzejki, Pępkowe, Sylwestrowa, Studenckie) są zakorzenione kulturowo — niejasne, kto jest odbiorcą wersji angielskiej (diaspora? ekspaci w Polsce? międzynarodowa publiczność bez kontekstu?). Pakiety generyczne/rodzinne mają dużo bardziej oczywisty sens do zrobienia w dwóch językach od razu.
- **Model danych:** karty muszą nieść język gry (np. `Card.language` enum `PL`/`EN`, albo osobne `CardPack`y per język — „Generyczny (PL)"/„Generyczny (EN)") filtrowane wg **języka gry** ustalonego przy tworzeniu przez hosta, **nie** wg języka przeglądarki pojedynczego gracza — wszyscy przy stole czytają te same karty, muszą być w jednym języku niezależnie od UI chrome każdego z nich. To osobny atrybut od lokalnego wyboru UI z toru 1, nie ten sam mechanizm.
- To decyzja produktowo-biznesowa (które pakiety tłumaczyć, kto pisze angielskie teksty — `BIZNES.md`/`FORMAL.md`), nie czysto techniczna.

### Podsumowanie
Tor 1 (UI) jest realny do zrobienia w etapie 1 — mały, czysto techniczny refaktor. Tor 2 (treść kart) to w praktyce nowy, równoległy zestaw kart pisany od zera po angielsku — większe pytanie produktowe (dla kogo, które pakiety) niż techniczne, raczej etap 2+, nie „przy okazji".

## 8. Panel admina aplikacji (operator serwisu) — rozwinięcie

*(już: README TODO sekcja „Panel admina aplikacji" — tu rozwinięte o konkretne propozycje implementacji, bo w kodzie dziś nie ma żadnego fragmentu tego panelu: sprawdzone, brak `/admin`, brak roli w `AppUser`, brak zapytań agregujących)*

### Dostęp i autoryzacja — operator to nie gracz, osobne konto od zera

> Decyzja (ważna korekta wcześniejszej wersji tego pomysłu): **operator serwisu nie jest graczem i nie loguje się przez ten sam mechanizm co gracze.** Panel admina aplikacji nie ma nic wspólnego z `AppUser` (kontami graczy — bcrypt + opcjonalny OAuth, i tak znikającymi w etapie 1) ani z „adminem gry" (README sekcja 5, rola *w rozgrywce*, rotująca, bez logowania). To trzecia, całkiem osobna tożsamość.

- **Nowa, osobna encja** (np. `Operator` / `AdminUser`) z własną tabelą, własnym hashem (bcrypt) — zero związku z `AppUserRepository`. Dzięki temu wyłączenie rejestracji/logowania/OAuth graczy w etapie 1 (README TODO) **nie dotyka** w żaden sposób dostępu operatora — to naprawdę dwa niezależne systemy, nie jeden z flagą roli.
- **Konta operatora tworzone wyłącznie ręcznie** — nigdy przez formularz rejestracji. Np. jednorazowy skrypt startowy/CLI czytający hasło z env var przy pierwszym uruchomieniu, albo bezpośredni insert do bazy. Brak jakiegokolwiek publicznego punktu wejścia, który tworzyłby konto operatora.
- **Osobny punkt logowania** — `/admin/login`, trzeci filter chain w `SecurityConfig` (obok istniejących `h2ConsoleFilterChain` i `appFilterChain`), z własnym `UserDetailsService` wskazującym na nową tabelę operatorów, `securityMatcher("/admin/**")`. Gracz logujący się przez `/login` nie ma i nie może mieć dostępu do `/admin/**`, niezależnie od tego, czy ma konto gracza — te dwie tabele się nie przecinają.
- **Cloudflare Access jako dodatkowa, niezależna warstwa przed appką** (patrz `DEPLOY.md`) — nawet błąd w logice `/admin/**` nie wystarcza do wejścia, bo Cloudflare blokuje request *przed* dotarciem do aplikacji. Darmowe w Cloudflare Free.

### Zarządzanie — CRUD/CMS dla operatora (nie tylko podglądy)

- **Gracze**: lista graczy w aktywnych/historycznych grach (nick, `lastSeenAt`, punkty), akcja „wyrzuć z gry" z panelu (ta sama ścieżka co dziś `left=true`), docelowo blokada po nicku/IP (moderacja, patrz niżej).
- **Gry**: podgląd stanu żywej gry (faza, runda, gracze), akcje zakończ/usuń/wymuś następną rundę, „sprzątnij teraz" porzucone.
- **Karty — pełny CRUD, nie tylko CSV import**: dodaj, edytuj, usuń, **kopiuj** (duplikuj istniejącą kartę jako punkt startowy do napisania podobnej — przydatne, gdy piszesz dużo wariantów jednego typu pytania), masowy import z CSV, docelowo zastępuje hardkod w `CardSeeder`.
- **Zestawy (`CardPack`) — budowanie, nie tylko przypisanie**: tworzenie nowego pakietu, przypisywanie/odpinanie kart do pakietu (wiele-do-wielu — karta może trafić do kilku zestawów), oznaczenie `PackAudience` (ADULT/rodzinne), daty aktywności sezonowej (jak dziś „Sylwestrowa"), włącz/wyłącz pakiet bez usuwania kart. Przy budowie nowego zestawu okolicznościowego (np. „Wesele — Milenialsi") przydatne jest **kopiowanie kart między zestawami** jako punkt startowy (patrz wyżej) — jedna operacja CRUD obsługuje oba przypadki.
- **Nadal aktualna zależność:** CRUD kart/zestawów w panelu **nie ma sensu, dopóki `ddl-auto=create-drop` czyści bazę przy każdym restarcie** — każda ręczna zmiana operatora zostałaby nadpisana przez `CardSeeder` przy najbliższym restarcie/deployu. Panel CRUD ma realny sens dopiero po przejściu na Postgres + Flyway (README TODO „Dane", `DEPLOY.md` sekcja 5).

### Co da się zrobić już teraz vs. co czeka na Postgres

- **Dashboard i „gry na żywo"** — da się zrobić już dziś, bez żadnej nowej infrastruktury: proste zapytania agregujące na istniejących `GameRepository`/`PlayerRepository` (`countByStatus`, filtrowanie po `lastSeenAt`), czysto odczytowe, zero nowych tabel.
- **Karty i pakiety — CRUD w panelu nie ma sensu, dopóki baza czyści się przy starcie.** Dziś `CardSeeder` (`ApplicationRunner`) ładuje karty raz przy każdym starcie, a `ddl-auto=create-drop` i tak czyści bazę przy restarcie — więc każda zmiana zrobiona ręcznie w panelu zostałaby nadpisana przy najbliższym restarcie/deployu. **Zależność, nie tylko „miło by było":** CRUD kart ma sens dopiero po przejściu na Postgres + Flyway, inaczej to praca do wyrzucenia.
- **Zdrowie systemu** — naturalny fit to **Spring Boot Actuator** (`spring-boot-starter-actuator`, dziś nieobecny w `pom.xml`) zamiast pisać monitoring od zera: `/actuator/health`, `/actuator/metrics` pod tym samym `ROLE_ADMIN` + Cloudflare Access.
- **Log akcji operatora** — nie wymaga nowej tabeli na start: structured logging z tagiem (`log.info("ADMIN_ACTION ...")`), ten sam wzorzec co już istniejące logi `GameService` po polsku z kodem gry (README sekcja 3) — pierwsze miejsce do grep'owania, bez budowania UI do przeglądania logów w MVP.
- **Moderacja (blokada IP)** — wymaga najpierw znajomości prawdziwego IP klienta za Cloudflare (`CF-Connecting-IP`) — patrz `DEPLOY.md` sekcja 3, to jest wspólna zależność z rate limitingiem, warto zrobić razem.

### Pozostałe punkty bez zmian

- Gry na żywo: akcje zakończ/usuń/sprzątnij
- Zgłoszone karty: kolejka zgłoszeń
- Statystyki kart, ranking „Chcę ten zestaw!"
- Ustawienia serwisu bez redeployu: tryb serwisowy, baner ogłoszeń, limit aktywnych gier, feature flags

## 9. Deployment i infrastruktura

> Pełny plan — krok po kroku VPS + Cloudflare Tunnel na obecną wersję, rate limiting, decyzja o Dockerze, migracja na Postgres, sesja gracza (Postgres vs Redis), limity zasobów — przeniesiony do [`DEPLOY.md`](DEPLOY.md), żeby nie duplikować treści w dwóch plikach. To jedyny temat w projekcie, który miesza „gotowe do zrobienia teraz" z „decyzje do podjęcia" pod jedną etykietą (deployment), bo z natury jest to plan operacyjny, nie czysty brainstorm ani zdecydowana spec kodu — stąd osobny plik, nie kolejna sekcja tutaj.

## 10. 🆕 Czysto teoretyczne: „bardziej live" i niezależność od WiFi

> Kontekst decyzji: polling (bez WebSocketów) został odrzucony **na starcie projektu świadomie**, żeby mieć działające MVP jak najszybciej i skupić się na problemach biznesowych i mechanice gry, nie na infrastrukturze real-time. To, co niżej, jest eksploracją „co by było, gdyby", nie propozycją do wdrożenia — `CLAUDE.md` wciąż mówi „Komunikacja zostaje polling (bez WebSocketów)".

### Niezależność od WiFi — to rozwiązuje deployment, nie protokół

Dzisiejsza architektura **nigdy nie była technicznie WiFi-specyficzna** — serwer rozmawia z telefonami zwykłym HTTP request/response, zero lokalnego discovery (mDNS, UDP broadcast, Bluetooth). „Ta sama sieć Wi-Fi" z pierwszej linijki README to opis *typowego use case* (impreza, jeden pokój), nie konstrukcja protokołu.

Realny dzisiejszy powód ograniczenia: **serwer działa na czyimś laptopie w LAN** (`./mvnw spring-boot:run` lokalnie) — telefony muszą być w tej samej sieci, bo inaczej nie mają jak dobić do prywatnego IP tego laptopa. To jest problem **deploymentu**, nie protokołu komunikacji. Gdy serwer trafi na VPS z publicznym hostname (konkretny plan: [`DEPLOY.md`](DEPLOY.md)), ograniczenie zniknie **za darmo, bez zmiany logiki gry** — telefon na WiFi i telefon na LTE trafiają do tego samego publicznego adresu identycznie. Nie trzeba nowego protokołu, tylko wdrożyć to, co już jest w planie.

*(Odwrotny scenariusz — gra bez internetu w ogóle, np. impreza bez zasięgu — wymagałby, żeby telefon hosta sam był serwerem, a reszta łączyła się przez WiFi Direct/Bluetooth/lokalny hotspot. To wymaga natywnej apki z dostępem do radiowych API telefonu, nie przeglądarki — całkowicie inny produkt, nie tweak obecnej architektury.)*

### „Bardziej live" — opcje od najbliższej do najdalszej obecnym niezmiennikom

Dziś lag to do 3s (interwał meta-refresh) + czas renderu.

| Podejście | Jak działa | Koszt względem niezmienników |
|---|---|---|
| **Krótszy interwał** (np. 1s) | Zero zmian architektury | Zero złamania niczego, ale ~3× więcej requestów/s na grę — prosto w limit rate-limitingu (`DEPLOY.md` sekcja 3) |
| **Server-Sent Events (SSE)** | Jeden strumień HTTP, serwer wysyła event gdy stan się zmienia | Wymaga JS (`EventSource`) do nasłuchu — ale skrypt może robić tylko `location.reload()`, nie dotykać logiki; mały, izolowany wyjątek jak te już zapisane w sekcji 1 (licznik czasu, kolejność kart) |
| **Long polling po stronie serwera (`DeferredResult` w Spring MVC)** | Request **czeka** na serwerze, aż `GameService` zmieni stan (albo timeout ~25s), dopiero wtedy zwraca HTML; strona ma meta-refresh 0s | **Teoretycznie zero JS** — ten sam model „serwer renderuje całą stronę", tylko odpowiedź przychodzi *gdy coś się zmieni*, nie na sztywny zegar. Koszt: wewnętrzny mechanizm „obudź czekające requesty tej gry" (in-memory pub-sub po `gameId`, sygnał, nie stan — stan gry wciąż w 100% w bazie); sprawdzić limit timeoutu Cloudflare Tunnel na długo trzymane połączenia |
| **WebSockets** | Prawdziwy push, dwustronny, najniższy lag | **Już explicite odrzucone w `CLAUDE.md`** — największy koszt: trzymanie połączenia per klient, sticky sessions przy skalowaniu, własny mechanizm notyfikacji stanu. Najbardziej „live", ale burzy większość dzisiejszych decyzji architektonicznych naraz |

Najciekawsza teoretycznie opcja bez łamania „bez JS": long polling przez `DeferredResult` — „czekanie" przenosi się z klienta (pusty sen 3s) na serwer (realne czekanie na realną zmianę). Nieoczywisty koszt: trzeba zaprojektować, *co* dokładnie budzi czekający request — każda akcja w `GameService`, która zmienia `Game`/`Round`, musiałaby też „strzelić" sygnał do wszystkich aktywnych long-poll requestów tej gry.

## 11. 🆕 Efektywniejszy i atrakcyjniejszy frontend — droga do PWA (native to daleka przyszłość)

> Dwie różne osie, dwa różne rozwiązania — „atrakcyjny wizualnie" i „efektywniejszy" to nie jeden problem.

- **Wizualna atrakcyjność** — w ogromnej mierze czysta robota CSS/brandingowa, zero ryzyka architektonicznego. Sprawdzone w kodzie: dziś frontend to **Bootstrap 5.3.3 z CDN** (`index.html:7`), generyczny, bez własnego theme'u — a `FORMAL.md` już wymaga własnej kolorystyki/fontu (nie czarno-białe jak CAH). Da się to zrobić niezależnie od wszystkiego innego poniżej, od razu.
- **„Efektywniejszy"** — to realnie oznacza koniec z widocznym mignięciem przy każdym full-page-reload co 3s (meta-refresh odświeża całą stronę, nie tylko to, co się zmieniło) — problem perceived-performance/UX, nie surowej przepustowości (statyczne assety są i tak cache'owane przez przeglądarkę między reloadami).

### Spektrum opcji, od najbliższej do najdalszej

| Krok | Co to daje | Koszt/ryzyko |
|---|---|---|
| **0. Polish CSS/brand** | Ładniejszy wygląd, własna kolorystyka/font — zero zmian architektury | Brak — czysty frontend, zgodne z `FORMAL.md` TODO |
| **1. PWA shell** (manifest.json + minimalny service worker) | Ikona na ekranie głównym, pełny ekran bez chrome przeglądarki, splash screen — „czuje się jak apka" bez pisania apki | Mały, izolowany JS (rejestracja SW) — ten sam precedens co `html2canvas` w `game.html` (patrz korekta na górze tego pliku). **Ważne:** SW musi cache'ować **tylko statyczne assety** (CSS/fonty/ikony), nigdy `/games/**` — inaczej gracz zobaczy nieaktualny stan gry z cache |
| **2. htmx-style partial swaps** (zamiast pełnego reloadu co 3s) | Zabija mignięcie — serwer wciąż renderuje HTML (te same szablony Thymeleaf), wymieniany jest tylko fragment, który się zmienił | Jeden mały `<script>` z CDN (htmx), zero build stepu, zero bundlera. **Zgodne z `CODING.md`** („widok dostaje gotowy model, szablon niczego nie liczy") — htmx nie przenosi logiki na klienta, tylko zmienia mechanikę odświeżania |
| **3. Full SPA** (React/Vue/Svelte + JSON API) | Najbardziej „efektywne" i elastyczne wizualnie | Duży rewrite: backend dorabia REST/JSON API równolegle do Thymeleaf, osobny frontend z build toolingiem — i to **łamie** „szablon niczego nie liczy" z `CODING.md`. Naturalny prekursor natywnej apki |
| **4. Native (Capacitor/React Native)** | Daleka przyszłość, jak już zdecydowano | Najlepiej budować **na bazie** kroku 3 (Capacitor owija istniejącą SPA/PWA) — jeśli kiedyś tam dojdzie, kroki 3 i 4 robią się razem, nie osobno |

### Rekomendowana sekwencja

**0 → 1 → 2**, zatrzymać się i ocenić, zanim **3**. Kroki 0-2 nie wymagają przepisywania backendu, nie łamią żadnej zasady z `CODING.md`, każdy jest małym, odwracalnym krokiem. SPA (krok 3) to ten sam rząd inwestycji co natywna apka — skoro native to „daleka przyszłość", SPA prawdopodobnie też powinno czekać, bo to właśnie SPA/PWA byłoby tym, co potem owija się w Capacitor, nie coś robione osobno teraz.

## 12. 🆕 Konkurencja — profile, jak się promowały, jak się wyróżniamy

> Uwaga co do TV: **u nas ekran TV jest opcjonalnym dodatkowym widokiem** (`GET /games/{id}/tv`), nie wymogiem — każdy gracz dostaje pytanie, odpowiedzi i wyniki na własnym telefonie niezależnie od tego, czy ktoś włączył TV. To zbliża nas do Massive Decks (centralny ekran też opcjonalny), nie oddala.

### Quizado — B2B SaaS dla barów/lokali (bar trivia)

- **Co to:** platforma do quizów imprezowych w przeglądarce; gracze dołączają kodem QR z telefonu, bez instalacji. Prowadzący (zwykle właściciel/pracownik lokalu) generuje pytania (nawet AI), wyświetla wyniki live na dużym ekranie. 6 typów rund.
- **Model:** SaaS — płaci **host/lokal** (stały abonament miesięczny + darmowy plan startowy), nie gracze. To płatność za *funkcję organizatora*, nie za treść/karty.
- **Jak się promowali (potwierdzone z ich bloga/stron):** rozbudowana **programatyczna SEO** — dziesiątki landing page'y typu „Quizado vs Crowdpurr", „Kahoot alternative for bars", „SpeedQuizzing alternative" (przechwytywanie ruchu z wyszukiwań porównujących konkurentów) + content marketing (blog o tym, jak quizy zwiększają frekwencję w barach) + zachęta B2B2C: polecają lokalom program poleceń dla stałych gości („darmowe wejście za przyniesienie nowej drużyny") — czyli marketing kierowany do *właścicieli lokali*, nie do grających.
- **Nieweryfikowalne:** liczby typu „+30% frekwencji" to własne claimy marketingowe Quizado, bez niezależnego potwierdzenia.

### Massive Decks — darmowy, open-source klon CAH

- **Co to:** gra w tym samym pokoju lub online, dowolne urządzenie z przeglądarką, centralny ekran opcjonalny. Własne talie (Many Decks), house rules, boty AI dobijające stół, obserwatorzy.
- **Model:** **brak** — projekt społecznościowy (Reread Games), AGPLv3, zero zakupów/reklam, samodzielny hosting (Docker).
- **Jak się promował:** **zero budżetu marketingowego** — czysty organiczny word-of-mouth. Pojawia się na community-curowanych listach typu „darmowe alternatywy dla CAH online" na Reddicie (np. `r/CardsAgainstHumanity`, lista: Massive Decks, Bad Cards, Cards Against Formality, Picturecards Online) — czyli dystrybucja przez to, że ludzie sami polecają go sobie, gdy oryginalna płatna/oficjalna wersja online CAH zniknęła.

### Evil Apples — komercyjny klon CAH na iOS/Android

- **Co to:** klasyczny CAH mobilnie, sędzia rotuje, waluta „Cake" za pakiety kart.
- **Model:** hybrydowy — **79% przychodu z reklam, 21% z IAP** (dane zewnętrznego trackera). To odwraca nasz plan z `BIZNES.md` (zero reklam, tylko płatne pakiety) — reklamy dominują, zakupy są drugorzędne.
- **Skala:** ok. 11–14 mln instalacji, ~4.75★ z ponad 200 tys. ocen (dane szacunkowe z trackerów ASO, nie oficjalne).
- **Jak się promował:** nie znalazłem potwierdzonej historii „viralowej kampanii" — brak źródeł łączących Evil Apples z konkretną akcją marketingową. To, co widać w danych, to **standardowy pętla F2P**: przychód z reklam (79%) prawdopodobnie finansuje płatne zdobywanie użytkowników (ads → UA → więcej graczy → więcej wyświetleń reklam), wspierane silnym naciskiem na ASO (optymalizacja pod wyszukiwanie w sklepie) i wysoką liczbę ocen jako czynnik rankingowy w App Store/Google Play. To samonapędzający się mechanizm skali, nie jednorazowa kampania.

### Jak się wyróżniamy na tle tych trzech

1. **Telefon + TV razem, oba wymagane do pełnego doświadczenia stołu** — nie czysto mobilnie jak Evil Apples (każdy w swój ekran, nawet siedząc razem), nie czysto webowo-bez-ekranu jak Massive Decks, nie B2B-dla-lokalu jak Quizado. Projektujemy pod *wspólny stół w tym samym pokoju*, z TV jako opcjonalnym wzmocnieniem tego doświadczenia, nie zamiennikiem telefonu.
2. **Punktacja podium (3-2-1)** zamiast „jeden zwycięzca wybrany przez sędziego" (CAH/Evil Apples) — więcej osób w rundzie czuje się nagrodzonych, mniej „wszystko albo nic".
3. **Zero reklam, zero trackingu, zero apki, zero kont w etapie 1** — najniższy próg wejścia (otwórz link w przeglądarce) i najprostszy, najtańszy hosting (jeden mały VPS) w porównaniu z modelem ads-first (Evil Apples) czy SaaS-dla-biznesu (Quizado).
4. **Treści 100% własne, po polsku, pod polskie okazje** (wesele, panieński, integracja firmowa) w wersjach pokoleniowych — nisza kulturowo-językowa, której żadna z trzech apek nie celuje (Evil Apples/Massive Decks są generyczne i anglojęzyczne, Quizado to ogólny trivia, nie gra-humor karciana).
5. **Tryb rodzinny na żywo (toggle)** odfiltrowujący treści 18+ w trakcie gry — żadna z trzech nie ma tej funkcji wyraźnie opisanej; przy grach rodzinnych/wielopokoleniowych to realna przewaga.

## 13. 🆕 Czego może brakować — audyt operacyjny/UX/prawny

> Poza biznesplanem/marketingiem (pokryte w `BIZNES.md`/`FORMAL.md`/sekcji 12). Sprawdzone w repo: brak `.github/workflows` (zero CI), brak szablonu „Jak grać"/tutorialu, brak obsługi cookie, brak kodu moderacji (zakazane słowa/zgłoś kartę).

### Operacyjne — zero wzmianki gdziekolwiek dziś
- **Monitoring/alerting** — jeśli VPS padnie w trakcie czyjejś imprezy, nikt się o tym nie dowie. Zero uptime monitoringu (nawet darmowy UptimeRobot/healthchecks.io), zero error trackingu.
- **CI** — potwierdzone: brak `.github/workflows`, zero automatycznego `./mvnw test` na push/PR. Testy są (5 plików, w tym e2e), puszczane tylko ręcznie. Akceptowalne dla solo projektu, ale warto nazwać to świadomą decyzją, nie przeoczeniem.
- **Load test realnego VPS** — `DEPLOY.md` proponuje `-Xmx256m` i limity, ale nikt nie sprawdził appki pod realnym obciążeniem (np. 10 równoczesnych gier × 6 klientów co 3s) na docelowym, tanim VPS.

### Bezpieczeństwo treści — sekwencjonowanie, nie nowa funkcja
- **„Pusta karta" vs moderacja — zależność między dwoma istniejącymi TODO, nigdzie niezapisana.** README TODO ma „pusta karta" (Rozgrywka) i „zakazane słowa"/„zgłoś kartę" (Panel admina) jako osobne pozycje. Jeśli pusta karta wyjdzie na produkcję zanim moderacja istnieje, jest okno czasowe z niemoderowanym tekstem od nieznajomych graczy, bez żadnej siatki bezpieczeństwa. Kolejność wdrożenia, nie coś do dopisania.

### Prawne niuanse — nie blokujące, do zaznaczenia
- **Banner cookie** — `FORMAL.md` mówi „nie zbiera danych osobowych", ale nie rozstrzyga explicite, czy cookie sesji (`playerId`/`gameId`) wymaga bannera zgody. Prawdopodobnie nie („ściśle niezbędne do działania usługi" to wyjątek z ePrivacy), ale to osobne pytanie od RODO/danych osobowych, dziś nieadresowane wprost.

### UX — brakuje warstwy „dla nowego gracza"
- [x] **Brak w-grze wyjaśnienia reguł** — zrobione: `/jak-grac` (`InfoController`), krótki tutorial, link z `/`. Inna rzecz niż strona „O grze" (ta jest o prywatności/legalności, nie o regułach).
- **Accessibility** — kontrast kolorów, odróżnienie kart QUESTION/ANSWER dla daltonistów, wielkość fontu na małym ekranie telefonu — zero wzmianki gdziekolwiek.

### Zaakceptowane, nie przeoczone ryzyko — do nazwania wprost
- **Jeden VPS = single point of failure**, brak redundancji — świadoma decyzja („stała cena, nie pay-per-use" z `CLAUDE.md`), ale warto to nazwać jako zaakceptowane ryzyko w dokumentacji, nie milczeć.

## 14. Później / bez konkretnego etapu

*(już: ROADMAP.md)*

- Kolejne zestawy okolicznościowe × pokolenia
- Własny pakiet kart na zamówienie (impreza firmowa, panieński na zlecenie)
- Dobrowolne przekazanie roli admina konkretnej osobie (bez wychodzenia z gry)

## 15. Źródła (research konkurencji, 2026-11)

- [Jackbox Party Pack — gry i mechanika telefon/TV](https://jackboxgames.com/how-to-play)
- [10 Fun Phone Games for Fans of KBH Games](https://www-origin.jackboxgames.com/blog/games-for-fans-of-kbh-games)
- [Cards Against Originality / Massive Decks / Evil Apples — przegląd klonów CAH](https://www.romper.com/life/10-best-apps-games-for-parties-that-you-can-play-on-your-phone-for-free-cheap-18181364)
- [Massive Decks (open source, web)](https://nextquest.dev/games/massive-decks)
- [Psych! Outwit Your Friends — mechanika głosowania](https://mechanicsofmagic.com/?p=6409)
- [Fibbage — analiza mechaniki bluffowania](https://mechanicsofmagic.com/2022/04/14/competitive-analysis-fibbage-nadin/)
- [Quizado — quiz bar trivia bez instalacji, QR na TV](https://quizado.com/pl/bar-trivia)
- [Wavelength — mechanika spektrum/dialu](https://officialgamerules.org/game-rules/cmyk-wavelength/)
- [Spyfall / Monikers / Codenames — role ukryte i granie na zgadywanie](https://destructoid.com/?p=619920)
- [Game monetization: Models, strategies, and how to choose (Unity)](https://unity.com/en/resources/mobile-game-monetization-guide)
- [The 2026 Mobile Game Monetization Playbook](https://appfollow.io/blog/mobile-game-monetization)
- [Quizado — bar trivia marketing/SEO blog](https://quizado.com/blog/trivia-night-marketing-strategy-packed-venues)
- [Quizado vs Crowdpurr / SpeedQuizzing alternative (przykłady SEO landing page'y)](https://quizado.com/pl/compare/quizado-vs-crowdpurr)
- [Massive Decks jako polecana alternatywa dla CAH (Reddit, r/CardsAgainstHumanity)](https://reddit.sudovanilla.org/r/CardsAgainstHumanity)
- [Evil Apples — model monetyzacji (ASO/ad-IAP split)](https://appgoblin.info/apps/645705454)
- [Evil Apples — App Store pricing/IAP](https://apppricinglab.com/app/apple/645705454)
