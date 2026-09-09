const fs = require('node:fs');

const LABELS = {
  bug: 'd73a4a', enhancement: 'a2eeef', documentation: '0075ca', question: 'd876e3',
  'area:playback': '1d76db', 'area:libraries': '1d76db', 'area:authentication': '1d76db',
  'area:ui': '1d76db', 'area:android-tv': '1d76db', 'area:casting': '1d76db',
  'area:downloads': '1d76db', 'area:firebase': '1d76db', 'area:ci': '1d76db',
  'area:dependencies': '1d76db', 'area:security': 'b60205',
  'area:ai': '1d76db', 'area:performance': '1d76db',
};

function extractJson(raw) {
  if (typeof raw !== 'string') throw Error('Model report must be a string');
  const trimmed = raw.trim();
  if (!trimmed) throw Error('Empty model report');

  // 1. Direct parse attempt
  try {
    return JSON.parse(trimmed);
  } catch (_) {}

  // 2. Extract from markdown code fence ```json ... ``` or ``` ... ```
  const codeBlockMatch = trimmed.match(/```(?:json)?\s*\n?([\s\S]*?)\n?\s*```/);
  if (codeBlockMatch) {
    try {
      return JSON.parse(codeBlockMatch[1].trim());
    } catch (_) {}
  }

  // 3. Find outermost JSON object { ... }
  const firstBrace = trimmed.indexOf('{');
  const lastBrace = trimmed.lastIndexOf('}');
  if (firstBrace !== -1 && lastBrace > firstBrace) {
    try {
      return JSON.parse(trimmed.slice(firstBrace, lastBrace + 1));
    } catch (_) {}
  }

  throw Error('Failed to parse JSON from model output');
}

function parseReport(raw, mode) {
  const report = extractJson(raw);
  const string = (value, max) => typeof value === 'string' && value.length > 0 && value.length <= max;
  if (!report || !string(report.summary, 2000)) throw Error('Missing or oversized summary');
  if (!Array.isArray(report.labels) || report.labels.length > 4 ||
      report.labels.some(label => !Object.hasOwn(LABELS, label))) throw Error('Invalid labels');

  if (mode === 'review') {
    if (!Array.isArray(report.findings) || report.findings.length > 10 ||
        !Array.isArray(report.limitations) || report.limitations.length > 10 ||
        report.limitations.some(x => !string(x, 2000))) throw Error('Invalid review');
    for (const f of report.findings) {
      if (typeof f.severity === 'string') f.severity = f.severity.toLowerCase();
      if (!string(f.path, 500) || !Number.isSafeInteger(f.line) || f.line < 1 ||
          !['low', 'medium', 'high', 'critical'].includes(f.severity) ||
          !string(f.title, 300) || !string(f.body, 3000)) throw Error('Invalid finding');
    }
  } else {
    report.questions = report.questions || [];
    report.possibleDuplicates = report.possibleDuplicates || [];
    report.potentialCauses = report.potentialCauses || [];
    report.details = report.details || {};
    if (!Array.isArray(report.questions) || report.questions.length > 3 ||
        report.questions.some(x => !string(x, 1000)) ||
        !Array.isArray(report.possibleDuplicates) || report.possibleDuplicates.length > 3 ||
        report.possibleDuplicates.some(x => !Number.isSafeInteger(x) || x < 1)) throw Error('Invalid triage');
    if (!Array.isArray(report.potentialCauses) || report.potentialCauses.length > 3 ||
        report.potentialCauses.some(x => !string(x, 1000))) throw Error('Invalid potentialCauses');
  }
  return report;
}

