# Shared library: persistence

`:backend:library:persistence` holds the technical persistence code that more than one module needs,
and the shared test code of the persistence adapters. Like every shared library it holds no domain
type and no business rule
([package structure](../../../../docs/coding-convention/backend-java-package-structure.md#shared-libraries)).
The persistence conventions it supports are in
[backend-java-persistence.md](../../../../docs/coding-convention/backend-java-persistence.md).

## What it holds

| Source set | Classes | Used by |
|---|---|---|
| `main` | `JpaAuditingConfiguration`, `ClockDateTimeProvider`: JPA auditing fed from the application's `Clock` | `:backend` at runtime |
| `testFixtures` | `@JpaAdapterTest`, `PostgresTestDatabase`, `MutableTestClock`: the wiring of an adapter test | the persistence adapters' tests |
| `testFixtures` | `DomainPersistenceConventionsTest` and its checks (`EntitySchemaRule`, `DomainMigrationIsolationCheck`, `DatabaseNamingCheck`): the schema rules of one domain, extended once by each domain with persistence | the persistence adapters' tests |
| `test` | The proofs that each check fails, against fixtures of made-up domains | this module only |

## Decision: the library knows no domain

Nothing in this module names a domain of the platform: no domain package or class, no schema, table,
column or type of a domain (`ANALYSIS`, `"USERS"`, `"ANALYSIS_STATUS"`, a ticker), not even in a
test fixture, a Javadoc example or a comment. Each domain brings its own details to the library's
code, never the other way round:

- **The domain passes them in.** A domain's `<Domain>PersistenceConventionsTest`, in its own
  persistence adapter, hands `DomainPersistenceConventionsTest` its configuration class, its schema
  and its Flyway bean. The checks work on whatever they are given.
- **The library's own tests use made-up domains.** The proofs run against fixtures of a domain
  `SAMPLE` (`fixture/sample`, the correct one, and `fixture/entities`, `fixture/stray`), a second
  domain `OTHER` that a broken fixture reaches into (`fixture/cross`, `fixture/crosstype`), and a
  schema `SAMPLE_NAMING` for the naming rules (`fixture/naming`). Their tables and columns have
  neutral names (`ORDERS`, `PARENT_ID`, `LABEL`, `MOOD`).
- **Examples point at the made-up domain.** A Javadoc example shows `SamplePersistenceConventionsTest`,
  not a real domain's test.

Why:

- A library with domain names in it is coupled to those domains, even if only through a test: renaming
  or removing a domain would mean changing the library, and the library could not be reused for a
  new domain without reading around another domain's details.
- A check proved against a real domain's names proves less: a fixture that reaches into `"IDENTITY"`
  passes for a reason that depends on which domains exist. A made-up `"OTHER"` schema makes the case
  explicit, whatever domains the platform has.
- It keeps one direction of knowledge: domains depend on the library, the library on no domain.
  `libraries_depend_only_on_libraries` (ArchUnit) enforces that for code; names in SQL, strings and
  comments are outside its reach, so this rule is kept by review.

When adding a check or a fixture here, invent the names (`SAMPLE`, `OTHER`, `ORDERS`), and put any
test that needs a real domain's schema in that domain's adapter module, as its conventions test does.
