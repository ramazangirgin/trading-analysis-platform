-- Migration of the made-up domain "sample" (a test fixture of the conventions test): everything is in
-- its schema "SAMPLE", Flyway creates the schema, versions start at V1.

CREATE TYPE "SAMPLE"."SAMPLE_MOOD" AS ENUM ('HAPPY', 'SAD');

CREATE TABLE "SAMPLE"."SAMPLES" (
    "ID"    TEXT                     NOT NULL,
    "MOOD"  "SAMPLE"."SAMPLE_MOOD"[] NOT NULL DEFAULT '{}',
    CONSTRAINT "SAMPLES_PK" PRIMARY KEY ("ID")
);

CREATE TABLE "SAMPLE"."SAMPLE_TAGS" (
    "SAMPLE_ID" TEXT NOT NULL,
    "TAG"       TEXT NOT NULL,
    CONSTRAINT "SAMPLE_TAGS_PK" PRIMARY KEY ("SAMPLE_ID", "TAG"),
    CONSTRAINT "SAMPLE_TAGS_SAMPLE_ID_FK" FOREIGN KEY ("SAMPLE_ID") REFERENCES "SAMPLE"."SAMPLES" ("ID")
);

CREATE INDEX "SAMPLE_TAGS_TAG_IDX" ON "SAMPLE"."SAMPLE_TAGS" ("TAG");
