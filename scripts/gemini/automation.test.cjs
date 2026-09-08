const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {prepare, publish, parseReport} = require('./automation.cjs');
const valid = {summary: 'Playback changes', labels: ['area:playback'], findings: [], limitations: []};
test('rejects invented labels and malformed findings', () => {
  assert.throws(() => parseReport(JSON.stringify({...valid, labels: ['approved']}), 'review'));
  assert.throws(() => parseReport(JSON.stringify({...valid, findings: [{path: 'x', line: -1}]}), 'review'));
  assert.deepEqual(parseReport(JSON.stringify(valid), 'review'), valid);
});
test('comment commands require verified write access', async () => {
  const outputs = [];
  await prepare({github: {rest: {repos: {getCollaboratorPermissionLevel: async () => ({data: {permission: 'read'}})}}},
    context: {repo: {}, actor: 'outsider', eventName: 'issue_comment', payload: {
      comment: {body: '@gemini-cli /review'}, issue: {number: 12, pull_request: {}}}},
    core: {setOutput: (...x) => outputs.push(x)}});
  assert.deepEqual(outputs, []);
});
test('draft PRs are skipped before any API calls', async () => {
  await prepare({github: {}, context: {repo: {}, eventName: 'pull_request_target',
    payload: {pull_request: {draft: true}}}, core: {setOutput: () => assert.fail()}});
});
test('stale PR results never label or publish', async () => {
  const previous = process.cwd(), temp = fs.mkdtempSync(path.join(os.tmpdir(), 'gemini-test-'));
  try {
    process.chdir(temp); fs.mkdirSync('gemini-result');
    fs.writeFileSync('gemini-result/report.json', JSON.stringify(valid));
    process.env.TARGET_NUMBER = '12'; process.env.TASK_MODE = 'review'; process.env.TARGET_REVISION = 'old';
    let noticed = false;
    await publish({github: {rest: {pulls: {get: async () => ({data: {state: 'open', head: {sha: 'new'}}})}}},
      context: {repo: {}}, core: {notice: () => {noticed = true;}}});
    assert.equal(noticed, true);
  } finally {process.chdir(previous); fs.rmSync(temp, {recursive: true, force: true});}
});
test('current PR gets allowed labels and a COMMENT review, with rerun deduplication', async () => {
  const previous = process.cwd(), temp = fs.mkdtempSync(path.join(os.tmpdir(), 'gemini-test-'));
  try {
    process.chdir(temp); fs.mkdirSync('gemini-result');
    fs.writeFileSync('gemini-result/report.json', JSON.stringify(valid));
    process.env.TARGET_NUMBER = '12'; process.env.TASK_MODE = 'review'; process.env.TARGET_REVISION = 'abc';
    const reviews = [], labels = [];
    const github = {rest: {
      pulls: {get: async () => ({data: {state: 'open', head: {sha: 'abc'}}}), listFiles: 'files', listReviews: 'reviews',
        createReview: async r => reviews.push({...r, user: {login: 'github-actions[bot]'}})},
      issues: {getLabel: async () => ({}), addLabels: async r => labels.push(...r.labels)},
    }, paginate: async route => route === 'files' ? [] : reviews};
    const args = {github, context: {repo: {owner: 'owner', repo: 'repo'}, serverUrl: 'https://github.com', runId: 1}, core: {}};
    await publish(args); await publish(args);
    assert.equal(reviews.length, 1); assert.equal(reviews[0].event, 'COMMENT');
    assert.equal(reviews[0].commit_id, 'abc'); assert.deepEqual(labels, ['area:playback', 'area:playback']);
  } finally {process.chdir(previous); fs.rmSync(temp, {recursive: true, force: true});}
});
test('issue triage updates its bot comment and preserves existing labels', async () => {
  const previous = process.cwd(), temp = fs.mkdtempSync(path.join(os.tmpdir(), 'gemini-test-'));
  try {
    process.chdir(temp); fs.mkdirSync('gemini-result');
    const issue = {title: 'Empty library', body: 'Steps', state: 'open'};
    fs.writeFileSync('gemini-result/report.json', JSON.stringify({summary: 'Empty library', labels: ['bug'],
      questions: ['Which server version?'], possibleDuplicates: [5]}));
    fs.writeFileSync('gemini-result/input.json', JSON.stringify({recentIssues: [{number: 5}]}));
    process.env.TARGET_NUMBER = '12'; process.env.TASK_MODE = 'triage';
    process.env.TARGET_REVISION = require('node:crypto').createHash('sha256').update('Empty library\nSteps').digest('hex');
    const updated = [], added = [];
    const github = {rest: {issues: {get: async () => ({data: issue}), getLabel: async () => ({}),
      addLabels: async r => added.push(r.labels), listComments: 'comments',
      updateComment: async r => updated.push(r)}}, paginate: async () => [{id: 99,
      user: {login: 'github-actions[bot]'}, body: '<!-- cinefin-gemini-triage -->'}]};
    await publish({github, context: {repo: {owner: 'owner', repo: 'repo'}, serverUrl: 'https://github.com', runId: 1}, core: {}});
    assert.deepEqual(added, [['bug']]); assert.equal(updated[0].comment_id, 99);
    assert.match(updated[0].body, /#5/);
  } finally {process.chdir(previous); fs.rmSync(temp, {recursive: true, force: true});}
});
