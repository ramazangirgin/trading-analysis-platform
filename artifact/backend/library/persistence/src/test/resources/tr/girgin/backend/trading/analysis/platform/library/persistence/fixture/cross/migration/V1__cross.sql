-- Test fixture of DomainMigrationIsolationCheckTest, for a domain whose schema is "SAMPLE": a foreign key
-- into the schema of another, made-up domain "OTHER", which does not exist in the isolated database.

CREATE TABLE "SAMPLE"."CROSS_ORDERS" (
    "ID"        TEXT NOT NULL,
    "PARENT_ID" TEXT NOT NULL,
    CONSTRAINT "CROSS_ORDERS_PK" PRIMARY KEY ("ID"),
    CONSTRAINT "CROSS_ORDERS_PARENT_ID_FK" FOREIGN KEY ("PARENT_ID") REFERENCES "OTHER"."PARENTS" ("ID")
);
