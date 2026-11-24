# DEPLOY.md — plan wdrożenia

> Jak wypuścić appkę poza demo po Wi-Fi w domu. Mieszanka: (1) konkretny, gotowy do wykonania plan na **obecną wersję** (H2, bez Dockera) i (2) decyzje, które trzeba podjąć przed kolejnym krokiem (Docker, Postgres, rate limiting w kodzie). Oznaczenia: ✅ gotowe do zrobienia teraz, 🤔 decyzja do podjęcia, 🔮 czeka na inny etap/pracę.
>
> Pomysły na mechanikę/biznes/UI → [`IDEAS.md`](IDEAS.md). Zdecydowane specy → [`README.md`](README.md). To jest trzeci rodzaj dokumentu: **plan operacyjny**, nie brainstorm i nie spec kodu.

## 1. Dziś vs cel

**Dziś:** `./mvnw spring-boot:run` na czyimś laptopie w LAN — telefony muszą być w tej samej sieci Wi-Fi, bo nie mają jak dobić do prywatnego IP tego laptopa. To jest ograniczenie **deploymentu**, nie protokołu komunikacji (appka i tak rozmawia zwykłym HTTP, zero lokalnego discovery) — patrz `IDEAS.md` sekcja „bardziej live i niezależność od WiFi".

