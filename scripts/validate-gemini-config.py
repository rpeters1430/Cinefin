#!/usr/bin/env python3
"""
Validates Gemini CLI workflow configurations.
Checks that all workflow files have valid YAML and JSON settings.
"""

import yaml
import json
import sys
from pathlib import Path

ALLOWED_WORKFLOWS = {'ci.yaml', 'ci.yml', 'build.yaml', 'build.yml'}

def validate_workflow(workflow_path):
    """Validate a single workflow file."""
    path_obj = Path(workflow_path)
    if path_obj.name not in ALLOWED_WORKFLOWS:
        print(f"Error: Workflow file '{path_obj.name}' is not allowed.")
        sys.exit(1)
    safe_path = path_obj if path_obj.is_absolute() else Path.cwd() / path_obj.name
    try:
        with open(safe_path, 'r', encoding='utf-8') as f:
            workflow = yaml.safe_load(f)

        
        # Check for settings in workflow
        settings_found = False
        for job_name, job_data in workflow.get('jobs', {}).items():
            for step in job_data.get('steps', []):
                settings_str = None
                if 'with' in step and 'settings' in step.get('with', {}):
                    settings_str = step['with']['settings']
                elif 'env' in step and 'SETTINGS' in step.get('env', {}):
                    settings_str = step['env']['SETTINGS']

                if settings_str is not None:
                    settings_found = True
                    
                    # Validate JSON
                    try:
                        settings_obj = json.loads(settings_str)
                        print(f"  ✓ Job '{job_name}': Valid JSON settings ({len(settings_str)} chars)")

                        
                        # Check for required fields
                        if 'model' in settings_obj:
                            max_turns = settings_obj['model'].get('maxSessionTurns', 'not set')
                            print(f"    - maxSessionTurns: {max_turns}")
                        if 'mcpServers' in settings_obj:
                            servers = list(settings_obj['mcpServers'].keys())
                            print(f"    - MCP Servers: {', '.join(servers)}")
                        if 'tools' in settings_obj:
                            core_tools = settings_obj['tools'].get('core', [])
                            print(f"    - Core tools: {len(core_tools)} commands")
                            
                    except json.JSONDecodeError as e:
                        print(f"  ✗ Job '{job_name}': JSON Error at position {e.pos}")
                        print(f"    {e.msg}")
                        return False
        
        if not settings_found:
            print(f"  ℹ No Gemini settings found (might be a dispatcher workflow)")
        
        return True
        
    except yaml.YAMLError as e:
        print(f"  ✗ YAML parsing error: {e}")
        return False
    except Exception as e:
        print(f"  ✗ Unexpected error: {e}")
        return False

def main():
    """Main validation function."""
    print("Gemini CLI Workflow Validator")
    print("=" * 60)
    
    # Find all Gemini workflow files
    workflow_dir = Path('.github/workflows')
    workflow_files = sorted(workflow_dir.glob('gemini-*.yml'))
    
    if not workflow_files:
        print("No Gemini workflow files found!")
        return 1
    
    all_valid = True
    for wf_file in workflow_files:
        print(f"\n{wf_file.name}:")
        if not validate_workflow(wf_file):
            all_valid = False
    
    print("\n" + "=" * 60)
    if all_valid:
        print("✓ All Gemini workflows are valid!")
        return 0
    else:
        print("✗ Some workflows have errors")
        return 1

if __name__ == '__main__':
    sys.exit(main())
