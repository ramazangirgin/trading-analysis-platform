# Backend: Java formatting

Every Java file of the backend (`artifact/backend`, every module, main and test) is formatted by
[Spotless](https://github.com/diffplug/spotless) with
[Palantir Java Format](https://github.com/palantir/palantir-java-format). Nobody formats by hand and
reviews do not discuss layout: the formatter's output is the convention. Checkstyle
([rules](backend-java-checkstyle.md)) leaves layout to the formatter, so the two never disagree.

## Rules

The formatter has no options; it decides all of them:

- 120 characters per line (the same limit as Checkstyle's `LineLength`), 4-space indentation,
  8 spaces for continuation lines.
- Import order: static imports first, a blank line, then the others; each group sorted, with no
  blank lines inside a group. Unused imports are removed.
- No trailing whitespace; every file ends with a newline.
- Long argument lists, annotations with several attributes and method chains are wrapped one item per
  line once they do not fit on one.

A line the formatter cannot shorten (a long text block line, a string literal) still has to stay
within Checkstyle's 120 characters: move the value into a local variable or constant, or suppress
`LineLength` where it occurs ([Checkstyle](backend-java-checkstyle.md#suppressing-a-finding)).

Generated sources (`build/generated/`, MapStruct `*Impl`) are not formatted.

## Where it runs

| Where | Command | Files |
|---|---|---|
| Build | `spotlessCheck` in every backend module, part of `./gradlew build` / `mise run build` | all |
| CI | *Backend and frontend*: a *Formatting* step (`mise run format-check`) before the build, so an unformatted file fails fast, even when the hook was skipped | all |
| By hand | `mise run check` (with the other static checks) or `mise run format-check` | all |
| Git hook | pre-commit, when `.java` files under `artifact/backend/` are staged: `spotlessCheck -PspotlessRatchetFrom=HEAD`, in the same Gradle run as Checkstyle and ArchUnit | changed since `HEAD` |

`-PspotlessRatchetFrom=<git ref>` limits Spotless to files that differ from that ref, so the hook
checks the files a commit changes and not the whole backend. Like the hook's other Gradle checks it
reads the working tree, so an unstaged edit counts too.

The setup lives in the convention plugin
[`tradinganalysisplatform.java-library`](../../build-logic/src/main/kotlin/tradinganalysisplatform.java-library.gradle.kts),
so a new module gets it. The versions are `spotless` and `palantirJavaFormat` in
[`gradle/libs.versions.toml`](../../gradle/libs.versions.toml).

## Fixing a finding

```sh
mise run format                          # Java (spotlessApply) and frontend (Prettier), every file
./gradlew spotlessApply                  # Java only
./gradlew spotlessApply -PspotlessRatchetFrom=HEAD   # Java files changed since HEAD only
```

`spotlessCheck` prints the diff it expects; `spotlessApply` writes it.

## Why Palantir Java Format

It is the only formatter without settings that matches the layout the code already had and
Checkstyle's limit: 120-character lines and 4-space indentation. It is a fork of google-java-format,
so it is just as deterministic, but it keeps lambdas and short method chains on fewer lines.
The cost: it has fewer users than google-java-format, and where we disagree with its output there is
nothing to configure.

| Alternative | Why not |
|---|---|
| google-java-format (Google style) | 2-space indentation would re-indent every line, and its 100-column limit wraps far more than today and clashes with Checkstyle's 120 |
| google-java-format, AOSP style | 4-space indentation, but the same 100-column limit |
| Eclipse JDT formatter with a checked-in profile | Every detail is configurable, so every setting is a possible review discussion; a long XML profile to own; output changes between Eclipse versions |
| Prettier Java (`prettier-plugin-java`) | Would match the frontend's tool, but needs Node.js in the Java build and is little used for Java |

A new formatter version can reformat unchanged files: bump `palantirJavaFormat` in a pull request of
its own, together with the `spotlessApply` it causes.

## In the IDE

IntelliJ: install the *palantir-java-format* plugin and enable it (Settings → palantir-java-format).
The IDE's *Reformat Code* then produces the same output as the build. Without the plugin, run
`mise run format` before committing.