async function prepare({github, context, core}) {
  const repo = context.repo;
  const event = context.payload;
  let mode, number;
  if (context.eventName === 'issues') {
    if (event.issue.user.type === 'Bot') return;
    mode = 'triage'; number = event.issue.number;
  } else if (context.eventName === 'pull_request_target') {
    if (event.pull_request.draft) return;
    mode = 'review'; number = event.pull_request.number;
  } else if (context.eventName === 'issue_comment' || context.eventName === 'workflow_dispatch') {
    if (context.eventName === 'workflow_dispatch') {
      const {data: permission} = await github.rest.repos.getCollaboratorPermissionLevel({
        ...repo, username: context.actor,
      });
      if (!['admin', 'maintain', 'write'].includes(permission.permission)) return;
      if (context.ref !== `refs/heads/${event.repository.default_branch}`) return;
      mode = event.inputs.task; number = Number(event.inputs.number);
    } else {
      const command = (event.comment.body || '').trim();
      const isPr = Boolean(event.issue.pull_request);
      if (isPr) {
        if (!command.includes('@gemini-cli /review') && !command.startsWith('@gemini-cli /review')) return;
        const {data: permission} = await github.rest.repos.getCollaboratorPermissionLevel({
          ...repo, username: context.actor,
        });
        if (!['admin', 'maintain', 'write'].includes(permission.permission)) return;
        mode = 'review';
      } else {
        if (!command.includes('@gemini-cli')) return;
        // Issue author or maintainers with write access can trigger triage / replies
        const isAuthor = event.issue.user?.login === context.actor;
        let hasAccess = isAuthor;
        if (!hasAccess) {
          const {data: permission} = await github.rest.repos.getCollaboratorPermissionLevel({
            ...repo, username: context.actor,
          });
          hasAccess = ['admin', 'maintain', 'write'].includes(permission.permission);
        }
        if (!hasAccess) return;
        mode = 'triage';
      }
      number = event.issue.number;
    }
  }
  if (!['review', 'triage'].includes(mode) || !Number.isSafeInteger(number) || number < 1) return;
  const input = {mode, number, allowedLabels: Object.keys(LABELS)};
  let revision;
  if (mode === 'review') {
    const {data: pr} = await github.rest.pulls.get({...repo, pull_number: number});
    if (pr.state !== 'open' || pr.draft) return;
    revision = pr.head.sha;
    const files = await github.paginate(github.rest.pulls.listFiles, {...repo, pull_number: number, per_page: 100});
    let budget = 350000;
    input.title = pr.title; input.body = (pr.body || '').slice(0, 20000);
    input.baseSha = pr.base.sha; input.headSha = revision;

    // Prioritize high-signal code files over lockfiles or binary assets
    const isPriority = file => /\.(kt|kts|java|xml|gradle|toml)$/i.test(file.filename);
    const isLockfile = file => /(lock|lockfile|\.lock)$/i.test(file.filename) || file.filename.endsWith('package-lock.json');
    const isBinary = file => /\.(png|webp|jpg|jpeg|gif|ico|jar|aar|so|dylib|bin|keystore|jks)$/i.test(file.filename);

    const sortedFiles = [...files].sort((a, b) => {
      const aScore = isPriority(a) ? 0 : isLockfile(a) ? 2 : 1;
      const bScore = isPriority(b) ? 0 : isLockfile(b) ? 2 : 1;
      return aScore - bScore;
    });

    input.files = sortedFiles.slice(0, 300).map(f => {
      const isBin = isBinary(f);
      const patch = isBin ? '' : (f.patch || '').slice(0, Math.max(0, budget));
      if (!isBin) budget -= patch.length;
      return {
        path: f.filename,
        status: f.status,
        patch: isBin ? '[Binary file omitted]' : patch,
        incomplete: !isBin && (!f.patch || patch.length !== f.patch.length)
      };
    });
    input.incomplete = files.length > 300 || input.files.some(f => f.incomplete);
  } else {
    const {data: issue} = await github.rest.issues.get({...repo, issue_number: number});
    if (issue.pull_request || issue.state !== 'open' || issue.user.type === 'Bot') return;
    input.title = issue.title; input.body = (issue.body || '').slice(0, 20000);
    input.incomplete = (issue.body || '').length > 20000;

    // Include recent conversation comments for context awareness
    const comments = github.paginate
      ? await github.paginate(github.rest.issues.listComments, {...repo, issue_number: number, per_page: 100})
      : [];
    const userComments = comments.filter(c => !c.body?.includes('<!-- cinefin-gemini-triage -->'));
    input.comments = userComments.slice(-10).map(c => ({
      author: c.user?.login,
      body: (c.body || '').slice(0, 3000)
    }));

    const lastComment = userComments.slice(-1)[0];
    const commentHash = lastComment ? `\ncomment:${lastComment.id}:${lastComment.body}` : '';
    revision = require('node:crypto').createHash('sha256').update(issue.title + '\n' + (issue.body || '') + commentHash).digest('hex');
    const {data: recent} = await github.rest.issues.listForRepo({...repo, state: 'all', per_page: 50});
    input.recentIssues = recent.filter(i => !i.pull_request && i.number !== number)
      .map(i => ({number: i.number, title: i.title, body: (i.body || '').slice(0, 1000)}));
  }
  fs.mkdirSync('.gemini', {recursive: true});
  fs.writeFileSync('.gemini/input.json', JSON.stringify(input));
  core.setOutput('mode', mode); core.setOutput('number', number); core.setOutput('revision', revision);
}

