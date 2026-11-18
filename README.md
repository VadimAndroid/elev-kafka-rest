# Elev-system: elev-api og elev-service

To Spring Boot-tjenester skrevet i Kotlin som samarbeider via **Kafka** og **HTTP**.
`elev-service` lagrer elevene i **PostgreSQL**, slik at de finnes også etter omstart.
`elev-api` har ingen database.

> Dette er grenen **`portfolio`**. Skoleversjonen med H2-database ligger i grenen `main`.

## Arkitektur

```
Opprette elev (asynkront):
Klient ──HTTP POST──▶ elev-api ──Kafka (topic "elever")──▶ elev-service ──▶ PostgreSQL

Hente elever (synkront):
Klient ──HTTP GET───▶ elev-api ──HTTP GET──▶ elev-service ──▶ leser fra PostgreSQL
```

| Tjeneste       | Port | Ansvar                                                                 |
|----------------|------|------------------------------------------------------------------------|
| `elev-api`     | 8080 | Eneste inngang for klienten. Validerer, genererer ID, sender til Kafka, henter fra `elev-service`. Lagrer ingenting selv. |
| `elev-service` | 8081 | Leser elever fra Kafka og lagrer dem i databasen. Tilbyr GET-endepunkter som leser fra databasen. |
| Kafka          | 9092 | Meldingskø mellom tjenestene (kjører i Docker).                        |
| PostgreSQL     | 5432 | Databasen til `elev-service` (kjører i Docker, data i volumet `elev-pgdata`). |

## Forutsetninger

- Java 25 (JDK)
- Docker Desktop

## Slik starter du

Start i denne rekkefølgen, fra mappen `elev-kafka-rest`:

**1. Start Kafka og PostgreSQL**

```bash
docker compose up -d
docker ps          # kafka skal ha status "Up", elev-postgres "Up (healthy)"
```

Topicen `elever` opprettes automatisk ved første melding. Den kan også opprettes manuelt:

```bash
docker exec -it kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --create --topic elever
```

**2. Start elev-service** (port 8081)

```bash
cd elev-service
./gradlew bootRun
```

**3. Start elev-api** (port 8080) – i en ny terminal

```bash
cd elev-api
./gradlew bootRun
```

Tjenestene kan også startes fra IntelliJ med ▶ ved `fun main` i `ElevServiceApplication.kt` og `ElevApiApplication.kt`.
**Stoppe:** `Ctrl + C` i hver terminal, og `docker compose down` for Kafka og PostgreSQL.
Dataene i PostgreSQL ligger i Docker-volumet `elev-pgdata` og blir **ikke** slettet av `docker compose down`.
`docker compose down -v` sletter også volumet, altså alle elevene.

## Database

### Oppsett

`elev-service` bruker **PostgreSQL 17** og **Spring Data JPA** (med Hibernate).

| Del | Fil | Hva den gjør |
|-----|-----|--------------|
| Database | `docker-compose.yaml` | PostgreSQL-container `elev-postgres`, database `elevdb`, data i volumet `elev-pgdata` |
| Tilkobling | `application.properties` | `jdbc:postgresql://localhost:5432/elevdb`, kan overstyres med miljøvariabler |
| Tabell | `src/main/resources/db/migration/V1__opprett_elev_tabell.sql` | Flyway-migrering som lager tabellen `elev` |
| Entity | `model/ElevEntity.kt` | JPA-klassen som tilsvarer én rad i `elev` |
| Repository | `repository/ElevRepository.kt` | `JpaRepository<ElevEntity, UUID>` – gir `save`, `findById`, `findAll` |

```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/elevdb}
spring.datasource.username=${DB_USER:elev}
spring.datasource.password=${DB_PASSWORD:elev}
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
```

`${DB_PASSWORD:elev}` betyr: bruk miljøvariabelen `DB_PASSWORD` hvis den finnes, ellers `elev`.
Lokalt fungerer standardverdiene direkte. I et ekte miljø settes passordet som miljøvariabel og ligger ikke i git.

Avhengigheter i `elev-service/build.gradle.kts`:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
implementation("org.springframework.boot:spring-boot-starter-flyway")
implementation("org.flywaydb:flyway-database-postgresql")  // Flyway trenger egen modul per database
runtimeOnly("org.postgresql:postgresql")                    // JDBC-driver
```

Tabellen `elev`:

| Kolonne  | Type           | Merknad |
|----------|----------------|---------|
| `id`     | `UUID`         | Primærnøkkel. Kommer fra Kafka-meldingen – databasen lager ikke en ny ID. |
| `navn`   | `VARCHAR(255)` | `NOT NULL` |
| `klasse` | `VARCHAR(255)` | `NOT NULL` |

### Migreringer med Flyway

Tabellene lages av **Flyway**, ikke av Hibernate. Ved oppstart sjekker Flyway tabellen `flyway_schema_history` og kjører bare migreringer som ikke er kjørt før.
Hibernate står på `ddl-auto=validate`, så det kun *sjekker* at `ElevEntity` stemmer med tabellen og stopper oppstarten hvis ikke.

- En migrering som allerede er kjørt, skal **ikke** endres (Flyway oppdager det via checksum).
- Endringer i skjemaet gjøres med en ny fil, for eksempel `V2__legg_til_epost.sql`.

### Kontrollere innholdet i databasen

**Med `psql` i containeren:**

```bash
docker exec -it elev-postgres psql -U elev -d elevdb
```

**Med IntelliJ:** Database-vinduet → `+` → Data Source → PostgreSQL →
host `localhost`, port `5432`, database `elevdb`, bruker `elev`, passord `elev`.

Nyttige spørringer:

```sql
SELECT * FROM elev;                                -- alle elever
SELECT COUNT(*) FROM elev;                         -- antall rader
SELECT id, COUNT(*) FROM elev GROUP BY id
  HAVING COUNT(*) > 1;                             -- duplikater (skal gi 0 rader)
