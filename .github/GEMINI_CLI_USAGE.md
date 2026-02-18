# Gemini CLI GitHub Actions Usage Guide

This document describes how to use the Gemini CLI automated workflows for code review, issue triaging, and automated fixes.

## Overview

The Gemini CLI system provides AI-powered assistance for:
- **Triage**: Automatically labels new issues
- **Review**: Analyzes issues to create implementation plans, or reviews PR code changes
- **Fix/Implement**: Automatically implements fixes based on issue descriptions or implementation plans
- **Invoke**: General-purpose AI assistance for custom requests

## Available Commands

You can trigger Gemini CLI workflows by commenting on issues or pull requests with these commands:

### `/triage`
Analyzes an issue and automatically applies appropriate labels from the repository's label list.

**Usage:**
```
/triage
```

**When to use:**
- Manually trigger triage on an existing issue (triage runs automatically on new issues)
- Re-triage an issue after significant updates

---

### `/review`
Reviews code or analyzes an issue to create an implementation plan.

**Behavior depends on context:**
- **On a Pull Request**: Reviews the code changes and provides feedback
- **On an Issue**: Analyzes the issue and posts a detailed implementation plan

**Usage:**
```
/review
```

Or with additional context:
```
/review Focus on security implications
```

**What you get:**
- For PRs: Code review with feedback on correctness, architecture, security, testing, and performance
- For Issues: A detailed implementation plan including affected files, steps, and testing instructions

---

### `/fix` or `/implement`
Implements fixes or features based on an issue's implementation plan or description.

**Usage:**
```
/fix
```

Or:
```
/implement
```

With additional context:
```
/fix Please prioritize performance optimization
```

**What happens:**
1. Gemini reads the issue and looks for an implementation plan (from a previous `/review`)
2. Creates a new branch for the changes
3. Implements the changes following Cinefin coding conventions
4. Creates a Pull Request with the changes
5. Posts a completion comment with a link to the PR

**Requirements:**
- Must be run by a repository OWNER, MEMBER, or COLLABORATOR
- Works best when there's an existing implementation plan from `/review`

---

### `@gemini-cli <your request>`
General-purpose AI assistant for custom requests.

**Usage:**
```
@gemini-cli What changes are needed to add dark mode support?
```

**When to use:**
- Ask questions about the codebase
- Request analysis or suggestions
- Any task that doesn't fit the other commands

---

## Command Formats

All commands support two formats:

1. **Direct slash commands** (recommended):
   ```
   /review
   /fix
   /triage
   ```

2. **With @gemini-cli prefix** (legacy):
   ```
   @gemini-cli /review
   @gemini-cli /fix
   @gemini-cli /triage
   ```

Both formats work identically.

---

## Typical Workflows

### Workflow 1: Automated Issue Fix

1. **Open an issue** describing the problem or feature request
2. **Triage runs automatically**, adding labels
3. **Comment `/review`** to get an implementation plan
4. **Review the plan** and verify it matches your expectations
5. **Comment `/fix`** to have Gemini implement the changes
6. **Review the PR** and merge when ready

### Workflow 2: PR Code Review

1. **Open a Pull Request** with your changes
2. **Review runs automatically** (or comment `/review` manually)
3. **Get AI feedback** on code quality, architecture, and best practices
4. **Address feedback** and update the PR

### Workflow 3: Manual Triage

1. **Open or update an issue**
2. **Comment `/triage`** to apply labels
3. **Labels are applied** based on issue content

---

## Permissions

Commands can only be triggered by users with the following GitHub roles:
- **OWNER**: Repository owner
- **MEMBER**: Organization member (for org repos)
- **COLLABORATOR**: Repository collaborator

This prevents unauthorized users from triggering expensive AI operations.

---

## Automatic Triggers

Some workflows run automatically:
- **Triage**: Runs automatically when an issue is opened or reopened
- **Review**: Runs automatically when a PR is opened (if not from a fork)

---

## Environment Variables & Configuration

The workflows are configured via repository variables and secrets:

### Required Secrets
- `GEMINI_API_KEY` or `GOOGLE_API_KEY`: API key for Gemini AI
- `APP_PRIVATE_KEY`: GitHub App private key (if using GitHub App auth)

### Required Variables
- `GEMINI_CLI_VERSION`: Version of Gemini CLI to use (e.g., `latest`)
- `GEMINI_MODEL`: AI model to use (e.g., `gemini-2.0-flash-exp`)

### Optional Variables
- `GOOGLE_CLOUD_PROJECT`: GCP project ID (for Vertex AI)
- `GOOGLE_CLOUD_LOCATION`: GCP region (for Vertex AI)
- `GCP_WIF_PROVIDER`: Workload Identity Federation provider
- `SERVICE_ACCOUNT_EMAIL`: GCP service account
- `GEMINI_DEBUG`: Enable debug logging (true/false)
- `UPLOAD_ARTIFACTS`: Upload workflow artifacts (true/false)
- `APP_ID`: GitHub App ID (if using GitHub App auth)

