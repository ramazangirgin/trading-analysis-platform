# User guide

Steps 1 and 2 (prerequisites and starting the platform) are in the [README's Quick start](../README.md#quick-start); this guide continues from the running platform.

## 3. Log in

The first start creates the user `admin` and prints a generated password once in the terminal:

```
First start: created the admin user "admin" with the generated password <password>. It is shown only this once; change it after the first login.
```

Log in at **http://127.0.0.1:8080** with it. The first admin has the ADMIN role, with every
permission. To choose the name and password instead, set
`PLATFORM_ADMIN_USERNAME` and `PLATFORM_ADMIN_PASSWORD` before the first start; the password must
meet the password policy (at least 12 characters, not a common password, not the username), or the
platform does not start and says why. Later starts change nothing. A generated password is flagged
to be changed: after logging in with it, the UI asks for a new password before anything else. If
you lost it while `admin` is still the only user, delete the user
(`DELETE FROM "IDENTITY"."USERS";` in the database) and restart to get a new one.

Every page shows a short investment disclaimer under the header; the About page (`/about`, linked
from the footer and the login page) shows the full text and opens without a login.

![The login page, with the link back to the landing page](screenshots/login.png)

Without a login, the main page is a **landing page**: what the platform does, a **Log in** button
and cards of the latest analyses that their owners made **Partial** (see
[step 6](#6-watch-it-and-read-the-results)), each with the start of its text faded and locked. A card opens
the **preview page**: the parts the owner allows in full, the other sections as blurred
placeholders with their titles, and a **Log in** button. No text of a locked section ever reaches
the visitor's browser. After logging in, the visitor lands on the full analysis they were looking
at. A logged-in user who opens `/` sees the dashboard, and `/preview/<id>` leads to the analysis
itself. There is no self-registration yet: only an admin creates users.

![The landing page as a visitor sees it](screenshots/landing.png)

![The preview page of a partial analysis](screenshots/preview.png)

More users are added in the UI, under **Admin → Users**: **New user** takes a username, roles and
a one-time password, generated or entered. A generated one is shown once, to pass on; the user
must change it at the first login. Per row an admin edits the roles, disables or enables the
account (there is no delete), resets the password and unlocks a locked account. The UI explains
beforehand what the platform refuses: disabling yourself, removing your own ADMIN role, or
disabling or demoting the last enabled ADMIN. The page needs the permission `user:read`, its
changes `user:manage`.

![Admin → Users: an admin, an analyst who can also publish and a disabled viewer](screenshots/users.png)

**Admin → Roles** (permission `role:manage`) lists every role with its permissions and its number
of users. **New role** takes a name, a description and the permissions, chosen from a checklist
grouped by area (analysis, presets, settings, users and roles, audit). **Edit** changes a custom
role's name, description and permissions; **Delete** removes it. On the built-in roles the name and
description cannot be changed, ADMIN keeps all its permissions, and none of them can be deleted; a
role that users still hold cannot be deleted either. The page explains this beforehand. A change to
a role applies to the users who are logged in on their next request, without logging in again.

![Admin → Roles: the four built-in roles and a custom read-only role](screenshots/roles.png)

## 4. Add an API key

Open **Settings**, paste the key next to its provider and press **Save**. Keys are stored in
`~/.tradingagents-platform/secrets.env` (owner-only) and are never shown back. A key already in
`artifact/ta-runner/.env` is picked up too (read-only).

The **Analysis defaults** card below the keys holds the provider, the deep- and quick-thinking
model and the two round counts that every analysis uses. Only users with
`analysis:defaults:manage` (ADMIN) see it. Until an admin saves once, the catalog's defaults apply
with one round each. If another admin saved meanwhile, the save fails with a message and the card
reloads their values.

![Settings: provider API keys and analysis defaults](screenshots/settings.png)

## 5. Start an analysis

Open **New analysis** and fill in:

| Field | What to enter |
|---|---|
| Ticker | Any Yahoo Finance symbol: `NVDA`, `THYAO.IS`, `BTC-USD` (crypto pairs switch the asset type automatically) |
| Analysis date | Defaults to the last weekday; future dates are rejected |
| Analysts | Market, Sentiment, News, Fundamentals — fewer analysts means a faster, cheaper run |
| Provider, models | A deep-thinking model (research/portfolio managers) and a quick-thinking one (everyone else). They start from the platform's analysis defaults: read-only ("Set by the administrator") without `analysis:override-models`, editable with it, where **Reset to defaults** restores them. A model id that is not listed can be typed only for providers that allow custom ids |
| Debate rounds | The debate and risk rounds (1 to 10) come from the same defaults; every extra round adds LLM calls |
| Report language | The language of the reports and the decision; follows the UI language by default |

Press **Start analysis**. **Save as preset** keeps the provider, model and analyst choices for next
time; **Settings** lists your own presets with what each holds, and renames or deletes them. With
`preset:read:all` (ADMIN) an **All users** switch lists everyone's presets, with an **Owner** column.
Without `analysis:override-models`, applying a preset leaves the provider, models and rounds at the
platform values and saving one stores the other fields only.
Renaming a preset that someone else changed meanwhile fails with a message and the list reloads.
**Run again** keeps the values of the original analysis.

![New analysis form](screenshots/new-analysis.png)

![New analysis form for a user without analysis:override-models](screenshots/new-analysis-readonly.png)

![Presets in Settings](screenshots/presets.png)

## 6. Watch it and read the results

The analysis page updates live: the agent pipeline (analysts → research debate → trader → risk
debate → portfolio manager), a feed of agent messages and tool calls, the runner log, and token
counts. A full run with all four analysts can take 20 minutes or more, depending on the model and
the debate rounds (the example above: 41 LLM calls, ~1.1M input tokens, 18 minutes). **Stop** ends it; **Run again**
repeats it with the same settings.

![A running analysis: pipeline status and the live feed](screenshots/run-live.png)

When it finishes, the decision card shows the portfolio manager's rating (Buy / Overweight / Hold /
Underweight / Sell) with its reasoning, and the tabs hold everything behind it:

| Reports — one per analyst and manager | Debates — bull vs. bear, then the risk debate |
|---|---|
| ![Reports tab](screenshots/run-reports.png) | ![Debates tab](screenshots/run-debates.png) |

![Stats tab: calls, tokens, cost, duration and the run's settings](screenshots/run-stats.png)

The cost is worked out per LLM call from the provider's published prices
([LLM prices](configuration.md#llm-prices)); a model without a known price shows no cost.

The market report opens with a price chart — close, 10 EMA, 50 SMA and 200 SMA with volume, over
3 months to a year up to the analysis date, from the price data TradingAgents itself downloaded —
and report tables that are series over periods (quarterly or yearly figures) get a chart beside them:

![A fundamentals table with its chart](screenshots/run-table-chart.png)

A user with the permission `analysis:share` (ADMIN, or anyone with the PUBLISHER role) can make
their own completed analysis public with the visibility select on its page (**Private**, **Public
(logged-in users)** or **Partial (preview for visitors)**), after a confirmation. With **Public**,
everyone who may read analyses sees it under **Shared with everyone**, read-only: they can open,
read, compare and export it, but not stop it, run it again or read its log. With **Partial**, the
logged-in users see it the same way, and visitors without a login see a preview of it on the
landing page (see [step 3](#3-log-in)); the confirmation says that anyone on the internet will see
it. The owner makes it private again at any time, also after losing the permission, and an admin
can make any public or partial analysis private with the same select.

A visitor who is not logged in gets a preview only, through `GET /api/public/analyses`: the ticker,
the date, the rating, the report sections the platform is configured to show (by default the market
report) and the names of the sections that stay hidden. The hidden text is cut on the server and
never sent. Only the owner makes an analysis partial; an admin can move a partial analysis to public
or private, but never makes someone else's analysis partial. Which sections a visitor reads is a
setting (see [Login](login-and-backend.md#login)) and needs a restart to change.

![The confirmation before an analysis is made partial](screenshots/run-share.png)

## 7. Past analyses

**Analyses** lists your own runs with their analysis date and start time, decision, model and
duration, filterable by ticker and status, under the tab **My analyses**. **Shared with everyone**
lists the analyses other users have made public (see [step 6](#6-watch-it-and-read-the-results)),
and a user with `analysis:read:all` (ADMIN) also gets an **All users** tab with everyone's runs.
Both show an **Owner** column, and a public analysis carries a **Public** tag and a partial one a **Partial** tag.
Runs made outside the platform are imported (read-only) and show up by themselves within about
10 seconds, as the data dir is watched; they belong to the first admin. **Scan data dir** forces a
rescan:

- reports the TradingAgents CLI (or another UI) left in `~/.tradingagents/logs`;
- a third-party UI's run history, `~/.tradingagents/runs.json`: its failed runs with their errors,
  and the models, token counts, cost and timing of the finished ones;
- deep-analysis write-ups, `~/.tradingagents/reports/<TICKER>_deep_<DATE>.md`, as a
  *Deep analysis* section of that ticker and date's report.

A run whose report files are still being written is not shown as failed; it appears once it
finishes.

![Analyses list of all users with the Owner column, a public run and two runs ticked for comparing](screenshots/analyses.png)

To compare runs, tick two to four of them and press **Compare**: the compare view shows them side
by side, one column per run, with the settings (ticker, date, provider and models, analysts,
debate rounds), the decision, the stats and the report sections. Rows whose values differ between
the runs are highlighted, so the same ticker run with another model or date can be compared at a
glance. The run ids are in the page's URL, so a comparison can be bookmarked or shared.

![Compare view: two runs of the same ticker with different models](screenshots/compare.png)

The **Dashboard** shows the running analyses and the latest decisions at a glance.

![Dashboard](screenshots/dashboard.png)

## 8. Reports and export

**Reports** gathers every finished report in one place: the list of tickers and dates on the left,
the report's contents in the middle (each section with its headings, then the debates), and the
report itself with its charts on the right. The data dir keeps one report per ticker and date, so
when a ticker and date ran several times, this is the newest run's report.

**Markdown**, **HTML** and **PDF** export the open report: the HTML file is a single page that
opens anywhere, and PDF goes through the browser's print dialog (*Save as PDF*). On a phone the page
folds into one column, with the list as a picker and the contents folded above the report.

Every report, on screen and in every export, ends with the investment disclaimer, in the report's
language rather than the screen language.

![Reports: list, contents and the report](screenshots/reports.png)

## Language

The UI is in English. Reports are written in the language chosen in the New Analysis form's
"Report language" field; the analysis page shows it as a tag.
