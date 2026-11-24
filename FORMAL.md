# FORMAL.md — prawo, licencje, anonimowość, etapy

> Notatki robocze, nie porada prawna. Przed etapem 2 (sprzedaż) — jedna konsultacja z prawnikiem.

## Decyzja
**Etap 1: darmowe MVP, anonimowo, dla zabawy.** Etap 2 (freemium) dopiero po sprawdzeniu, że ludzie chcą grać.

## ⚠️ Odrzucone na razie: granie o prawdziwe pieniądze (ryzyko hazardu)

Pomysł: gracze wpłacają stawkę (np. 10 zł), pula rozkłada się na zwycięzców. **To prawdopodobnie gra losowa w rozumieniu polskiej Ustawy o grach hazardowych (2009)** — trzy elementy się zgadzają: stawka pieniężna + element przypadku (losowe rozdanie kart) + nagroda. Nie ma znaczenia, czy platforma bierze cokolwiek z puli dla siebie — liczy się samo **organizowanie** takiej gry dla nieznajomych graczy, co wymaga koncesji od Ministra Finansów. Prowadzenie bez koncesji grozi karami administracyjnymi i w niektórych przypadkach odpowiedzialnością karną.

**Owijanie tego w wewnętrzną walutę (np. „Coiny" kupowane za PLN) nie rozwiązuje problemu automatycznie** — regulator patrzy przez taki wrapper na to, czy realne pieniądze wchodzą na początku i czy coś o realnej wartości (w tym: wymienialne z powrotem na pieniądze) wychodzi na końcu. Jeśli waluta jest wymienialna z powrotem na pieniądze/coś zbywalnego — to wciąż ten sam problem, tylko z dodatkową warstwą pośrednią, która *ułatwia* regulatorowi dowodzenie (wyraźna księga transferu wartości). Jeśli waluta jest **jednostronna, bez cash-outu, bez wartości rynkowej** (można ją tylko wydać w appce na coś bez wartości poza appką) — to realnie zmienia naturę rzeczy, ale też przestaje być tym samym pomysłem (gracze nie „wygrywają realnych pieniędzy", tylko odblokowują nagrody w appce — bliżej mikro-pakietów z `BIZNES.md`/`IDEAS.md` niż hazardu).

**To wymaga dedykowanej konsultacji z prawnikiem specjalizującym się w prawie hazardowym** — inna, głębsza konsultacja niż ta zaplanowana przed etapem 2 (VAT/spółka/sprzedaż pakietów, patrz niżej). Nie projektować ani nie budować żadnej wersji tego (prawdziwe pieniądze czy waluta wewnętrzna z choćby cząstkowym cash-outem) bez tej konsultacji. Niniejsza notatka nie jest poradą prawną.

## Inne gry tego typu (np. Cards Against Humanity) — czego nie robimy
- **Mechanika** (pytanie + karty-odpowiedzi + rotujący prowadzący) **nie jest chroniona** — wolno.
- **Ich teksty kart** — nie używamy. Licencja CC BY-NC-SA 2.0 zabrania użycia komercyjnego, a planujemy freemium. **Wszystkie karty piszemy sami od pierwszego dnia.**
- **Nazwa** — bez „Cards Against…”, „…Against Humanity” i gier słownych na tej nazwie (CAH skutecznie bronił się przed podobnymi nazwami).
- **Wygląd** — bez czarno-białych kart w ich stylu i bez Helvetica Neue; własna kolorystyka, font, nazewnictwo (u nas `QUESTION`/`ANSWER`).
- **Materiały publiczne** (strona, opisy, social media) — bez porównań typu „jak CAH”.

## Czym się różnimy (warto podkreślać)
- Punktacja podium: prowadzący wybiera 3 najlepsze odpowiedzi (3/2/1 pkt), a nie „jeden zwycięzca”.
- Ekran TV, tryb rodzinny, polski język, zestawy okolicznościowe w wersjach pokoleniowych.

