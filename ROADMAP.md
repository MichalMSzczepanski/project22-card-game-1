# ROADMAP.md — rozwój produktu

> Szczegóły zadań: README → TODO. Pieniądze: [`BIZNES.md`](BIZNES.md). Prawo: [`FORMAL.md`](FORMAL.md).
> Status: ⬜ do zrobienia · 🟨 w trakcie · ✅ gotowe

## v0 — prototyp (obecny stan) ✅
- ✅ Gra 3–5 graczy, 20 rund, rotujący prowadzący, punktacja rankingowa
- ✅ Ekran TV, tryb rodzinny, wykrywanie rozłączeń, timeouty
- ✅ Konta, OAuth, symulowane zakupy (do wyłączenia w v1)

## v1 — publiczne darmowe MVP
Cel: grywalne w internecie, bezpieczne, bez kosztów-niespodzianek.
- 🟨 Rola admina gry (README sekcja 5) — host-only start zrobione, reszta (wyrzucanie, „Przejmij admina”, TV) w planach
- ⬜ Do 10 graczy
- ✅ Punktacja podium 3-2-1 (MECHANICS.md)
- ✅ Dłuższy timeout rozłączenia (60s, zgaszony ekran telefonu nie wyrzuca z gry) i priorytet prowadzącego (30s) na „Następna runda”
- ⬜ Wyłączenie zakupów i kont; „Tematyczne zestawy — wkrótce” + licznik „Chcę ten zestaw!”
- ⬜ Własne karty i własny wygląd
- ⬜ Postgres + Flyway, sprzątanie porzuconych gier
- ⬜ Rate limiting, limity zasobów
- ⬜ Deployment: VPS + Cloudflare Tunnel
- ⬜ Panel admina aplikacji — wersja minimalna (dashboard, gry na żywo, karty)

## v1.1 — więcej zabawy
- 🟨 Ustawienia gry — liczba rund wybierana przy tworzeniu gry ✅; „do X punktów” i timeouty per-gra ⬜
- ⬜ Pusta karta (własna odpowiedź raz na grę)
- ⬜ Wymiana ręki raz na grę
- ⬜ „Zagraj jeszcze raz”
- ⬜ QR do dołączania na ekranie TV
- ⬜ Podsumowanie gry na TV (podium, najlepsze odpowiedzi)

## v1.2 — jakość treści
- ⬜ Zgłoś kartę + kolejka zgłoszeń w panelu admina aplikacji
- ⬜ Statystyki kart (co wygrywa, co przegrywa)
- ⬜ Zarządzanie kartami i pakietami z panelu (zamiast hardkodu w `CardSeeder`)

## v2 — freemium (po walidacji, patrz BIZNES.md)
- ⬜ Forma prawna, regulamin, polityka prywatności, znak towarowy
- ⬜ Konta (wraca logowanie) + operator płatności
- ⬜ 2 pierwsze zestawy tematyczne
- ⬜ Pakiet admina działa dla całego stołu
- ⬜ Panel admina aplikacji: zakupy, zwroty, przychody

## Później / pomysły
- ⬜ Głos publiczności (QR z TV, +1 punkt dla ulubionej odpowiedzi)
- ⬜ Kolejne zestawy okolicznościowe × pokolenia
- ⬜ Własny pakiet kart na zamówienie (impreza firmowa, panieński)
- ⬜ Dobrowolne przekazanie roli admina gry
- ⬜ **Easter eggi i uatrakcyjnienia** (hat trick, zmartwychwstanie, underdog bonus, runda-bonus x2, finałowa runda x3, Rando) — pełna lista z przykładowymi tekstami: [`MECHANICS.md`](MECHANICS.md) → „Easter eggi i uatrakcyjnienia”. Zasada: śmieszny tekst przed nową mechaniką punktową.
