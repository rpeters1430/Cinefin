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
available to your API key; leaving it unset uses the CLI default. Set
`GEMINI_ENABLED=false` to pause automation. Missing credentials fail with a clear
setup error. API quota/model failures appear in the workflow logs and publish no
partial result. AI requests can consume your Gemini API quota/billing.

## Behavior

| Trigger | Result |
| --- | --- |
| Issue opened/reopened | Suggested type/area labels and one updated triage comment |
| PR opened, reopened, updated, or marked ready | Code/security review and relevant area labels |
| `@gemini-cli /triage` on an issue | Rerun triage (verified write/maintain/admin permission required) |
| `@gemini-cli /review` on a PR | Rerun review (same permission requirement) |
| Manual workflow dispatch | Review or triage an existing number |

Commands must be the entire comment. Draft PRs and bot-authored issues are skipped.
PRs from bots and forks are reviewed using GitHub's patch data. Existing labels
are preserved; missing labels from the finite allowlist are created as needed.
Duplicates are suggestions, never automatic closures. Reviews use COMMENT,
include file/line findings and never approve, request changes, merge or push code.
An existing review for the same commit is not posted twice. To request a new
review after changes, push a new commit. Triage updates its existing bot comment.
The former `/fix`, `/approve`, `/deny` command files have been removed.

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
The default-branch scripts are checked out again at the exact recorded SHA for
publication. Results are discarded if the target changed while analysis ran.

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
