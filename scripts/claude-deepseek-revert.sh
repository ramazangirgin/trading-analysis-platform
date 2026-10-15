#!/usr/bin/env bash
# Remove the DeepSeek API configuration written by scripts/claude-deepseek.sh and
# fully restore Claude Code to its default Anthropic (Claude) usage.
#
#   scripts/claude-deepseek-revert.sh           remove the DeepSeek env file and every rc block
#   scripts/claude-deepseek-revert.sh --help
#
# Deletes ~/.claude/deepseek.env (which holds your DeepSeek API key) and removes the
# marked block from all common shell startup files, so Claude Code goes back to your
# normal Anthropic account. Open a new terminal afterwards for the change to take effect.
set -euo pipefail

say() { echo "claude-deepseek-revert: $*"; }

case "${1:-}" in
  -h|--help) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  '') ;;
  *) echo "claude-deepseek-revert: unknown argument: $1 (see --help)" >&2; exit 2 ;;
esac

MARKER_START='# >>> claude-deepseek >>>'
MARKER_END='# <<< claude-deepseek <<<'
ENV_FILE="$HOME/.claude/deepseek.env"
RC_FILES=("$HOME/.zshrc" "$HOME/.bashrc" "$HOME/.zprofile" "$HOME/.profile" "$HOME/.bash_profile")

remove_from_rc() {
  local rc="$1" tmp
  [ -f "$rc" ] || return 0
  grep -qF "$MARKER_START" "$rc" || return 0
  tmp="${rc}.deepseek-revert.$$"
  awk -v start="$MARKER_START" -v end="$MARKER_END" '
    $0 == start { skip = 1; next }
    $0 == end   { skip = 0; next }
    skip == 0   { print }
  ' "$rc" > "$tmp"
  mv "$tmp" "$rc"
  say "removed DeepSeek block from $rc"
}

changed=0
for rc in "${RC_FILES[@]}"; do
  if [ -f "$rc" ] && grep -qF "$MARKER_START" "$rc"; then
    remove_from_rc "$rc"
    changed=1
  fi
done

if [ -f "$ENV_FILE" ]; then
  rm -f "$ENV_FILE"
  say "removed $ENV_FILE"
  changed=1
fi

if [ "$changed" -eq 0 ]; then
  say "no DeepSeek configuration found; nothing to revert."
  say "Claude Code is already using its default Anthropic (Claude) settings."
else
  say "done. Claude Code now uses your default Anthropic (Claude) account."
  say "Open a new terminal (or start a new shell) for the change to take effect."
  say "To clear it from the current shell immediately, run:"
  say "  unset ANTHROPIC_BASE_URL ANTHROPIC_AUTH_TOKEN ANTHROPIC_MODEL ANTHROPIC_DEFAULT_OPUS_MODEL ANTHROPIC_DEFAULT_SONNET_MODEL ANTHROPIC_DEFAULT_HAIKU_MODEL CLAUDE_CODE_SUBAGENT_MODEL CLAUDE_CODE_EFFORT_LEVEL CLAUDE_CODE_AUTO_COMPACT_WINDOW"
fi
