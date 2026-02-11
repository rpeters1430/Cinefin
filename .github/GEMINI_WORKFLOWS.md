# 🤖 Gemini Automated Issue Workflows

This repository uses AI-powered GitHub workflows to automatically triage, plan, and fix issues using Google's Gemini AI.

## 📋 Overview

The Gemini workflow system consists of 5 interconnected workflows that automate the entire issue lifecycle:

1. **Triage** → Auto-label new issues
2. **Fix Plan** → Generate fix plans with `/fix` command
3. **Apply Fix** → Create PR with `/approve` command on issue
4. **Merge** → Merge PR with `/approve` command on PR
5. **Deny** → Cancel and close with `/deny` command

## 🔄 Workflow Sequence

```
Issue Created
    ↓
[🏷️ Auto-Triage] → Labels applied automatically
    ↓
User comments "/fix"
    ↓
[📋 Fix Plan] → Gemini analyzes and posts a plan
    ↓
User reviews plan
    ↓
    ├─→ Comment "/approve" on issue
    │       ↓
    │   [🔧 Apply Fix] → Creates branch + PR with fix
    │       ↓
    │   User reviews PR
    │       ↓
    │       ├─→ Comment "/approve" on PR
    │       │       ↓
    │       │   [✅ Merge] → Squash merges PR, closes issue
    │       │
    │       └─→ Comment "/deny" on PR
    │               ↓
    │           [🛑 Deny] → Closes PR
    │
    └─→ Comment "/deny" on issue
            ↓
        [🛑 Deny] → Closes issue
```

## 🏷️ Workflow 1: Auto-Triage (`gemini-triage.yml`)

**Trigger:** When an issue is opened or reopened

**What it does:**
- Analyzes the issue title and body using Gemini AI
- Automatically applies relevant labels from these categories:
  - **Type:** `type:bug`, `type:feature`, `type:enhancement`, `type:chore`, `type:question`, `type:documentation`
  - **Area:** `area:android`, `area:ui`, `area:media`, `area:network`, `area:auth`, `area:build`, `area:docs`, `area:testing`
  - **Priority:** `priority:critical`, `priority:high`, `priority:medium`, `priority:low`

**Example:**
```
Issue: "App crashes when playing video"
Labels applied: type:bug, area:media, priority:high
```

**Permissions required:** `issues: write`, `contents: read`

## 📋 Workflow 2: Fix Plan (`gemini-fix-plan.yml`)

**Trigger:** When someone comments `/fix` on an issue

**What it does:**
1. Verifies the commenter has permission (OWNER, MEMBER, or COLLABORATOR)
2. Uses Gemini to analyze the issue and generate a detailed fix plan
3. Posts the plan as a comment with:
   - Step-by-step implementation plan
   - Technical approach
   - Questions (if any clarification is needed)
   - Instructions for next steps
4. Adds `gemini:awaiting-approval` label

**Example output:**
```markdown
## 📋 Fix Plan
1. Update ExoPlayer configuration in VideoPlayerScreen.kt
2. Add error handling for unsupported codecs
3. Test with various video formats

## ⚙️ Implementation Approach
Modify the ExoPlayer initialization to include fallback codec support...

---
## 🚀 Next Steps
- Comment `/approve` to implement this fix and create a PR
- Comment `/deny` to cancel and close the issue
```

**Permissions required:** `issues: write`, `contents: read`

## 🔧 Workflow 3: Apply Fix (`gemini-apply-fix.yml`)

**Trigger:** When someone comments `/approve` (or `@gemini-cli /approve`) on an issue with `gemini:awaiting-approval` label

**What it does:**
1. Verifies the commenter has permission
2. Checks out the repository
3. Creates a new branch: `gemini/issue-{number}-fix-{run_id}`
4. Uses Gemini to generate a unified diff patch
5. Applies the patch to the codebase
6. Runs tests (best effort)
7. Commits and pushes the changes
8. Creates a Pull Request
9. Updates labels and posts links

**Features:**
- Includes repository context (file list, README)
- Uses 3-way merge for patch application
- Handles whitespace issues automatically
- Provides detailed error diagnostics on failure

**Example PR:**
```
Title: Fix: #123 (Gemini)
Body:
Closes #123

Comment `/approve` on this PR to merge, or `/deny` to cancel.

<!-- gemini-issue:123 -->
```

**Permissions required:** `contents: write`, `pull-requests: write`, `issues: write`

## ✅ Workflow 4: Merge PR (`gemini-merge.yml`)

**Trigger:** When someone comments `/approve` (or `@gemini-cli /approve`) on a PR with `gemini:awaiting-merge` label

**What it does:**
1. Verifies the commenter has permission
2. Checks the PR has `gemini:awaiting-merge` label
3. Squash merges the PR into the default branch
4. Removes the `gemini:awaiting-merge` label
5. Posts success comment on the PR
6. Posts success comment on the original issue (if found)

**Example:**
```
PR #124: 🚀 Merged! Changes have been integrated into the main branch.
Issue #123: ✅ Fixed! PR #124 has been merged and this issue is now resolved.
```

