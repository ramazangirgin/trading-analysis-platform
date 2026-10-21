#!/usr/bin/env bash
# Remove every alternative endpoint configured by scripts/claude-deepseek.sh or
# scripts/claude-ollama.sh and fully restore Claude Code to its default Anthropic (Claude) usage.
#
#   scripts/claude-reset.sh           remove the env files and every rc block
#   scripts/claude-reset.sh --help
#
# Deletes ~/.claude/deepseek.env (which holds your DeepSeek API key) and ~/.claude/ollama.env,
# and removes their marked blocks from all common shell startup files, so Claude Code goes back
# to your normal Anthropic account. The <model>-claude copy in Ollama is kept; remove it with
# 'ollama rm'. Open a new terminal afterwards for the change to take effect.
set -euo pipefail

say() { echo "claude-reset: $*"; }

case "${1:-}" in
  -h|--help) sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  '') ;;
  *) echo "claude-reset: unknown argument: $1 (see --help)" >&2; exit 2 ;;
esac

PROVIDERS=(deepseek ollama)
RC_FILES=("$HOME/.zshrc" "$HOME/.bashrc" "$HOME/.zprofile" "$HOME/.profile" "$HOME/.bash_profile")

remove_from_rc() {
  local rc="$1" provider="$2" start="# >>> claude-$2 >>>" end="# <<< claude-$2 <<<" tmp
  [ -f "$rc" ] || return 1
  grep -qF "$start" "$rc" || return 1
  tmp="${rc}.claude-reset.$$"
  awk -v start="$start" -v end="$end" '
    $0 == start { skip = 1; next }
    $0 == end   { skip = 0; next }
    skip == 0   { print }
  ' "$rc" > "$tmp"
  mv "$tmp" "$rc"
  say "removed $provider block from $rc"
}

changed=0
for provider in "${PROVIDERS[@]}"; do
  for rc in "${RC_FILES[@]}"; do
    remove_from_rc "$rc" "$provider" && changed=1
  done
  env_file="$HOME/.claude/$provider.env"
  if [ -f "$env_file" ]; then
    rm -f "$env_file"
    say "removed $env_file"
    changed=1
  fi
done

if [ "$changed" -eq 0 ]; then
  say "no DeepSeek or Ollama configuration found; nothing to reset."
  say "Claude Code is already using its default Anthropic (Claude) settings."
else
  say "done. Claude Code now uses your default Anthropic (Claude) account."
  say "Open a new terminal (or start a new shell) for the change to take effect."
  say "To clear it from the current shell immediately, run:"
  say "  unset ANTHROPIC_BASE_URL ANTHROPIC_AUTH_TOKEN ANTHROPIC_API_KEY ANTHROPIC_MODEL ANTHROPIC_DEFAULT_OPUS_MODEL ANTHROPIC_DEFAULT_SONNET_MODEL ANTHROPIC_DEFAULT_HAIKU_MODEL CLAUDE_CODE_SUBAGENT_MODEL CLAUDE_CODE_EFFORT_LEVEL CLAUDE_CODE_AUTO_COMPACT_WINDOW CLAUDE_CODE_AUTO_MODE_SERVER CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC"
fi
