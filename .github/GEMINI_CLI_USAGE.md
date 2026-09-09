# Gemini CLI automation

## Setup

1. Add repository Actions secret **GEMINI_API_KEY** in Settings → Secrets and
   variables → Actions. Use a Gemini API key from https://aistudio.google.com/apikey.
2. Merge the workflow migration into the default branch. Issue/comment events
   and the trusted review scripts become available after merge.
3. Open an issue or non-draft PR, or use Actions → Gemini triage and PR review →
   Run workflow (select the default branch, task and issue/PR number).

The workflow uses GitHub's built-in token; no PAT, GitHub App or Firebase service
account is required. Optional repository variable `GEMINI_MODEL` selects a model
available to your API key (defaults to `gemini-3.8-flash` with automatic fallback to
`gemini-3.7-flash` if unavailable). Set `GEMINI_ENABLED=false` to pause automation.
Missing credentials fail with a clear setup error. Extensions and skills are
automatically cached in GitHub Actions to minimize latency and avoid network flakiness.

## Behavior

| Trigger | Result |
| --- | --- |
| Issue opened/reopened | Suggested type/area labels and one updated triage comment |
| PR opened, reopened, updated, or marked ready | Code/security review with direct file/line links and area labels |
| Reply with `@gemini-cli` on an issue | Context-aware triage rerun: parses comments, extracts verified details (versions, logs), and suggests potential architectural causes |
| `@gemini-cli /triage` on an issue | Rerun triage (allowed for the issue author or collaborators) |
| `@gemini-cli /review` on a PR | Rerun review (verified write/maintain/admin permission required) |
| Manual workflow dispatch | Review or triage an existing number |

Commands and replies can be commented with or without trailing newlines/whitespace.
When an issue author or collaborator replies to `@gemini-cli` with answers (e.g. server logs, device models, version numbers),
Gemini reads the comment thread, extracts verified context, proposes potential architectural causes, and updates the existing triage comment.
Draft PRs and bot-authored issues are skipped.
PRs from bots and forks are reviewed using GitHub's patch data. Source files (`.kt`, `.kts`, `.xml`, `.java`)
are prioritized over lockfiles and binary assets so that diff budgets are spent on code changes.
Existing labels are preserved; missing labels from the finite allowlist are created as needed.
Duplicates are suggestions, never automatic closures. Reviews use COMMENT, include clickable
file/line findings and collapsible coverage sections, and never approve, request changes, merge or push code.
An existing review for the same commit is not posted twice. To request a new
review after changes, push a new commit or trigger `@gemini-cli /review`. Triage updates its existing bot comment.
Every run produces an accessible GitHub Actions Step Summary for fast visibility.

## Which Setup Approach is Better to Use?

Depending on your team's goals, there are two primary architectures for Gemini in GitHub CI:

1. **Gemini CLI Action (`google-github-actions/run-gemini-cli`) [CURRENT & RECOMMENDED]**
   - **How it works:** Runs Google's official Gemini CLI engine in headless CI with sandboxed tools, extensions (`code-review`, `security`), and project skills (`android/skills`, `firebase/agent-skills`).
   - **Pros:** Full access to curated extensions and specialized Android/Firebase skills; headless sandbox security; uses official Google CLI tooling; runs offline without executing PR code.
   - **Best for:** Cinefin's current setup where deep Android domain skills (adaptive layouts, Media3, Intent security, R8) and security passes are needed.

2. **Direct Gemini API Action / Script (REST / `@google/genai` SDK)**
   - **How it works:** A single Node/Python script directly queries the Gemini API with the diff and posts comments via Octokit.
   - **Pros:** Slightly faster runner start (no CLI installation); smaller runner footprint; direct control over inline diff comment placement.
   - **Cons:** Does not have access to Gemini CLI extensions or multi-skill catalogs (`activate_skill`), losing Android/Firebase specialized domain rules.
   - **Verdict:** Use the current Gemini CLI architecture because Cinefin relies heavily on specific Android and Firebase guidance that the skills system provides.

## Extensions and skills

Pinned upstream revisions live in `.github/gemini/sources.json`.

- **gemini-cli-extensions/code-review:** installed as a local Gemini extension;
  the reviewer activates its code-review-commons skill.
- **gemini-cli-extensions/security:** installed as a local Gemini extension; the
  prompt reads its analysis command and applies its evidence/source-to-sink
  guidance in memory. Its standard `/security:analyze` command is interactive,
  so CI uses this headless adaptation. No exploit generation or patching.
- **firebase/agent-skills:** Firebase basics, Crashlytics and Remote Config skills
  are copied with references from the pinned official source. No Firebase MCP or
  cloud credentials are installed in automated review jobs.
- **android/skills:** the official Android CLI skill plus Intent security,
  adaptive UI, edge-to-edge, R8, testing, Media3 Cast and Navigation 3 guidance.
  CI copies these from the pinned source, preserving skill references. It does
  not install SDKs, boot emulators or execute Android CLI during static review.

For interactive Android development, install the official Android CLI following
https://developer.android.com/tools/agents/android-cli, then run:

```sh
android skills add --all --agent=gemini --project=.
android docs search --help
```

For full interactive extension use outside CI:

```sh
gemini extensions install https://github.com/gemini-cli-extensions/code-review
gemini extensions install https://github.com/gemini-cli-extensions/security
gemini extensions install https://github.com/firebase/agent-skills
```

The CI copies remove MCP server/hook registrations before installation. No
upstream build scripts are executed. Gemini gets only file-reading/search and
skill activation tools; it has no shell, network, file-writing or GitHub tools.
GitHub writes happen in a separate job with validated JSON and allowed labels.
PR code is never checked out or executed by the privileged review workflow.
Both jobs check out the same immutable `github.sha` directly from GitHub event
context, never an analysis-job output. For issue/comment/PR-target events this is
the default-branch event commit. Manual dispatch is restricted to the default
branch before checkout. See [GitHub's event reference changes](https://github.blog/changelog/2025-11-07-actions-pull_request_target-and-environment-branch-protections-changes/). Results are discarded if the target changed while analysis ran.

## Coverage and maintenance

Reviews are bounded to 300 files and 350,000 patch characters. Binary/missing or
truncated patches are explicitly marked as incomplete. Source files in the
review workspace are the default branch, not the proposed head. Findings are
advisory and must be checked against the PR. Android CI still runs the build,
unit tests and lint; dependency review remains the known-vulnerability check.
No claim of a complete security audit or executed tests is made by this workflow.

Action references are pinned and tracked by existing Renovate github-actions
support. CLI and guidance updates should be tested before updating their pins.
Workflow validation runs actionlint and tests output validation, command access,
draft handling, stale-result rejection and publication/deduplication.
Validated result artifacts expire after one day; full Gemini traces are not
uploaded. Do not make this optional AI workflow a required merge check until
credentials and quota have been confirmed with a live run.

## Migration cleanup

All tracked Claude workflows, `.claude` settings/agents and `CLAUDE.md` have been
removed. Historical attribution in old project documents is retained. Old
Anthropic/Claude repository secrets and any installed GitHub App can be removed
in repository/account settings after migration; those settings are separate
from tracked repository files and are not changed by this PR.