**Permissions required:** `contents: write`, `pull-requests: write`, `issues: write`

## 🛑 Workflow 5: Deny (`gemini-deny.yml`)

**Trigger:** When someone comments `/deny` (or `@gemini-cli /deny`) on an issue or PR

**What it does:**
1. Verifies the commenter has permission
2. Removes Gemini-specific labels (`gemini:awaiting-approval`, `gemini:awaiting-merge`)
3. Keeps other labels intact
4. Closes the issue/PR as "not planned"
5. Posts a cancellation comment

**Example:**
```
Issue: 🛑 Denied. Issue closed as not planned. Gemini labels removed.
PR: 🛑 Denied. PR closed without merging. Gemini labels removed.
```

**Permissions required:** `issues: write`, `pull-requests: write`

## 🔐 Required Secrets & Variables

### Secrets (Repository Settings → Secrets)
- `GEMINI_API_KEY` - Your Google Gemini API key ([Get one here](https://aistudio.google.com/app/apikey))
- `GITHUB_TOKEN` - Automatically provided by GitHub

### Variables (Repository Settings → Variables)
- `GEMINI_MODEL` - The Gemini model to use (e.g., `gemini-2.0-flash-exp`, `gemini-pro`)

## 🎯 Usage Examples

### Example 1: Simple Bug Fix
```
1. User opens issue: "Video player crashes on Android 13"
2. ✅ Auto-triaged: type:bug, area:media, priority:high
3. Maintainer comments: /fix
4. ✅ Gemini posts detailed fix plan
5. Maintainer comments: /approve
6. ✅ PR created with fix
7. Maintainer reviews PR, comments: /approve
8. ✅ PR merged, issue closed
```

### Example 2: Feature Request
```
1. User opens issue: "Add picture-in-picture support"
2. ✅ Auto-triaged: type:feature, area:media, priority:medium
3. Maintainer comments: /fix
4. ✅ Gemini posts implementation plan
5. Maintainer decides not to implement, comments: /deny
6. ✅ Issue closed as not planned
```

### Example 3: Invalid Fix
```
1. Issue exists with plan
2. Maintainer comments: /approve
3. ✅ PR created but fix is incorrect
4. Maintainer comments /deny on PR
5. ✅ PR closed, can try /fix again on original issue
```

## ⚙️ Customization

### Adjusting Label Categories
Edit `.github/workflows/gemini-triage.yml` to modify the label families in the prompt.

### Changing Model
Update the `GEMINI_MODEL` variable in repository settings to use a different model:
- `gemini-2.0-flash-exp` - Fastest, good for most tasks
- `gemini-pro` - More capable, slower
- `gemini-1.5-pro-latest` - Most powerful

### Timeout Adjustments
- Triage: 7 minutes (line 18 of `gemini-triage.yml`)
- Apply Fix: 15 minutes (line 22 of `gemini-apply-fix.yml`)

### Permission Requirements
By default, only `OWNER`, `MEMBER`, and `COLLABORATOR` can use `/fix`, `/approve`, and `/deny` commands. Modify the permission gate in each workflow to change this.

## 🔍 Troubleshooting

### Issue: Triage doesn't apply labels
- Check that `GEMINI_API_KEY` is set correctly
- View workflow run logs in Actions tab
- Ensure Gemini returns valid JSON

### Issue: Apply Fix fails
- Check the patch preview in workflow logs
- Ensure the issue description is clear and specific
- Try rephrasing the issue with more context
- Verify the repository structure matches expectations

### Issue: PR merge fails
- Ensure there are no merge conflicts
- Check that the PR has the `gemini:awaiting-merge` label
- Verify branch protection rules don't block merges

### Issue: Commands don't trigger workflows
- Ensure comment is exactly `/fix`, `/approve`, or `/deny`
- Check that commenter has required permissions
- Verify workflows are enabled in repository settings

## 📊 Monitoring

### Workflow Status
- Go to **Actions** tab to see all workflow runs
- Each run shows detailed logs for debugging
- Failed runs include error diagnostics

### Artifacts
- Fix plan and apply fix workflows upload artifacts
- Access artifacts from the workflow run summary page

## 🚨 Security Considerations

1. **API Key Security**: Never commit `GEMINI_API_KEY` to the repository
2. **Permission Gates**: All workflows check commenter permissions
3. **Label Verification**: Apply Fix only works on issues with `gemini:awaiting-approval`
4. **Rate Limiting**: Concurrency groups prevent duplicate runs
5. **Test Execution**: Tests run (best effort) before creating PR

## 🤝 Contributing

To improve these workflows:

1. Test changes in a fork first
2. Use workflow dispatch triggers for testing
3. Monitor API usage and costs
4. Keep prompts clear and specific
5. Document any changes in this file

## 📝 License

These workflows are part of the Cinefin project and follow the same license.

---

**Questions or Issues?** Open an issue with the `area:build` label for workflow-related problems.
