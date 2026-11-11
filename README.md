# Elev-system: elev-api og elev-service

To Spring Boot-tjenester skrevet i Kotlin som samarbeider via **Kafka** og **HTTP**.
Ingen database brukes – elevene lagres i minnet i `elev-service`.

## Arkitektur

```
Opprette elev (asynkront):
Klient ──HTTP POST──▶ elev-api ──Kafka (topic "elever")──▶ elev-service ──▶ minne

Hente elever (synkront):
Klient ──HTTP GET───▶ elev-api ──HTTP GET──▶ elev-service ──▶ leser fra minne
```

| Tjeneste       | Port | Ansvar                                                                 |
|----------------|------|------------------------------------------------------------------------|
| `elev-api`     | 8080 | Eneste inngang for klienten. Validerer, genererer ID, sender til Kafka, henter fra `elev-service`. Lagrer ingenting selv. |
| `elev-service` | 8081 | Leser elever fra Kafka og lagrer dem i minnet. Tilbyr GET-endepunkter. |
| Kafka          | 9092 | Meldingskø mellom tjenestene (kjører i Docker).                        |

## Forutsetninger

- Java 25 (JDK)
- Docker Desktop

## Slik starter du

Start i denne rekkefølgen, fra mappen `oppgave`:

**1. Start Kafka**

```bash
docker compose up -d
docker ps          # kafka-containeren skal ha status "Up"
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

**Stoppe:** `Ctrl + C` i hver terminal, og `docker compose down` for Kafka.

## Kommunikasjon: Kafka vs. HTTP

### Over Kafka: opprettelse av elever

`POST /elever` i `elev-api` validerer `navn` og `klasse`, genererer en unik ID og publiserer hele eleven som JSON på topicen `elever`, med elevens ID som meldingsnøkkel. `elev-api` svarer først når Kafka har bekreftet mottak.

`elev-service` lytter på topicen med consumer group `elev-service`, leser JSON-meldingen, lagrer eleven i minnet med samme ID og logger ID-en.

**Hvorfor Kafka her?** Kommunikasjonen er asynkron. `elev-api` er ikke avhengig av at `elev-service` kjører akkurat nå – meldingen venter i Kafka til den blir lest. Ingen elever går tapt selv om `elev-service` er nede.

Derfor svarer `POST` med **202 Accepted** og ikke 201 Created: eleven er mottatt og sendt til behandling, men ikke nødvendigvis lagret ennå. En `GET` rett etter `POST` kan derfor gi 404 et kort øyeblikk.

### Over HTTP: henting av elever

`GET /elever` og `GET /elever/{id}` i `elev-api` gjør et HTTP-kall til `elev-service` og returnerer svaret til klienten. GET sender ingen Kafka-meldinger.

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
| GET    | `/elever`       | `200` med alle lagrede elever          |
| GET    | `/elever/{id}`  | `200` med eleven · `404` hvis den ikke finnes |

## Konfigurasjon

Adresser og navn ligger i `src/main/resources/application.properties`, ikke i koden.

**elev-api**

```properties
spring.kafka.bootstrap-servers=localhost:9092
elev.kafka.topic=elever
elev.service.url=http://localhost:8081
```

**elev-service**

```properties
server.port=8081
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=elev-service
spring.kafka.consumer.auto-offset-reset=earliest
elev.kafka.topic=elever
```

## Merk

- Data i `elev-service` forsvinner når tjenesten starter på nytt (lagres kun i minnet).
- Kafka husker hvor consumer groupen `elev-service` har lest til. Elever som opprettes mens `elev-service` er nede, blir lest og lagret når den starter igjen.
