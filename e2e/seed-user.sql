-- The user of the end-to-end tests when login is on (start-platform.sh runs this file against the
-- throwaway database, once Flyway has created the tables). Username: e2e. Password: e2e-test-password.
-- Safe to keep in the repository: the database is a disposable container that is removed when the
-- tests stop, and the user exists only there. No role row: no endpoint checks permissions yet (#87).
INSERT INTO "IDENTITY"."USERS" ("ID", "USERNAME", "PASSWORD_HASH", "ENABLED", "MUST_CHANGE_PASSWORD",
                                "CREATED_AT", "UPDATED_AT")
VALUES ('u_e2e', 'e2e', '{bcrypt}$2a$10$tq5s1FJ41.TAK29Idv2B4uy9DtQ7Vyi9TbBrezXJW4xHn3R9INygi', TRUE, FALSE,
        NOW(), NOW());
