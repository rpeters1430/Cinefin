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
test('review publish normalizes simple finding paths and discards out-of-diff findings', async () => {
  const previous = process.cwd(), temp = fs.mkdtempSync(path.join(os.tmpdir(), 'gemini-test-'));
  try {
    process.chdir(temp); fs.mkdirSync('gemini-result');
    fs.writeFileSync('gemini-result/report.json', JSON.stringify({
      summary: 'Review feedback',
      labels: ['area:ci'],
      findings: [
        {path: './README.md', line: 12, severity: 'medium', title: 'Doc fix', body: 'Update the docs.'},
        {path: 'docs/other.md', line: 3, severity: 'low', title: 'Ignore me', body: 'Outside the diff.'}
      ],
      limitations: []
    }));
    process.env.TARGET_NUMBER = '12'; process.env.TASK_MODE = 'review'; process.env.TARGET_REVISION = 'abc';
    const notices = [], reviews = [];
    const github = {rest: {
      pulls: {
        get: async () => ({data: {state: 'open', head: {sha: 'abc'}}}),
        listFiles: 'files',
        listReviews: 'reviews',
        createReview: async r => reviews.push(r)
      },
      issues: {getLabel: async () => ({}), addLabels: async () => {}},
    }, paginate: async route => route === 'files' ? [{filename: 'README.md'}] : []};
    await publish({
      github,
      context: {repo: {owner: 'owner', repo: 'repo'}, serverUrl: 'https://github.com', runId: 1},
      core: {notice: message => notices.push(message)}
    });
    assert.equal(reviews.length, 1);
    assert.match(reviews[0].body, /Feedback Identified \(1\)/);
    assert.match(reviews[0].body, /\[README\.md#L12\]/);
    assert.doesNotMatch(reviews[0].body, /docs\/other\.md/);
    assert.match(reviews[0].body, /discarded because (it|they) referenced file(s)? outside the PR diff/);
    assert.deepEqual(notices, ['Discarded Gemini findings that referenced files outside the PR diff.']);
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
test('privileged workflow checkouts use the event SHA, never model/job outputs or PR refs', () => {
  const workflow = fs.readFileSync(path.join(__dirname, '../../.github/workflows/gemini.yml'), 'utf8');
  const refs = [...workflow.matchAll(/^\s+ref: (.+)$/gm)].map(match => match[1]);
  assert.deepEqual(refs, ['${{ github.sha }}', '${{ github.sha }}']);
  assert.doesNotMatch(workflow, /trusted_sha|pull_request\.head|refs\/pull\//);
  assert.match(workflow, /github\.event_name != 'workflow_dispatch' \|\|\s+github\.ref == format/);
});
test('extracts JSON surrounded by markdown code blocks or text and normalizes severity', () => {
  const rawWithFences = 'Here is the analysis:\n```json\n{"summary":"Fine","labels":["area:ai"],"findings":[{"path":"app/A.kt","line":10,"severity":"HIGH","title":"Leak","body":"Fix it"}],"limitations":[]}\n```\nThanks!';
  const parsed = parseReport(rawWithFences, 'review');
  assert.equal(parsed.summary, 'Fine');
  assert.deepEqual(parsed.labels, ['area:ai']);
  assert.equal(parsed.findings[0].severity, 'high');
});
test('handles comment commands with trailing whitespace or newlines', async () => {
  const outputs = [];
  await prepare({
    github: {
      rest: {
        repos: {getCollaboratorPermissionLevel: async () => ({data: {permission: 'write'}})},
        issues: {get: async () => ({data: {state: 'open', title: 'Title', body: 'Body', user: {type: 'User'}}}), listForRepo: async () => ({data: []})}
      }
    },
    context: {repo: {}, actor: 'maintainer', eventName: 'issue_comment', payload: {
      comment: {body: '@gemini-cli /triage \r\n'}, issue: {number: 14, user: {type: 'User'}}}},
    core: {setOutput: (...x) => outputs.push(x)}
  });
  assert.deepEqual(outputs, [['mode', 'triage'], ['number', 14], ['revision', require('node:crypto').createHash('sha256').update('Title\nBody').digest('hex')]]);
});
test('allows issue author to reply to @gemini-cli and extracts context & potential causes', async () => {
  const outputs = [];
  const previous = process.cwd(), temp = fs.mkdtempSync(path.join(os.tmpdir(), 'gemini-test-'));
  try {
    process.chdir(temp);
    await prepare({
      github: {
        rest: {
          issues: {
            get: async () => ({data: {state: 'open', title: 'Playback failure', body: 'Fails to play', user: {login: 'issueAuthor'}}}),
            listForRepo: async () => ({data: []})
          }
        },
        paginate: async () => [
          {id: 1, user: {login: 'github-actions[bot]'}, body: '<!-- cinefin-gemini-triage --> Please provide logs'},
          {id: 2, user: {login: 'issueAuthor'}, body: '@gemini-cli Here are the logs: ExoPlayer codec error with AC3 audio on Android TV'}
        ]
      },
      context: {repo: {}, actor: 'issueAuthor', eventName: 'issue_comment', payload: {
        comment: {body: '@gemini-cli Here are the logs: ExoPlayer codec error with AC3 audio on Android TV'},
        issue: {number: 15, user: {login: 'issueAuthor'}}
      }},
      core: {setOutput: (...x) => outputs.push(x)}
    });
    assert.equal(outputs[0][0], 'mode'); assert.equal(outputs[0][1], 'triage');
    const input = JSON.parse(fs.readFileSync('.gemini/input.json', 'utf8'));
    assert.equal(input.comments.length, 1);
    assert.match(input.comments[0].body, /ExoPlayer codec error/);

    // Test publishing with details and potential causes
    fs.mkdirSync('gemini-result');
    fs.writeFileSync('gemini-result/report.json', JSON.stringify({
      summary: 'AC3 codec failure on Android TV',
      labels: ['bug', 'area:playback', 'area:android-tv'],
      details: {versions: 'Android TV 12', environment: 'FireStick 4K', keyFacts: ['AC3 passthrough failure']},
      potentialCauses: ['ExoPlayer AC3 passthrough unsupported on device hardware', 'Transcoding fallback disabled'],
      questions: [],
      possibleDuplicates: []
    }));
    fs.writeFileSync('gemini-result/input.json', JSON.stringify({recentIssues: []}));

    process.env.TARGET_NUMBER = '15'; process.env.TASK_MODE = 'triage';
    process.env.TARGET_REVISION = outputs[2][1];

    const updated = [];
    await publish({
      github: {
        rest: {
          issues: {
            get: async () => ({data: {state: 'open', title: 'Playback failure', body: 'Fails to play'}}),
            getLabel: async () => ({}),
            addLabels: async () => {},
            updateComment: async r => updated.push(r)
          }
        },
        paginate: async () => [
          {id: 10, user: {login: 'github-actions[bot]'}, body: '<!-- cinefin-gemini-triage -->'},
          {id: 2, user: {login: 'issueAuthor'}, body: '@gemini-cli Here are the logs: ExoPlayer codec error with AC3 audio on Android TV'}
        ]
      },
      context: {repo: {owner: 'owner', repo: 'repo'}, serverUrl: 'https://github.com', runId: 1}, core: {}
    });

    assert.equal(updated.length, 1);
    assert.match(updated[0].body, /Extracted Context & Details/);
    assert.match(updated[0].body, /Potential Causes & Hypotheses/);
    assert.match(updated[0].body, /FireStick 4K/);
    assert.match(updated[0].body, /All necessary reproduction details appear to be provided/);
  } finally {
    process.chdir(previous); fs.rmSync(temp, {recursive: true, force: true});
  }
});
