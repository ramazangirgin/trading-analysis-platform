-- Test fixture for ColumnTypeCheckTest, in the schema "SAMPLE_TYPES". One column per ruled-out type of
-- backend-java-persistence.md, and one table of allowed types that must produce none.

CREATE TYPE "SAMPLE_TYPES"."TYPES_MOOD" AS ENUM ('HAPPY', 'SAD');

CREATE TABLE "SAMPLE_TYPES"."TYPES_WRONG" (
    "ID"       TEXT                                 NOT NULL,
    "STAMP"    TIMESTAMP                            NOT NULL,
    "CLOCK"    TIMETZ                               NOT NULL,
    "NAME"     VARCHAR(20)                          NOT NULL,
    "CODE"     CHAR(3)                              NOT NULL,
    "RATIO"    REAL                                 NOT NULL,
    "RATE"     DOUBLE PRECISION                     NOT NULL,
    "PRICE"    MONEY                                NOT NULL,
    "SMALL"    SMALLINT                             NOT NULL,
    "PAYLOAD"  JSON                                 NOT NULL,
    "BLOB"     OID                                  NOT NULL,
    "REF"      UUID                                 NOT NULL,
    "DOC"      XML                                  NOT NULL,
    "STAMPS"   TIMESTAMP[]                          NOT NULL,
    "SEQ"      BIGINT GENERATED ALWAYS AS IDENTITY  NOT NULL,
    "COUNTER"  BIGSERIAL                            NOT NULL,
    CONSTRAINT "TYPES_WRONG_PK" PRIMARY KEY ("ID")
);

CREATE TABLE "SAMPLE_TYPES"."TYPES_RIGHT" (
    "ID"          TEXT                            NOT NULL,
    "LABEL"       TEXT                            NOT NULL,
    "ENABLED"     BOOLEAN                         NOT NULL,
    "COUNT"       INTEGER                         NOT NULL,
    "TOTAL"       BIGINT                          NOT NULL,
    "COST"        NUMERIC                         NOT NULL,
    "PRICE"       NUMERIC(12, 4)                  NOT NULL,
    "DUE_DATE"    DATE                            NOT NULL,
    "OPENS"       TIME                            NOT NULL,
    "CREATED_AT"  TIMESTAMPTZ                     NOT NULL,
    "ELAPSED"     INTERVAL                        NOT NULL,
    "DETAILS"     JSONB                           NOT NULL,
    "CONTENT"     BYTEA                           NOT NULL,
    "MOOD"        "SAMPLE_TYPES"."TYPES_MOOD"     NOT NULL,
    "MOODS"       "SAMPLE_TYPES"."TYPES_MOOD"[]   NOT NULL,
    "TAGS"        TEXT[]                          NOT NULL,
    "LAPS"        INTERVAL[]                      NOT NULL,
    CONSTRAINT "TYPES_RIGHT_PK" PRIMARY KEY ("ID")
);
