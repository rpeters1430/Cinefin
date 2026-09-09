#!/usr/bin/env python3
"""
Validates Gemini CLI workflow and automation configurations.
Checks workflow YAML, CLI settings JSON, extension sources, and prompt templates.
"""

import json
import sys
from pathlib import Path

def validate_workflow(workflow_path):
    """Validate a Gemini workflow file."""
    path = Path(workflow_path)
    if not path.exists():
        print(f"  [ERROR] Workflow file not found: {path}")
        return False

    try:
        import yaml
    except ImportError:
        print("  [ERROR] PyYAML is not installed. Run: pip install pyyaml")
        return False

    try:
        with open(path, 'r', encoding='utf-8') as f:
            workflow = yaml.safe_load(f)

        if not isinstance(workflow, dict):
            print("  [ERROR] Workflow YAML did not parse to a dictionary")
            return False

        jobs = workflow.get('jobs', {})
        if 'analyze' not in jobs or 'publish' not in jobs:
            print("  [ERROR] Workflow must define both 'analyze' and 'publish' jobs")
            return False

        # Verify run-gemini-cli action step and settings
        settings_found = False
        for step in jobs['analyze'].get('steps', []):
            uses = step.get('uses', '')
            if 'google-github-actions/run-gemini-cli' in uses:
                settings_str = step.get('with', {}).get('settings')
                if settings_str:
                    settings_found = True
                    try:
                        settings = json.loads(settings_str)
                        if 'model' not in settings or 'tools' not in settings:
                            print("  [ERROR] Settings JSON missing 'model' or 'tools'")
                            return False
                        print(f"  [OK] Valid settings in run-gemini-cli (maxSessionTurns: {settings.get('model', {}).get('maxSessionTurns')})")
                    except json.JSONDecodeError as e:
                        print(f"  [ERROR] Invalid JSON in run-gemini-cli settings: {e}")
                        return False

        if not settings_found:
            print("  [ERROR] No settings block found in analyze job")
            return False

        print(f"  [OK] Workflow '{path.name}' is valid")
        return True
    except yaml.YAMLError as e:
        print(f"  [ERROR] YAML parsing error in {path.name}: {e}")
        return False

def validate_sources(sources_path):
    """Validate the pinned upstream sources definition."""
    path = Path(sources_path)
    if not path.exists():
        print(f"  [ERROR] Sources file not found: {path}")
        return False

    try:
        with open(path, 'r', encoding='utf-8') as f:
            sources = json.load(f)
        required = {'code-review', 'security', 'firebase', 'android'}
        missing = required - set(sources.keys())
        if missing:
            print(f"  [ERROR] Missing required sources in sources.json: {missing}")
            return False
        for name, entry in sources.items():
            if 'repository' not in entry or 'ref' not in entry:
                print(f"  [ERROR] Source '{name}' missing repository or ref")
                return False
        print(f"  [OK] Upstream sources in '{path.name}' are valid ({len(sources)} sources)")
        return True
    except Exception as e:
        print(f"  [ERROR] Error validating {path.name}: {e}")
        return False

def validate_prompts(prompts_dir):
    """Validate that review and triage prompt files exist and have content."""
    p_dir = Path(prompts_dir)
    for prompt_name in ['review.md', 'triage.md']:
        p_path = p_dir / prompt_name
        if not p_path.exists() or p_path.stat().st_size == 0:
            print(f"  [ERROR] Prompt file missing or empty: {p_path}")
            return False
        content = p_path.read_text(encoding='utf-8')
        if 'JSON' not in content:
            print(f"  [ERROR] Prompt {prompt_name} does not instruct model to output JSON")
            return False
        print(f"  [OK] Prompt '{prompt_name}' is valid ({len(content)} chars)")
    return True

def main():
    print("Gemini CLI Workflow & Config Validator")
    print("=" * 60)

    all_valid = True

    print("\n1. Validating Workflow:")
    if not validate_workflow('.github/workflows/gemini.yml'):
        all_valid = False

    print("\n2. Validating Upstream Sources:")
    if not validate_sources('.github/gemini/sources.json'):
        all_valid = False

    print("\n3. Validating Prompts:")
    if not validate_prompts('.github/gemini'):
        all_valid = False

    print("\n" + "=" * 60)
    if all_valid:
        print("[OK] All Gemini configurations and workflows are valid!")
        return 0
    else:
        print("[FAIL] Validation failed with errors.")
        return 1

if __name__ == '__main__':
    sys.exit(main())

