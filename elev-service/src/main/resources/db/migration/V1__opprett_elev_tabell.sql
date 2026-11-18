-- V1: Oppretter tabellen for elever.
-- id kommer fra Kafka-meldingen (UUID) og er primærnøkkel,
-- så det kan bare finnes én rad per elev-ID.
CREATE TABLE elev (
    id     UUID         NOT NULL,
    navn   VARCHAR(255) NOT NULL,
    klasse VARCHAR(255) NOT NULL,
    CONSTRAINT pk_elev PRIMARY KEY (id)
);
