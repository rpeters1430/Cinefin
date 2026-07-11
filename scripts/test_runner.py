import unittest
from unittest.mock import AsyncMock, MagicMock, patch
import os
import sys
import json
import importlib.util

# Manually load the script module with hyphens
script_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "run-antigravity-agent.py")
spec = importlib.util.spec_from_file_location("run_antigravity_agent", script_path)
run_antigravity_agent = importlib.util.module_from_spec(spec)
sys.modules["run_antigravity_agent"] = run_antigravity_agent
spec.loader.exec_module(run_antigravity_agent)

class TestAntigravityRunner(unittest.TestCase):
    def setUp(self):
        # Setup mock environment variables
        os.environ["AVAILABLE_LABELS"] = "bug,enhancement,question"
        os.environ["ISSUE_TITLE"] = "Fix login crash"
        os.environ["ISSUE_BODY"] = "The app crashes on login button click."
        os.environ["GITHUB_ENV"] = "test_env_output.txt"
        
    def tearDown(self):
        # Clean up mock environment variables
        for key in ["AVAILABLE_LABELS", "ISSUE_TITLE", "ISSUE_BODY", "GITHUB_ENV"]:
            if key in os.environ:
                del os.environ[key]
        if os.path.exists("test_env_output.txt"):
            os.remove("test_env_output.txt")

    def test_evaluate_prompt(self):
        template = "Labels: !{echo $AVAILABLE_LABELS}\nTitle: !{echo $ISSUE_TITLE}\nFile: !{echo $GITHUB_ENV}"
        evaluated = run_antigravity_agent.evaluate_prompt(template)
        expected = "Labels: bug,enhancement,question\nTitle: Fix login crash\nFile: test_env_output.txt"
        self.assertEqual(evaluated, expected)

    def test_run_shell_command_allowed(self):
        result = run_antigravity_agent.run_shell_command("echo Hello")
        self.assertIn("Hello", result)

    def test_run_shell_command_disallowed(self):
        result = run_antigravity_agent.run_shell_command("rm -rf /")
        self.assertIn("Error: Command 'rm' is not allowed", result)

    @patch("run_antigravity_agent.Agent")
    @patch("run_antigravity_agent.LocalAgentConfig")
    @patch("run_antigravity_agent.McpStdioServer")
    def test_main_execution(self, mock_mcp, mock_config, mock_agent):
        import asyncio
        
        # Mock sys.argv
        sys.argv = [
            "run-antigravity-agent.py",
            "--prompt", "/gemini-triage",
            "--settings", '{"model":{"model":"gemini-2.5-flash"},"mcpServers":{"github":{"command":"docker","args":["run","-i","--rm"],"env":{"GITHUB_PERSONAL_ACCESS_TOKEN":"${GITHUB_TOKEN}"}}}}'
        ]
        
        # Mock Agent context manager
        mock_response = AsyncMock()
        mock_response.__aiter__.return_value = ["output_token_1", "output_token_2"]
        mock_agent_instance = MagicMock()
        mock_agent_instance.chat = AsyncMock(return_value=mock_response)
        
        # Async context manager mock
        mock_agent.return_value.__aenter__.return_value = mock_agent_instance
        mock_agent.return_value.__aexit__.return_value = AsyncMock()

        # Run main async
        asyncio.run(run_antigravity_agent.main())
        
        # Verify Agent and Config calls
        mock_agent.assert_called_once()
        mock_config.assert_called_once()
        
        # Assert mcp server was created with docker args
        mock_mcp.assert_called_once()
        _, mcp_kwargs = mock_mcp.call_args
        self.assertEqual(mcp_kwargs["name"], "github")
        self.assertEqual(mcp_kwargs["command"], "docker")

if __name__ == "__main__":
    unittest.main()
