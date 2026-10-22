# Plan: Investment disclaimer in the header, on an About page and in reports

- **Issue**: #195 (Investment disclaimer in the header and on the generic pages: analyses are used at the user's own risk)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Every page, the login page included, shows a short, muted disclaimer in the header: the analyses are
not investment advice, acting on them is at the user's own risk, and the platform accepts no
responsibility for any loss. A new About page shows the full disclaimer. A report shows it at its
end, on the report pages and in every export (Markdown, HTML, print), in the report's language
rather than the screen language. The wording lives in one place (`app.disclaimer` in `en.json` and
`tr.json`) behind one shared component.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| frontend | [frontend-folder-structure.md](../docs/coding-convention/frontend-folder-structure.md): the generic component goes to `shared/ui/`, the page to `pages/`, the route to `app/router/index.ts`, translations to `shared/i18n/locales/*.json`, pure logic to a feature's `model/` with a `*.spec.ts` next to it; pages reach `features/reports` through its `index.ts` only. [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md): no comments that restate the code. |
| e2e | Playwright tests in `e2e/tests/*.e2e.ts`, screenshots in `e2e/screenshots/*.shot.ts`. |
| docs | Root `README.md` describes every page and shows its screenshot. |
| backend | None: no API, schema or security change (the SPA fallback already serves `/about`). |

## Design

**Wording.** `app.disclaimer` in `shared/i18n/locales/en.json` and `tr.json` with the texts of the
issue. The existing `locales.spec.ts` keeps both files in step.

**Component.** `shared/ui/DisclaimerNotice.vue`: renders `t('app.disclaimer')` as a paragraph with
`role="note"`. Props: `locale?: AppLocale` (default: the screen language, which follows the language
switcher) and `compact?: boolean` (small, muted header style). With a `locale` it calls
`t('app.disclaimer', {}, { locale })`, so a report can show it in its own language.

**Report language to locale.** `shared/i18n/index.ts` gets `localeForLanguage(language)`, the inverse
of `outputLanguageFor`: `'Turkish'` gives `'tr'`, anything else `'en'`. The report pages pass
`localeForLanguage(language)` to the component. A report whose language is still unknown (null,
before it loads) shows nothing.

**Header.** In `app/App.vue`, `NLayoutHeader` becomes the header row plus a second line with
`<DisclaimerNotice compact />`, so it sits under the title and navigation on every page, including
the bare login page. Small font, muted colour, wraps on a phone, no horizontal overflow.

**About page.** `pages/AboutPage.vue`, route `/about` (`name: 'about'`), a short page with the full
disclaimer (the same component, not compact). It needs no permission and no API call. The router's
"no session goes to login" guard lets a route with `meta: { public: true }` through, so the page also
opens for a visitor who is not logged in; the login page links to it and so does the footer (next to
the version), so every user finds it.

**Reports.**
- On screen: `ReportsPage.vue` and `RunDetailPage.vue` show `<DisclaimerNotice :locale="…">` after
  the last report part, with the language each already computes (`language`, `outputLanguage`).
- Export: `ExportDocument` (`features/reports/model/reportDocument.ts`) gets a `disclaimer: string`.
  `exportMarkdown` ends with a horizontal rule and the disclaimer in italics; `exportHtml` renders it
  from the Markdown, with a small style rule so it prints at the end. `useReportExport` fills it from
  `t('app.disclaimer', {}, { locale: localeForLanguage(language) })`, the same language the export's
  report text is localised in.

## Work packages

### WP1: Wording, shared component and report language helper

- **Status**: done
- **Depends on**: none
- **Files**: `artifact/frontend/src/shared/i18n/locales/en.json`, `tr.json`,
  `artifact/frontend/src/shared/i18n/index.ts`, `artifact/frontend/src/shared/i18n/index.spec.ts`,
  `artifact/frontend/src/shared/ui/DisclaimerNotice.vue`
- **Steps**:
  - [x] Add `app.disclaimer` (EN and TR texts of the issue) and the About page's title and link
        labels (`about.title`, `nav.about`).
  - [x] Add `localeForLanguage` to `shared/i18n/index.ts`.
  - [x] Create `DisclaimerNotice.vue` with the `locale` and `compact` props.
- **Tests**: `index.spec.ts`: `localeForLanguage('Turkish')` is `'tr'`, `'English'` and unknown
  values are `'en'`. `locales.spec.ts` (existing) proves both languages define the same keys.

