-- Test fixture of DomainMigrationIsolationCheckTest, for a domain whose schema is "SAMPLE": a column of a
-- type that belongs to the schema of another, made-up domain "OTHER", which does not exist in the
-- isolated database.

CREATE TABLE "SAMPLE"."CROSSTYPE_ORDERS" (
    "ID"     TEXT                   NOT NULL,
    "STATUS" "OTHER"."OTHER_STATUS" NOT NULL,
    CONSTRAINT "CROSSTYPE_ORDERS_PK" PRIMARY KEY ("ID")
);
