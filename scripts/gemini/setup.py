"""Install pinned review guidance. Never execute extension MCP servers or hooks in CI."""
import json
import os
import pathlib
import shutil
import subprocess

root = pathlib.Path.cwd()
sources = json.loads((root / '.github/gemini/sources.json').read_text())
skills = root / '.gemini/skills'
skills.mkdir(parents=True, exist_ok=True)
selected_android = {
    'android-cli', 'android-intent-security', 'adaptive', 'edge-to-edge',
    'r8-analyzer', 'testing-setup', 'media3-cast-integration', 'navigation-3',
}
selected_firebase = {'firebase-crashlytics', 'firebase-remote-config-basics', 'firebase-basics'}
# Check if already installed from actions/cache or previous run
cached = True
for name in sources:
    vendor_target = root / '.gemini/vendor' / name
    if not vendor_target.exists():
        cached = False
        break

if cached:
    for skill in (selected_android | selected_firebase):
        if not (skills / skill).exists():
            cached = False
            break

if cached:
    print('Pinned code-review/security extensions and Android/Firebase skills already cached.')
else:
    for name, source in sources.items():
        target = root / '.gemini/vendor' / name
        target.mkdir(parents=True, exist_ok=True)
        subprocess.run(['git', 'init', '-q', str(target)], check=True)
        subprocess.run(['git', '-C', str(target), 'fetch', '--depth=1',
                        'https://github.com/' + source['repository'] + '.git', source['ref']], check=True)
        subprocess.run(['git', '-C', str(target), 'checkout', '--detach', 'FETCH_HEAD'], check=True)
        if name in {'code-review', 'security'}:
            # Preserve upstream instructions, commands, skills and licenses. These local
            # extension copies deliberately expose no executable MCP servers or hooks.
            manifest_path = target / 'gemini-extension.json'
            manifest = json.loads(manifest_path.read_text())
            manifest.pop('mcpServers', None)
            manifest.pop('hooks', None)
            manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
            shutil.rmtree(target / 'hooks', ignore_errors=True)
        else:
            selected = selected_android if name == 'android' else selected_firebase
            found = set()
            for skill in target.rglob('SKILL.md'):
                if skill.parent.name in selected:
                    shutil.copytree(skill.parent, skills / skill.parent.name, dirs_exist_ok=True)
                    found.add(skill.parent.name)
            if found != selected:
                raise RuntimeError(f'Missing {name} skills: {selected - found}')
    print('Pinned code-review/security extensions and Android/Firebase skills prepared.')

# Scope trust to the trusted checkout in an isolated CI home. Never trust PR code.
if os.environ.get('GEMINI_CLI_HOME'):
    config = pathlib.Path(os.environ['GEMINI_CLI_HOME']) / '.gemini'
    config.mkdir(parents=True, exist_ok=True)
    (config / 'trustedFolders.json').write_text(json.dumps({str(root): 'TRUST_FOLDER'}))
