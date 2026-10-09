-- Test fixture of DomainMigrationIsolationCheckTest, for a domain whose schema is "SAMPLE": a foreign key
-- into another domain's schema, which does not exist in the isolated database.

CREATE TABLE "SAMPLE"."CROSS_ORDERS" (
    "ID"      TEXT NOT NULL,
    "USER_ID" TEXT NOT NULL,
    CONSTRAINT "CROSS_ORDERS_PK" PRIMARY KEY ("ID"),
    CONSTRAINT "CROSS_ORDERS_USER_ID_FK" FOREIGN KEY ("USER_ID") REFERENCES "IDENTITY"."USERS" ("ID")
);