// Neutralize mentions/HTML before publishing model-controlled text.
const safe = value => String(value).replace(/@/g, '@\u200b').replace(/</g, '&lt;').replace(/>/g, '&gt;');

async function publish({github, context, core}) {
  const repo = context.repo, number = Number(process.env.TARGET_NUMBER);
  const mode = process.env.TASK_MODE, revision = process.env.TARGET_REVISION;
  if (!Number.isSafeInteger(number) || number < 1 || !['review', 'triage'].includes(mode)) throw Error('Invalid target');
  const report = parseReport(fs.readFileSync('gemini-result/report.json', 'utf8'), mode);
  let current;
  if (mode === 'review') {
    ({data: current} = await github.rest.pulls.get({...repo, pull_number: number}));
    if (current.state !== 'open' || current.draft || current.head.sha !== revision) {
      core.notice('PR changed or closed; stale review discarded.'); return;
    }
    const files = await github.paginate(github.rest.pulls.listFiles, {...repo, pull_number: number, per_page: 100});
    const paths = new Set(files.map(f => f.filename));
    if (report.findings.some(f => !paths.has(f.path))) throw Error('Finding is outside the PR diff');
  } else {
    ({data: current} = await github.rest.issues.get({...repo, issue_number: number}));
    const comments = github.paginate
      ? await github.paginate(github.rest.issues.listComments, {...repo, issue_number: number, per_page: 100})
      : [];
    const userComments = comments.filter(c => !c.body?.includes('<!-- cinefin-gemini-triage -->'));
    const lastComment = userComments.slice(-1)[0];
    const commentHash = lastComment ? `\ncomment:${lastComment.id}:${lastComment.body}` : '';
    const hash = require('node:crypto').createHash('sha256').update(current.title + '\n' + (current.body || '') + commentHash).digest('hex');
    if (current.state !== 'open' || hash !== revision) {core.notice('Issue changed; stale triage discarded.'); return;}
    const input = JSON.parse(fs.readFileSync('gemini-result/input.json', 'utf8'));
    const candidates = new Set(input.recentIssues.map(i => i.number));
    if (report.possibleDuplicates.some(n => !candidates.has(n))) throw Error('Unverified duplicate reference');
  }
  for (const label of new Set(report.labels)) {
    try {await github.rest.issues.getLabel({...repo, name: label});}
    catch (error) {
      if (error.status !== 404) throw error;
      try {await github.rest.issues.createLabel({...repo, name: label, color: LABELS[label]});}
      catch (createError) {if (createError.status !== 422) throw createError;}
    }
  }
  if (report.labels.length) await github.rest.issues.addLabels({...repo, issue_number: number, labels: report.labels});
  const run = `${context.serverUrl}/${repo.owner}/${repo.repo}/actions/runs/${context.runId}`;
  if (mode === 'review') {
    const marker = `<!-- cinefin-gemini-review:${revision} -->`;
    const reviews = await github.paginate(github.rest.pulls.listReviews, {...repo, pull_number: number, per_page: 100});
    if (reviews.some(r => r.user?.login === 'github-actions[bot]' && r.body?.includes(marker))) return;

    const severityBadges = {
      critical: '🚨 **CRITICAL**',
      high: '⚠️ **HIGH**',
      medium: '🟡 **MEDIUM**',
      low: 'ℹ️ **LOW**'
    };

    const findings = report.findings.map(f => {
      const badge = severityBadges[f.severity] || `**${f.severity.toUpperCase()}**`;
      const fileLink = `[${safe(f.path)}#L${f.line}](https://github.com/${repo.owner}/${repo.repo}/blob/${revision}/${encodeURI(f.path)}#L${f.line})`;
      return `### ${badge}: ${safe(f.title)}\n📍 **Location:** ${fileLink}\n\n${safe(f.body)}`;
    }).join('\n\n---\n\n');

    const statusHeader = report.findings.length > 0
      ? `## 🤖 Gemini PR Review: Feedback Identified (${report.findings.length})`
      : `## 🤖 Gemini PR Review: No Blocking Issues Found`;

    const limitationsSection = report.limitations.length > 0
      ? `<details>\n<summary><b>Review Scope & Limitations</b></summary>\n\n${report.limitations.map(x => '- ' + safe(x)).join('\n')}\n</details>\n\n`
      : '';

    const body = `${marker}\n${statusHeader}\n\n> ${safe(report.summary)}\n\n${findings || '✅ No actionable code quality or security defects identified in the reviewed changes.'}\n\n${limitationsSection}*Static AI review only; builds, tests, and lint are verified by Android CI.* • [Workflow run](${run})`;
    await github.rest.pulls.createReview({...repo, pull_number: number, commit_id: revision, event: 'COMMENT', body});
  } else {
    const marker = '<!-- cinefin-gemini-triage -->';

    let detailsSection = '';
    if (report.details && (report.details.versions || report.details.environment || (report.details.keyFacts && report.details.keyFacts.length))) {
      const facts = (report.details.keyFacts || []).map(f => `- ${safe(f)}`).join('\n');
      detailsSection = `### 📋 Extracted Context & Details\n` +
        (report.details.versions ? `- **Versions:** ${safe(report.details.versions)}\n` : '') +
        (report.details.environment ? `- **Environment:** ${safe(report.details.environment)}\n` : '') +
        (facts ? `${facts}\n` : '') +
        `\n`;
    }

    let causesSection = '';
    if (report.potentialCauses && report.potentialCauses.length > 0) {
      causesSection = `### 💡 Potential Causes & Hypotheses\n\n` +
        report.potentialCauses.map(c => `- ${safe(c)}`).join('\n') +
        `\n\n`;
    }

    const questionsSection = (report.questions && report.questions.length > 0)
      ? `### ❓ Clarification Questions\n\n${report.questions.map(q => '- ' + safe(q)).join('\n')}\n\n`
      : '✅ *All necessary reproduction details appear to be provided.*\n\n';

    const duplicatesSection = (report.possibleDuplicates && report.possibleDuplicates.length > 0)
      ? `### 🔗 Possible Related Issues\n\n${report.possibleDuplicates.map(n => `- #${n}`).join('\n')}\n\n`
      : '';

    const body = `${marker}\n## 🤖 Gemini Issue Triage\n\n> ${safe(report.summary)}\n\n${detailsSection}${causesSection}${questionsSection}${duplicatesSection}*Automated triage by Gemini CLI. Reply with \`@gemini-cli\` to provide updates or logs.* • [Workflow run](${run})`;
    const comments = await github.paginate(github.rest.issues.listComments, {...repo, issue_number: number, per_page: 100});
    const existing = comments.find(c => c.user?.login === 'github-actions[bot]' && c.body?.includes(marker));
    if (existing) await github.rest.issues.updateComment({...repo, comment_id: existing.id, body});
    else await github.rest.issues.createComment({...repo, issue_number: number, body});
  }

  if (process.env.GITHUB_STEP_SUMMARY) {
    const summaryLines = [
      `### 🤖 Gemini ${mode === 'review' ? 'PR Review' : 'Issue Triage'} Published`,
      `- **Target:** #${number}`,
      `- **Labels Applied:** ${report.labels.length ? report.labels.join(', ') : 'None'}`,
      mode === 'review' ? `- **Findings Count:** ${report.findings.length}` : `- **Questions Count:** ${report.questions.length}`,
      `- **Summary:** ${report.summary}`
    ];
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, summaryLines.join('\n') + '\n');
  }
}

module.exports = {prepare, publish, parseReport, LABELS};