---

## Architecture

### Workflows
- `.github/workflows/gemini-dispatch.yml`: Main dispatcher, routes commands to appropriate workflows
- `.github/workflows/gemini-triage.yml`: Issue labeling
- `.github/workflows/gemini-review.yml`: Code review and implementation planning
- `.github/workflows/gemini-fix.yml`: Automated fix implementation
- `.github/workflows/gemini-invoke.yml`: General-purpose AI assistant

### Command Files (Prompts)
- `.github/commands/gemini-triage.toml`: Instructions for triage
- `.github/commands/gemini-review.toml`: Instructions for reviews and planning
- `.github/commands/gemini-fix.toml`: Instructions for implementing fixes
- `.github/commands/gemini-invoke.toml`: Instructions for general assistance

---

## Cinefin-Specific Conventions

When implementing fixes, Gemini follows these Cinefin project conventions:

| Aspect | Convention |
|--------|-----------|
| Language | Kotlin 2.3.10, JDK 21 |
| UI Framework | Jetpack Compose BOM 2026.01.01, Material 3 Expressive |
| Architecture | MVVM + Repository Pattern |
| Dependency Injection | Hilt 2.59.1 (constructor injection) |
| Async | Kotlin Coroutines 1.10.2 + StateFlow |
| Data Access | All SDK calls through Repositories, returns `ApiResult<T>` |
| Networking | Retrofit 3.0.0 + OkHttp 5.3.2 + Jellyfin SDK 1.8.6 |
| Media | ExoPlayer (Media3 1.9.1) |
| Images | Coil 3.3.0 |
| Logging | `SecureLogger` only (strips PII) |
| Security | Auth via headers, NOT URL parameters |
| Testing | JUnit4, MockK, Turbine for Flow testing |
| Commits | Conventional Commits format |

---

## Troubleshooting

### "I'm sorry, but I was unable to process your request"
- Check that you have the correct permissions (OWNER/MEMBER/COLLABORATOR)
- Verify the command syntax is correct
- Check workflow logs for detailed error messages

### Workflow doesn't trigger
- Ensure you're using the correct command format (`/review`, not `review`)
- Check if your account has the required permissions
- Verify the command is in a comment, not in the issue/PR body

### Implementation is incorrect
- Review the implementation plan first with `/review` before running `/fix`
- Provide additional context: `/fix Focus on X and Y`
- The AI may not have enough information; consider providing more details in the issue

### TOML syntax errors in command files
- Command files must have `description` and `prompt` fields
- The `prompt` field must use triple-quoted strings: `"""`
- Validate with: `python3 -c "import tomli; tomli.loads(open('file.toml').read())"`

---

## Examples

### Example 1: Bug Fix Workflow
```
Issue #123: "Video playback crashes on Samsung Galaxy S21"

Comment: /review

Gemini responds with implementation plan...

Comment: /fix

Gemini creates PR #124 with the fix
```

### Example 2: Feature Request
```
Issue #125: "Add support for syncing watched status with server"

Comment: /review Focus on offline capabilities

Gemini analyzes and posts detailed implementation plan...

Comment: /implement

Gemini creates PR #126 with the feature implementation
```

### Example 3: PR Review
```
PR #127: "Implement offline downloads"

Gemini automatically reviews the changes...

Or manually trigger: /review Check for memory leaks
```

---

## Best Practices

1. **Use `/review` first**: Always get an implementation plan before using `/fix`
2. **Provide context**: Add details like "Focus on security" or "Prioritize performance"
3. **Review AI changes**: Always review PRs created by Gemini before merging
4. **Clear issue descriptions**: The better your issue description, the better the AI's output
5. **Incremental fixes**: For complex issues, break them into smaller issues
6. **Test AI changes**: Run tests and verify functionality before merging

---

## Limitations

- AI-generated code should always be reviewed by a human
- Complex architectural changes may require human oversight
- Gemini works best with well-defined, focused issues
- May not catch all edge cases or security issues
- Limited by the context window size (can't analyze entire large codebases at once)

---

## Contributing

To improve the Gemini CLI system:
1. Edit the workflow files in `.github/workflows/gemini-*.yml`
2. Update command prompts in `.github/commands/*.toml`
3. Test changes in a development branch first
4. Document any new features or changes

---

## Support

For issues with the Gemini CLI system:
1. Check the [Actions logs](../../actions) for detailed error messages
2. Review this documentation for correct usage
3. Open an issue with the `gemini-cli` label
4. Tag @rpeters1430 for urgent issues

---

**Last Updated**: February 2026