## Ochrona własnego pomysłu
- Pomysłu ani mechaniki **nie da się zastrzec** — każdy może zrobić podobną grę.
- **Kod i teksty kart** są chronione prawem autorskim automatycznie. Dowód autorstwa i daty: historia commitów.
- **Repo prywatne** — dziś jest publiczne, każdy może skopiować kod i karty.
- **Nazwa i logo** → znak towarowy (UPRP lub EUIPO) — dopiero w etapie 2, bo rejestr jest publiczny (patrz niżej).
- Prawdziwa przewaga: **treści (pakiety) + marka + regularne nowości**.

## Anonimowość
- Etap 1 (brak sprzedaży): firma i znak niepotrzebne → można pozostać anonimowym.
- Etap 2 (sprzedaż): działalność/spółka (CEIDG/KRS) i znak towarowy są **jawne** → pełna anonimowość się kończy; najwyżej nazwisko za spółką.
- Zawsze: brak danych osobowych w repo i na stronie, commity z noreply GitHuba, domena z ukrytym WHOIS, osobny alias e-mail.

## Etap 1 — darmowe MVP (zasady)
- Płatne pakiety wyłączone; zamiast nich „Tematyczne zestawy — wkrótce”, **bez dat i cen** (nic nie obiecujemy).
- Przycisk „Chcę ten zestaw!” z licznikiem — **bez maila i danych osobowych**; wynik decyduje, które 2 zestawy wydać jako pierwsze.
- Gra bez konta zostaje — same nicki, grywalne anonimowo.
- Krótka strona „O grze”: gra jest darmowa, nie zbiera danych osobowych (poza kontem, patrz niżej), treści 18+ (poza trybem rodzinnym).

### Konta gracza wróciły przed etapem 2 — polityka prywatności nadrobiona
Login/logowanie (`AuthController`, `AppUser`) zostały odblokowane wcześniej niż planowano (README TODO „Propozycje kart od graczy” — funkcja wymaga trwałej tożsamości gracza). To **łamie** założenie Etapu 1 wyżej („bez kont → minimalne obowiązki RODO”): konto to login + hash hasła (i opcjonalnie e-mail przez OAuth) = dane osobowe.
- ✅ **Polityka prywatności** — `/polityka-prywatnosci` (`InfoController`), linkowana z `/`, `register.html`, `o-grze.html`, `my-account.html`. Opisuje realny stan: co zbieramy (login+hash hasła, treść propozycji kart), komu udostępniamy (nikomu), jak długo (dziś: do restartu serwera — H2 `create-drop`), i **prawo do usunięcia** — zrealizowane jako samoobsługa: `/me/account` → `UserService.deleteAccount` usuwa konto, propozycje kart i historię wyników (zaakceptowane karty zostają w grze jako anonimowa treść).
- Brak jeszcze publicznego kontaktu (etap anonimowego MVP) — polityka to otwarcie mówi, zamiast obiecywać kanał, którego nie ma.
- Zakupy/płatności, znak towarowy, forma prawna, regulamin — **nadal** czekają na etap 2 (sprzedaż), tego konta sam fakt nie zmienia.
- Niniejsza notatka (i polityka prywatności) nie są poradą prawną — jeśli appka ma realny ruch, konsultacja prawnicza przed, nie po.

## Etap 2 — freemium (później)
- Darmowe: baza + tryb rodzinny. Płatne: na start **2 zestawy tematyczne** (wybrane licznikiem).
- Pakiet admina działa dla całego stołu.
- Do przygotowania: forma prawna, regulamin, operator płatności (plan integracji: [`README.md`](README.md) sekcja 6 — merchant-of-record zdejmuje rozliczanie VAT, nie zwalnia z jawności spółki), znak towarowy, konsultacja prawna. Polityka prywatności (RODO) — ✅ zrobiona wcześniej, patrz wyżej (trzeba ją tylko zaktualizować, gdy pojawi się realny kontakt/firma).
