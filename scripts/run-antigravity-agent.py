#!/usr/bin/env python3
import argparse
import asyncio
import json
import os
import re
import subprocess
import sys
from google.antigravity import Agent, LocalAgentConfig, CapabilitiesConfig, BuiltinTools
from google.antigravity.types import McpStdioServer

try:
    import tomllib
except ImportError:
    import toml as tomllib

def evaluate_prompt(prompt_template):
    def replace_env(match):
        var_name = match.group(1)
        return os.environ.get(var_name, '')
    
    # Resolves pattern like !{echo $VAR_NAME} or !{echo  $VAR_NAME}
    pattern = r'!\{\s*echo\s+\$([a-zA-Z0-9_]+)\s*\}'
    return re.sub(pattern, replace_env, prompt_template)

def run_shell_command(command: str) -> str:
    """Execute a shell command. Only cat, echo, grep, head, tail, and jq are allowed for security.
    """
    parts = command.strip().split()
    if not parts:
        return "Error: Empty command"
    
    binary = parts[0]
    binary_name = os.path.basename(binary)
    
    allowed = ["cat", "echo", "grep", "head", "tail", "jq"]
    if binary_name not in allowed:
        return f"Error: Command '{binary_name}' is not allowed. Only {allowed} are permitted."
    
    try:
        result = subprocess.run(
            command,
            shell=True,
            text=True,
            capture_output=True,
            env=os.environ
        )
        output = result.stdout
        if result.stderr:
            output += "\n" + result.stderr
        return output
    except Exception as e:
        return f"Error executing command: {str(e)}"

async def main():
    parser = argparse.ArgumentParser(description="Run Antigravity Agent inside GitHub Actions")
    parser.add_argument("--prompt", required=True, help="The prompt or slash command (e.g. /gemini-invoke)")
    parser.add_argument("--settings", default="{}", help="JSON settings block")
    parser.add_argument("--api-key", default=None, help="Gemini API Key")
    parser.add_argument("--model", default=None, help="Model name")
    parser.add_argument("--vertex", action="store_true", help="Use Vertex AI")
    parser.add_argument("--project", default=None, help="GCP Project ID")
    parser.add_argument("--location", default=None, help="GCP Location")
    
    args = parser.parse_args()
    
    # 1. Parse prompt and load TOML if it is a slash command
    prompt_str = args.prompt
    system_instructions = None
    
    if prompt_str.startswith("/"):
        command_name = prompt_str[1:]
        toml_path = f".github/commands/{command_name}.toml"
        if not os.path.exists(toml_path):
            print(f"Error: Command file {toml_path} not found.")
            sys.exit(1)
            
        print(f"Loading command template from {toml_path}...")
        try:
            with open(toml_path, "rb") as f:
                cmd_data = tomllib.load(f)
        except Exception:
            with open(toml_path, "r", encoding="utf-8") as f_txt:
                import toml
                cmd_data = toml.loads(f_txt.read())
                    
        prompt_template = cmd_data.get("prompt", "")
        prompt_str = evaluate_prompt(prompt_template)
        system_instructions = cmd_data.get("description", "You are an autonomous agent.")
        
    # 2. Parse settings
    settings = {}
    if args.settings:
        try:
            settings = json.loads(args.settings)
        except json.JSONDecodeError as e:
            print(f"Warning: Failed to parse settings JSON: {e}")
            
    # Extract model options from settings if not overridden by arguments
    model_name = args.model or settings.get("model", {}).get("model") or os.environ.get("GEMINI_MODEL")
    
    # 3. Setup MCP Servers
    mcp_servers = []
    mcp_config = settings.get("mcpServers", {})
    for server_name, server_data in mcp_config.items():
        # Resolve GITHUB_TOKEN or other env variables in args/env
        env_vars = {}
        for k, v in server_data.get("env", {}).items():
            if isinstance(v, str):
                matches = re.findall(r'\$\{?([a-zA-Z0-9_]+)\}?', v)
                for m in matches:
                    v = v.replace(f"${m}", os.environ.get(m, "")).replace(f"${{{m}}}", os.environ.get(m, ""))
            env_vars[k] = v
            
        args_resolved = []
        for a in server_data.get("args", []):
            if isinstance(a, str):
                matches = re.findall(r'\$\{?([a-zA-Z0-9_]+)\}?', a)
                for m in matches:
                    a = a.replace(f"${m}", os.environ.get(m, "")).replace(f"${{{m}}}", os.environ.get(m, ""))
            args_resolved.append(a)
            
        mcp_server = McpStdioServer(
            name=server_name,
            command=server_data.get("command"),
            args=args_resolved,
            env=env_vars if env_vars else None,
            enabled_tools=server_data.get("includeTools", None),
            disabled_tools=server_data.get("excludeTools", None)
        )
        mcp_servers.append(mcp_server)
        print(f"Configured MCP Server: {server_name}")

    # 4. Setup CapabilitiesConfig
    # Disabling all built-in tools for safety and security
    capabilities = CapabilitiesConfig(
        enable_subagents=False,
        enabled_tools=None,
        disabled_tools=list(BuiltinTools)
    )
    
    # Resolve API Key (prefers AV_API_KEY)
    api_key = args.api_key or os.environ.get("AV_API_KEY") or os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY")
    
    # 5. Create LocalAgentConfig
    config = LocalAgentConfig(
        system_instructions=system_instructions,
        capabilities=capabilities,
        tools=[run_shell_command],
        mcp_servers=mcp_servers,
        workspaces=[os.getcwd()],
        api_key=api_key,
        model=model_name,
        vertex=args.vertex or settings.get("use_vertex_ai", False),
        project=args.project or settings.get("gcp_project_id"),
        location=args.location or settings.get("gcp_location")
    )
    
    print("Spawning Antigravity Agent...")
    async with Agent(config) as agent:
        response = await agent.chat(prompt_str)
        # Stream response
        print("--- Agent Response ---")
        async for token in response:
            sys.stdout.write(token)
            sys.stdout.flush()
        print("\n----------------------")

if __name__ == "__main__":
    asyncio.run(main())