SELECT * FROM flyway_schema_history;               -- hvilke migreringer som er kjørt
```

I `psql`: `\dt` viser tabellene, `\q` avslutter.

Med `spring.jpa.show-sql=true` skriver `elev-service` også alle SQL-spørringer til loggen.

**Starte med tom database:** stopp `elev-service` og kjør `docker compose down -v` og `docker compose up -d`. Flyway lager tabellen på nytt ved neste oppstart.

### Ingen duplikater

Samme elevmelding kan bli lest flere ganger fra Kafka. Siden `id` er primærnøkkel og kommer fra meldingen, gjør `save()` slik:

```
SELECT ... FROM elev WHERE id = ?
  ├─ finnes ikke → INSERT
  └─ finnes      → UPDATE (ingen ny rad)
```

Det kan derfor bare finnes én rad per elev-ID.

## Kommunikasjon: Kafka vs. HTTP

### Over Kafka: opprettelse av elever

`POST /elever` i `elev-api` validerer `navn` og `klasse`, genererer en unik ID og publiserer hele eleven som JSON på topicen `elever`, med elevens ID som meldingsnøkkel. `elev-api` svarer først når Kafka har bekreftet mottak.

`elev-service` lytter på topicen med consumer group `elev-service`, leser JSON-meldingen, lagrer eleven i databasen med samme ID og logger ID-en etter at lagringen er fullført.

**Hvorfor Kafka her?** Kommunikasjonen er asynkron. `elev-api` er ikke avhengig av at `elev-service` kjører akkurat nå – meldingen venter i Kafka til den blir lest. Ingen elever går tapt selv om `elev-service` er nede.

Derfor svarer `POST` med **202 Accepted** og ikke 201 Created: eleven er mottatt og sendt til behandling, men ikke nødvendigvis lagret ennå. En `GET` rett etter `POST` kan derfor gi 404 et kort øyeblikk.

### Over HTTP: henting av elever

`GET /elever` og `GET /elever/{id}` i `elev-api` gjør et HTTP-kall til `elev-service`, som leser fra databasen. GET sender ingen Kafka-meldinger.

**Hvorfor HTTP her?** Klienten trenger svaret med en gang, så kommunikasjonen må være synkron. Hvis `elev-service` ikke svarer, vet vi det med en gang og returnerer **503 Service Unavailable** – ikke en tom liste, fordi en tom liste ville vært feil informasjon.

## Endepunkter

### elev-api (port 8080)

| Metode | Sti             | Svar                                                                     |
|--------|-----------------|--------------------------------------------------------------------------|
| POST   | `/elever`       | `202` med eleven (inkl. ID) · `400` ved tomt navn/klasse · `503` hvis Kafka feiler |
| GET    | `/elever`       | `200` med liste (eventuelt `[]`) · `503` hvis `elev-service` er nede      |
| GET    | `/elever/{id}`  | `200` med eleven · `404` hvis den ikke finnes · `503` hvis `elev-service` er nede |

Eksempel på body for POST:

```json
{ "navn": "Ola Nordmann", "klasse": "10A" }
```

### elev-service (port 8081)

| Metode | Sti             | Svar                                   |
|--------|-----------------|----------------------------------------|
| GET    | `/elever`       | `200` med alle elever fra databasen (eventuelt `[]`) |
| GET    | `/elever/{id}`  | `200` med eleven · `404` hvis den ikke finnes i databasen |

## Konfigurasjon

Adresser og navn ligger i `src/main/resources/application.properties`, ikke i koden.

**elev-api**

```properties
spring.kafka.bootstrap-servers=localhost:9092
elev.kafka.topic=elever
elev.service.url=http://localhost:8081
```

**elev-service** (i tillegg til databaseoppsettet over)

```properties
server.port=8081
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=elev-service
spring.kafka.consumer.auto-offset-reset=earliest
elev.kafka.topic=elever
```

## Lagring i minnet vs. i database

|                     | I minnet (før)                  | I database (nå)                           |
|---------------------|---------------------------------|-------------------------------------------|
| Hvor data ligger    | RAM, inne i Java-prosessen       | PostgreSQL-server, på disk i et Docker-volum |
| Etter omstart       | Tomt                            | Alt er der fortsatt                        |
| Hastighet           | Raskest                         | Litt tregere (disk + SQL)                  |
| Regler (unik ID)    | Må skrives i koden selv          | Håndheves av databasen (primærnøkkel)      |
| Se innholdet utenfra | Nei                            | Ja, med `psql`, IntelliJ og SQL            |

## Merk

- Elevene overlever omstart av `elev-service` fordi de ligger i databasen – ikke fordi Kafka-meldingene leses på nytt.
- Kafka husker hvor consumer groupen `elev-service` har lest til. Ved omstart leses bare nye meldinger. Elever som opprettes mens `elev-service` er nede, blir lest og lagret når den starter igjen.
