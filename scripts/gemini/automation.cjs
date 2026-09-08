const fs = require('node:fs');

const LABELS = {
  bug: 'd73a4a', enhancement: 'a2eeef', documentation: '0075ca', question: 'd876e3',
  'area:playback': '1d76db', 'area:libraries': '1d76db', 'area:authentication': '1d76db',
  'area:ui': '1d76db', 'area:android-tv': '1d76db', 'area:casting': '1d76db',
  'area:downloads': '1d76db', 'area:firebase': '1d76db', 'area:ci': '1d76db',
  'area:dependencies': '1d76db', 'area:security': 'b60205',
};
function parseReport(raw, mode) {
  const report = JSON.parse(raw.trim().replace(/^```(?:json)?\s*\n([\s\S]*?)\n```$/, '$1'));
  const string = (value, max) => typeof value === 'string' && value.length > 0 && value.length <= max;
  if (!report || !string(report.summary, 2000)) throw Error('Missing or oversized summary');
  if (!Array.isArray(report.labels) || report.labels.length > 4 ||
      report.labels.some(label => !Object.hasOwn(LABELS, label))) throw Error('Invalid labels');
  if (mode === 'review') {
    if (!Array.isArray(report.findings) || report.findings.length > 10 ||
        !Array.isArray(report.limitations) || report.limitations.length > 10 ||
        report.limitations.some(x => !string(x, 2000))) throw Error('Invalid review');
    for (const f of report.findings) {
      if (!string(f.path, 500) || !Number.isSafeInteger(f.line) || f.line < 1 ||
          !['low', 'medium', 'high', 'critical'].includes(f.severity) ||
          !string(f.title, 300) || !string(f.body, 3000)) throw Error('Invalid finding');
    }
  } else {
    if (!Array.isArray(report.questions) || report.questions.length > 3 ||
        report.questions.some(x => !string(x, 1000)) ||
        !Array.isArray(report.possibleDuplicates) || report.possibleDuplicates.length > 3 ||
        report.possibleDuplicates.some(x => !Number.isSafeInteger(x) || x < 1)) throw Error('Invalid triage');
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
    // Verify actual write permission, not just a claimed author association.
    const {data: permission} = await github.rest.repos.getCollaboratorPermissionLevel({
      ...repo, username: context.actor,
    });
    if (!['admin', 'maintain', 'write'].includes(permission.permission)) return;
    if (context.eventName === 'workflow_dispatch') {
      if (context.ref !== `refs/heads/${event.repository.default_branch}`) return;
      mode = event.inputs.task; number = Number(event.inputs.number);
    } else {
      const command = event.comment.body.trim();
      if (command === '@gemini-cli /review' && event.issue.pull_request) mode = 'review';
      if (command === '@gemini-cli /triage' && !event.issue.pull_request) mode = 'triage';
      if (!mode) return;
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
    input.files = files.slice(0, 300).map(f => {
      const patch = (f.patch || '').slice(0, Math.max(0, budget));
      budget -= patch.length;
      return {path: f.filename, status: f.status, patch,
        incomplete: !f.patch || patch.length !== f.patch.length};
    });
    input.incomplete = files.length > 300 || input.files.some(f => f.incomplete);
  } else {
    const {data: issue} = await github.rest.issues.get({...repo, issue_number: number});
    if (issue.pull_request || issue.state !== 'open' || issue.user.type === 'Bot') return;
    input.title = issue.title; input.body = (issue.body || '').slice(0, 20000);
    input.incomplete = (issue.body || '').length > 20000;
    // Track content only: our own comments/labels also change updated_at.
    revision = require('node:crypto').createHash('sha256').update(issue.title + '\n' + (issue.body || '')).digest('hex');
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
    const hash = require('node:crypto').createHash('sha256').update(current.title + '\n' + (current.body || '')).digest('hex');
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
    const findings = report.findings.map(f => `- **${f.severity.toUpperCase()}: ${safe(f.title)}** — ${safe(f.path)}:${f.line}\n\n  ${safe(f.body)}`).join('\n\n');
    const body = `${marker}\n## Gemini review\n\n${safe(report.summary)}\n\n${findings || 'No actionable findings in the reviewed changes.'}\n\n### Coverage\n\n${report.limitations.map(x => '- ' + safe(x)).join('\n')}\n- Static AI review only; builds, tests and lint are handled by Android CI.\n\n[Workflow run](${run})`;
    await github.rest.pulls.createReview({...repo, pull_number: number, commit_id: revision, event: 'COMMENT', body});
  } else {
    const marker = '<!-- cinefin-gemini-triage -->';
    const body = `${marker}\n## Gemini triage\n\n${safe(report.summary)}\n\n${report.questions.map(q => '- ' + safe(q)).join('\n')}${report.possibleDuplicates.length ? '\n\nPossible related issues: ' + report.possibleDuplicates.map(n => '#' + n).join(', ') : ''}\n\n[Workflow run](${run})`;
    const comments = await github.paginate(github.rest.issues.listComments, {...repo, issue_number: number, per_page: 100});
    const existing = comments.find(c => c.user?.login === 'github-actions[bot]' && c.body?.includes(marker));
    if (existing) await github.rest.issues.updateComment({...repo, comment_id: existing.id, body});
    else await github.rest.issues.createComment({...repo, issue_number: number, body});
  }
}
module.exports = {prepare, publish, parseReport, LABELS};