**Cel (v1):** VPS ze stałą ceną + Cloudflare Tunnel, zero otwartych portów, zero usług pay-per-use (`CLAUDE.md` „Decyzje"). Ograniczenie WiFi znika **za darmo**, bez zmiany logiki gry — telefon na Wi-Fi i telefon na LTE trafiają do tego samego publicznego adresu identycznie.

## 2. ✅ Krok po kroku: VPS + Cloudflare Tunnel, obecna wersja (H2, bez Dockera)

Jar uruchamiany przez systemd, nie kontener — szybsza ścieżka niż najpierw budować całą infrastrukturę Dockerową (patrz sekcja 4).

### VPS
- **Hetzner Cloud CX22** (~4€/mies., 2 vCPU/4GB) albo **Mikrus** (polski, ~15–20zł/mies., mniej zasobów, starczy na MVP).
- Uwaga anonimowości (`FORMAL.md`): Hetzner wymaga karty/PayPal i czasem weryfikacji tożsamości — mniej anonimowe. Mikrus historycznie bardziej liberalny. Decyzja biznesowa, nie techniczna.
- System: Ubuntu 24.04 LTS minimal.

### Hardening serwera (pierwsze 10 minut)
```bash
adduser karciana --disabled-password
usermod -aG sudo karciana
# w ~/.ssh/authorized_keys użytkownika karciana wklej publiczny klucz
# w /etc/ssh/sshd_config: PasswordAuthentication no, PermitRootLogin no
systemctl restart ssh

ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp   # tymczasowo — docelowo zastąpić Tailscale, patrz sekcja 3
ufw enable
apt install fail2ban -y
```
Port 80/443 **nie jest potrzebny** — cały ruch webowy wchodzi przez Cloudflare Tunnel (połączenie outbound-only z VPS do Cloudflare).

### Runtime: jar + systemd
```bash
apt install -y openjdk-17-jre-headless
mkdir -p /opt/karciana-gra/data
chown karciana:karciana -R /opt/karciana-gra
```
Lokalnie: `./mvnw clean package -DskipTests` → `scp target/*.jar vps:/opt/karciana-gra/app.jar`

`/etc/systemd/system/karciana-gra.service`:
```ini
[Unit]
Description=Karciana Gra
After=network.target

[Service]
User=karciana
WorkingDirectory=/opt/karciana-gra
ExecStart=/usr/bin/java -Xmx256m -jar app.jar --spring.datasource.url=jdbc:h2:file:/opt/karciana-gra/data/carddb
Restart=on-failure
Environment=SPRING_PROFILES_ACTIVE=prod

[Install]
WantedBy=multi-user.target
```
```bash
systemctl enable --now karciana-gra
```

### Cloudflare Tunnel
```bash
# dodaj repo Cloudflare, apt install cloudflared
cloudflared tunnel login                      # autoryzacja w przeglądarce
cloudflared tunnel create karciana-gra        # tworzy UUID + plik credentials
cloudflared tunnel route dns karciana-gra gra.twojadomena.pl   # CNAME, auto-proxied
```
`/etc/cloudflared/config.yml`:
```yaml
tunnel: <UUID-z-create>
credentials-file: /etc/cloudflared/<UUID>.json
ingress:
  - hostname: gra.twojadomena.pl
    service: http://localhost:8080
  - service: http_status:404
```
```bash
cloudflared service install
systemctl enable --now cloudflared
```
Apka słucha tylko na `localhost:8080` — UFW nie musi niczego dla niej przepuszczać, tunel łączy się od wewnątrz.

### Ustawienia w panelu Cloudflare (darmowe)
- SSL/TLS: **Full**, włącz „Always Use HTTPS".
- **Bot Fight Mode**: on.
- Reguła rate limit (free tier) na `POST /games/join` → max N żądań/minutę/IP → Challenge. Łata **częściowo** brak Bucket4j w kodzie (sekcja 3) — nie zastępuje go w 100%.

### Konkretne zmiany w konfiguracji appki — łatwo przeoczyć
- **`server.forward-headers-strategy=framework`** w `application.yaml` — bez tego Spring myśli, że ruch przychodzi po zwykłym HTTP (bo `cloudflared → localhost:8080` wewnętrznie jest plain HTTP, TLS kończy się na edge Cloudflare). Konsekwencja bez tej flagi: **cookie sesji nie dostanie flagi `Secure`** — realna dziura, nie kosmetyka.
- Ten sam flag odblokowuje poprawne czytanie `CF-Connecting-IP`/`X-Forwarded-For` — potrzebne pod rate limiting z sekcji 3.
- **H2 konsola** — już bezpieczna domyślnie (`CODING.md`: tylko profil `dev`), upewnić się, że na VPS nie startuje się z `-Dspring-boot.run.profiles=dev`.

### 🤔 Czego to NIE rozwiązuje — szczere ograniczenia tego kroku
- **Dane giną przy każdym restarcie** (`ddl-auto=create-drop`) — restart systemd/aktualizacja jara = wszystkie aktywne gry przepadają. Do zaakceptowania na czas bety, nie na stałe (realny fix: sekcja 5).
- **Brak rate limitingu w kodzie appki** — reguła Cloudflare łata to częściowo (sekcja 3 dalej opisuje pełne rozwiązanie).
- **Zakupy/konta wciąż żywe w kodzie** (stub `PacksController.purchase`, OAuth/rejestracja) — README TODO etapu 1 chce je wyłączone. Jeśli to ma wyjść choćby nieformalnie publicznie, warto to przełączyć pierwsze — inaczej `FORMAL.md` obiecuje „bez zakupów i kont", a kod na to pozwala.

## 3. 🤔 Warstwa sieciowa i rate limiting — do dopisania w kodzie

Te dwa TODO są ze sobą sprzężone, warto robić razem: rate limiting per IP nie ma sensu, dopóki appka nie znająca prawdziwego IP klienta — za Cloudflare Tunnel każdy request widzi IP samego Cloudflare, nie gracza, bez odczytania `CF-Connecting-IP` (patrz sekcja 2, `forward-headers-strategy`).

1. **Bucket4j** (nowa zależność, brak w `pom.xml`) w wariancie **in-memory, nie Redis** — jedna instancja appki na jednym VPS nie potrzebuje rozproszonego licznika, a Redis to kolejna usługa do utrzymania/kosztu, czego explicite unikamy.
2. Limity per IP na **konkretne** endpointy: `POST /games` (tworzenie gry), `POST /games/join` (zgadywanie 5-znakowych kodów — realny wektor ataku, 32^5 ≈ 33M kombinacji, brute-force bez rate limitu jest wykonalny), `/login`, `/register` (ten ostatni i tak znika w etapie 1).
3. **Limit musi zostawić miejsce na polling** — 10 graczy + TV odpytujący `/games/{id}` co 3s to ~4 req/s *na tę samą grę*, co jest ruchem legalnym, nie atakiem; limit per-IP musi rozróżniać „częste odpytywanie własnej gry" (OK) od „częste próby `POST /games/join` z różnymi kodami" (blokować).
4. **Limity na grę**: max liczba aktywnych gier globalnie i per IP — prosty licznik w `GameService.createGame` (`gameRepository.countByStatusNot(FINISHED)`), zero nowej infrastruktury, tylko warunek przed zapisem.
5. Firewall/SSH: port 22 otwarty „na wszystkich" z sekcji 2 to tymczasowe — docelowo **Tailscale** albo ograniczenie do konkretnego IP, SSH tylko po kluczu.

## 4. 🤔 Dockeryzacja — decyzja do podjęcia, nie zrobiona

**Napięcie:** `Dockerfile` + `docker-compose.yml` dla samej appki (bez Postgresa) da się zrobić niezależnie od migracji bazy — ale compose w naturalny sposób sugeruje `app + Postgres` razem, czyli dockeryzacja ciągnie za sobą pytanie o bazę, nie jest od niego niezależna.

Dwie opcje:
- **(a) Dockeryzować tylko appkę z H2 plikowym** jako krok pośredni pod demo — prościej, ale `ddl-auto=create-drop` nadal czyści dane przy restarcie kontenera, więc nic nie zyskujemy na trwałości, tylko ułatwiamy sobie deploy (`docker compose up -d` zamiast ręcznego systemd z sekcji 2).
- **(b) Poczekać z Dockerem do migracji na Postgres+Flyway** (sekcja 5) i zrobić to razem — więcej pracy na raz, ale bez tymczasowego, połowicznego setupu.

To decyzja do podjęcia, nie coś do wdrożenia teraz — zgodnie z priorytetem „MVP asap". Jeśli/gdy ruszy:
- Multi-stage build (builder z JDK + `./mvnw`, runtime na JRE), non-root user w kontenerze, `HEALTHCHECK`.
- `docker-compose.yml`: named volume na dane, `mem_limit`/`cpus` (patrz sekcja 7), port appki bindowany tylko do `127.0.0.1` (Cloudflare Tunnel łączy się lokalnie, nie publicznie).

### 💡 Pomysł na treść `Dockerfile` (draft, nie wdrożone)

Multi-stage, builder używa `./mvnw` projektu (ten sam build co lokalnie), runtime na JRE Alpine, non-root user, healthcheck:

```dockerfile
# syntax=docker/dockerfile:1

# Builder: własny mvnw, nie obraz maven-flavored — Java/Maven w wersjach,
# które wrapper i tak pinuje.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline
COPY src ./src
RUN ./mvnw -B -DskipTests clean package

# Runtime: tylko JRE, nic z builda poza jarem.
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S karciana && adduser -S karciana -G karciana
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN mkdir -p /app/data && chown -R karciana:karciana /app
USER karciana
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s \
    CMD wget -qO- http://localhost:8080/ > /dev/null || exit 1
ENTRYPOINT ["java", "-Xmx256m", "-jar", "app.jar"]
```

I towarzyszący `docker-compose.yml` (H2 plikowe jako wariant (a) z decyzji wyżej — bez Postgresa):

```yaml
services:
  app:
    image: karciana-gra:${VERSION:-local}
    build: .
    restart: unless-stopped
    ports:
      - "127.0.0.1:8080:8080"
    volumes:
      - karciana-data:/app/data
    mem_limit: 384m
    cpus: 1.0

volumes:
  karciana-data:
```

Oba pliki **jeszcze nie istnieją w repo** — to draft na później, do skonsultowania z decyzją (a)/(b) wyżej, nie coś do wklejenia od razu.

### 💡 Pomysł na wersjonowanie obrazu

Tagować obraz wersją z `pom.xml` (jedno źródło prawdy, to samo co wersja jara — nie wymyślać osobnego mechanizmu numeracji), plus `:latest` dla wygody lokalnego builda:

```bash
#!/usr/bin/env bash
# scripts/release.sh (draft) — buduje obraz z wersją z pom.xml.
# Podbicie wersji = edycja <version> w pom.xml, jak w każdym projekcie Maven.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version)"
IMAGE="karciana-gra"

docker build -t "${IMAGE}:${VERSION}" -t "${IMAGE}:latest" .

echo "Built ${IMAGE}:${VERSION}"
echo "Deploy na VPS: VERSION=${VERSION} docker compose up -d"
echo "Rollback: VERSION=<poprzednia-wersja> docker compose up -d (jeśli tag jest jeszcze lokalnie/w registry)"
```

Workflow: bump `<version>` w `pom.xml` → `./scripts/release.sh` → `docker compose up -d` na VPS ze zmiennym `VERSION`. Rollback to tylko zmiana `VERSION` na poprzedni tag, bez osobnego mechanizmu — pod warunkiem, że ten tag wciąż istnieje lokalnie albo w registry (patrz niżej, obraz trzeba gdzieś trzymać między buildem a VPS-em).

**Nierozstrzygnięte: skąd VPS bierze obraz?** Dwie opcje, obie do rozważenia później, nie teraz:
- **(a) Build lokalnie, bez registry** — `docker save karciana-gra:X.Y.Z | ssh vps docker load`. Zero zewnętrznych kont/usług (zgodne z anonimowością z `FORMAL.md`), ale ręczne kopiowanie obrazu przy każdym wydaniu.
- **(b) Registry (np. `ghcr.io`, darmowe)** — `docker push`/`docker compose pull` po tagu. Wygodniejsze przy częstszych wydaniach, ale nowe konto/zależność zewnętrzna, i obraz (nawet prywatny) leży poza własnym VPS.

## 5. 🔮 Dane: H2 → Postgres + Flyway, backup, sekrety

- **Przejście H2 → Postgres + Flyway** (`ddl-auto=validate`) — osobny duży kawałek pracy, ale odblokowuje trwałość danych między restartami, CRUD kart w panelu admina (`IDEAS.md` sekcja „Panel admina") i usuwa pułapkę z enumami (README sekcja 3).
- **Backup bazy** — prosty `pg_dump` do wolumenu/zewnętrznego storage przez cron **systemowy** na VPS (nie `@Scheduled` w appce — niezmiennik „brak schedulera" z `CLAUDE.md` dotyczy logiki gry, nie infrastruktury wokół niej, ale warto to rozróżnienie zapisać).
- **Sekrety przez zmienne środowiskowe** (`.env` niecommitowany, `env_file` w compose) — hasło DB, ewentualny sekret sesji; OAuth client secret przestaje być potrzebny w etapie 1 (OAuth wyłączony).

## 6. 🤔 Sesja gracza: Postgres vs Redis

Pod słowem „sesja gracza" kryją się **dwie zupełnie różne rzeczy**:

1. **`Player.lastSeenAt`** — dane biznesowe/domenowe (część agregatu `Game`, podlega tym samym kaskadom co reszta rundy), nie „sesja" w sensie tokenu. Zostaje w Postgres, razem z resztą stanu gry — zgodnie z niezmiennikiem „cały stan gry w bazie" (`CLAUDE.md`). Jedyny problem z nią to częstotliwość zapisu (`GameController` woła `gameService.touch()` na **każdym** pollu, bez throttlingu), nie to, w jakiej bazie siedzi.
2. **`HttpSession` (playerId/gameId, dziś Spring Session JDBC, `SPRING_SESSION*`)** — to naprawdę brzmi jak token, bo nim jest: mały blob (dwa ID), TTL 8h, zero relacji, zero potrzeby przeżycia restartu bazy (zgubienie sesji = gracz dołącza kodem jeszcze raz, nie utrata stanu gry). **Znacznie czystszy przypadek pod Redis** niż `lastSeenAt`.

**Konkretna propozycja dla (2):** `spring-session-data-redis` zamiast `spring-session-jdbc` — praktycznie podmiana jednej zależności + jednej anotacji (`@EnableRedisHttpSession` zamiast `@EnableJdbcHttpSession` w `SessionConfig`), zero zmian w `GameController`. TTL sesji mapuje się 1:1 na natywny `EXPIRE` w Redisie.

**Trade-offy:**
- Nowy, samodzielnie hostowany serwis na VPS (kontener Redis) — nie łamie „żadnych usług pay-per-use" (self-hosted, nie Redis Cloud), ale dodatkowa powierzchnia operacyjna. Redis jest lekki, koszt realny niewielki.
- Utrata Redisa = wszyscy gracze tracą sesję w trakcie imprezy — ale to **ten sam blast radius co dziś** (sesje i tak żyją w tej samej bazie co stan gry). Przeniesienie do Redisa nie jest pogorszeniem, raczej lepszą izolacją.
- Dane w Redisie są efemeryczne — zero backupu do robienia.

**Zanim się tym zajmujemy:** dwa darmowe fixy w kodzie, niezależnie od wyboru bazy — throttling `GameService.touch()` (zapis `lastSeenAt` tylko gdy minęło np. >5s od ostatniego) i `SaveMode` Spring Session (zapis sesji tylko gdy atrybut faktycznie się zmienia). Realistycznie pierwszy krok przed sięganiem po Redis.

## 7. ✅ Zasoby i limity

- `-Xmx256m`–`512m` dla JVM (mała apka, H2/Postgres na tym samym VPS) — do ustalenia dokładnie po pierwszych testach obciążenia.
- `mem_limit`/`cpus` w docker-compose (gdy dockeryzacja ruszy, sekcja 4) — żeby błąd/atak nie wyczerpał całego VPS ze stałą ceną.
- Polling co 3s musi się mieścić w limicie rate (sekcja 3, punkt 3): 10 graczy + TV ≈ 4 req/s na grę.