### WP2: Header, About page and route

- **Status**: done
- **Depends on**: WP1
- **Files**: `artifact/frontend/src/app/App.vue`, `artifact/frontend/src/app/router/index.ts`,
  `artifact/frontend/src/pages/AboutPage.vue`, `artifact/frontend/src/pages/LoginPage.vue`
- **Steps**:
  - [x] Add the compact disclaimer line to the header in `App.vue`, and an About link in the footer.
  - [x] Add the `/about` route with `meta.public`; skip the login redirect for public routes (and
        the password and permission gates, which have nothing to gate here).
  - [x] Create `AboutPage.vue`: title and the full disclaimer.
  - [x] Link to the About page from the login page.
- **Tests**: a router spec if one exists for the guard, else covered by the e2e below; a Vitest
  component test for `DisclaimerNotice` (default locale follows the screen language, `locale` prop
  overrides it).

### WP3: Disclaimer in reports and exports

- **Status**: done
- **Depends on**: WP1
- **Files**: `artifact/frontend/src/features/reports/model/reportDocument.ts`,
  `reportDocument.spec.ts`, `artifact/frontend/src/features/reports/composables/useReportExport.ts`,
  `artifact/frontend/src/pages/ReportsPage.vue`, `artifact/frontend/src/pages/RunDetailPage.vue`
- **Steps**:
  - [x] Add `disclaimer` to `ExportDocument`; `exportMarkdown` and `exportHtml` end with it.
  - [x] Fill it in `useReportExport` in the report's language.
  - [x] Show the component after the parts on both report pages, in the report's language.
- **Tests**: `reportDocument.spec.ts`: the Markdown and the HTML export end with the disclaimer
  given in the document.

### WP4: e2e and screenshots

- **Status**: done
- **Depends on**: WP2, WP3
- **Files**: `e2e/tests/disclaimer.e2e.ts`, `e2e/screenshots/login.shot.ts`,
  `e2e/screenshots/pages.shot.ts`, `docs/screenshots/*.png`
- **Steps**:
  - [x] e2e: the disclaimer is visible on the login page, the dashboard, the About page and a
        report; switching the screen language switches the header text; a Markdown export ends with
        the disclaimer.
  - [x] e2e: with the screen in English, a Turkish report (analysis started with
        `outputLanguage: 'Turkish'`) shows the Turkish disclaimer. If the replayed recording cannot
        produce a Turkish report, the test uses the recording with the language recorded as Turkish;
        see the open question.
  - [x] Add an About screenshot shot; retake the screenshots the header changes (see Docs).
- **Tests**: the e2e tests above prove the issue's acceptance criteria; a phone-width viewport
  check (390 px) asserts the page has no horizontal scroll with the header disclaimer shown.

## Tests

Unit and component tests (Vitest) for the helper, the component and the export; e2e for the login
page, dashboard, About page, an English report, a Turkish report under an English screen, the
language switch and the phone-width header. `mise run check`, `pnpm test` and `mise run e2e` must
pass.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, section on reports and exports | Say that every report and export ends with the investment disclaimer, in the report's language. |
| Text | `README.md`, new short paragraph near the login page / usage section | Describe the header disclaimer and the About page (`/about`, open without login). |
| Text | `docs/coding-convention/frontend-folder-structure.md` | Add `DisclaimerNotice` to the `shared/ui/` examples. |
| Screenshot | `docs/screenshots/about.png` (new) | The About page, English, logged in as the e2e admin; shown in the README paragraph above. |
| Screenshot | every page screenshot under `docs/screenshots/` | Retaken with `mise run screenshots`: the header now has the disclaimer line. |

## Out of scope

- Terms of use, a help page, and the public main page of #194: they do not exist yet; when they do,
  they add `<DisclaimerNotice />` (this plan makes that one line).
- Storing a user's acceptance of the disclaimer, or a consent dialog.
- Legal review of the wording: the text of the issue is used as given.

## Decisions

- The app has no info / about page, so this plan adds the minimal public `/about` page (linked from
  the footer and the login page). Approved by the developer.
- The e2e Turkish report uses a Turkish `outputLanguage` on the replayed run; if the recording's
  text stays English, that test checks the disclaimer's language only. Approved by the developer.
