# 🧪 Gemini Workflow Testing Checklist

Use this checklist to verify that all Gemini workflows are working correctly after setup.

## Prerequisites ✅

- [ ] `GEMINI_API_KEY` secret is set in repository settings
- [ ] `GEMINI_MODEL` variable is set (recommended: `gemini-2.0-flash-exp`)
- [ ] All workflow files are present in `.github/workflows/`
- [ ] You have OWNER, MEMBER, or COLLABORATOR access to the repository

## Test 1: Auto-Triage (gemini-triage.yml)

### Steps
1. Open a new issue with title: "App crashes when loading videos"
2. Add body: "The app crashes immediately when I try to play any video. This happens on Android 13."
3. Wait 1-2 minutes

### Expected Results
- [ ] Workflow runs successfully (check Actions tab)
- [ ] Labels are automatically applied (e.g., `type:bug`, `area:media`, `priority:high`)
- [ ] No errors in workflow logs

### Troubleshooting
- If no labels: Check GEMINI_API_KEY is correct
- If invalid JSON error: Check Gemini model is responding correctly
- View workflow run logs in Actions tab for details

## Test 2: Fix Plan (gemini-fix-plan.yml)

### Steps
1. On the test issue from Test 1, comment: `/fix`
2. Wait 1-2 minutes

### Expected Results
- [ ] Workflow runs successfully
- [ ] Comment posted with fix plan containing:
  - [ ] "📋 Fix Plan" section
  - [ ] "⚙️ Implementation Approach" section
  - [ ] "🚀 Next Steps" section
- [ ] Label `gemini:awaiting-approval` is added
- [ ] No errors in workflow logs

### Troubleshooting
- If permission denied: Ensure you have OWNER/MEMBER/COLLABORATOR role
- If no comment: Check workflow logs for Gemini API errors

## Test 3: Apply Fix (gemini-apply-fix.yml)

### Steps
1. On the test issue with the fix plan, comment: `/approve`
2. Wait 3-5 minutes (this workflow takes longer)

### Expected Results
- [ ] Workflow runs successfully
- [ ] New branch created: `gemini/issue-{number}-fix-{run_id}`
- [ ] PR created with:
  - [ ] Title: "Fix: #{issue_number} (Gemini)"
  - [ ] Body references the issue: "Closes #{issue_number}"
  - [ ] Label: `gemini:awaiting-merge`
- [ ] Comment on issue with PR link
- [ ] Comment on PR with instructions
- [ ] `gemini:awaiting-approval` label removed from issue
- [ ] No errors in workflow logs

### Troubleshooting
- If patch fails: Issue description may need more detail
- If no changes: Gemini might not have generated a valid patch
- Check "Extract diff into patch file" step for patch preview

## Test 4: Merge PR (gemini-merge.yml)

### Steps
1. Review the PR created in Test 3
2. On the PR, comment: `/approve`
3. Wait 30 seconds

### Expected Results
- [ ] Workflow runs successfully
- [ ] PR is merged (squash merge)
- [ ] Comment on PR: "🚀 Merged! Changes have been integrated..."
- [ ] Comment on original issue: "✅ Fixed! PR #{pr_number} has been merged..."
- [ ] Original issue is closed automatically (by "Closes #" in PR body)
- [ ] Label `gemini:awaiting-merge` removed from PR

### Troubleshooting
- If merge fails: Check for merge conflicts or branch protection rules
- If no PR label: Ensure PR has `gemini:awaiting-merge` label

## Test 5: Deny on Issue (gemini-deny.yml)

### Steps
1. Open a new issue: "Test issue for deny"
2. Comment: `/fix`
3. Wait for fix plan
4. Comment: `/deny`
5. Wait 30 seconds

### Expected Results
- [ ] Workflow runs successfully
- [ ] Gemini labels removed (`gemini:awaiting-approval`)
- [ ] Other labels preserved (from auto-triage)
- [ ] Issue closed with state_reason "not_planned"
- [ ] Comment: "🛑 Denied. Issue closed as not planned..."

### Troubleshooting
- If all labels removed: Check workflow preserves non-Gemini labels
- If permission denied: Ensure you have appropriate role

## Test 6: Deny on PR (gemini-deny.yml)

### Steps
1. Create an issue and follow steps to generate a PR (Tests 1-3)
2. On the PR, comment: `/deny`
3. Wait 30 seconds

### Expected Results
- [ ] Workflow runs successfully
- [ ] Gemini labels removed from PR
- [ ] PR closed (not merged)
- [ ] Comment: "🛑 Denied. PR closed without merging..."
- [ ] Original issue remains open (PR was not merged)

## 🎯 Full Workflow Integration Test

Complete this test to verify the entire workflow end-to-end:

1. [ ] Create issue → Auto-triaged with labels
2. [ ] Comment `/fix` → Fix plan posted
3. [ ] Comment `/approve` → PR created
4. [ ] Comment `/approve` on PR → PR merged, issue closed

## 📊 Success Criteria

All tests pass if:
- ✅ All workflows trigger correctly
- ✅ Labels are applied/removed as expected
- ✅ Comments are posted with correct formatting
- ✅ PR creation and merging work properly
- ✅ Original issues are closed after PR merge
- ✅ Error handling works (visible error messages on failure)

## 🐛 Common Issues

| Problem | Solution |
|---------|----------|
| "Not allowed" error | Check user has OWNER/MEMBER/COLLABORATOR role |
| No Gemini response | Verify GEMINI_API_KEY is correct |
| Invalid JSON from triage | Try different GEMINI_MODEL or check API quota |
| Patch fails to apply | Issue description needs more technical detail |
| Tests fail in apply-fix | Expected behavior, workflow continues anyway |
| Workflow doesn't trigger | Ensure comment is exactly `/fix`, `/approve`, or `/deny` |

## 📝 Notes

- All workflows use concurrency groups to prevent duplicate runs
- The apply-fix workflow has a 15-minute timeout
- Tests run automatically but failures don't block PR creation
- View detailed logs in the Actions tab for troubleshooting

## ✅ Sign-off

After completing all tests:

- Tested by: _______________
- Date: _______________
- All tests passed: [ ] Yes [ ] No
- Issues found: _______________
- Notes: _______________

---

📖 **Documentation:** See [GEMINI_WORKFLOWS.md](GEMINI_WORKFLOWS.md) for detailed workflow information.
