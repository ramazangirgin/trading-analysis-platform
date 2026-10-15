#!/usr/bin/env bash
# Configure Claude Code to use the DeepSeek API and start it.
#
#   scripts/claude-deepseek.sh                     configure + start (reads DEEPSEEK_API_KEY, or prompts)
#   scripts/claude-deepseek.sh --api-key KEY       configure + start with KEY
#   scripts/claude-deepseek.sh --project DIR       configure + start in DIR
#   scripts/claude-deepseek.sh --no-persist        use DeepSeek only for this session (no shell change)
#   scripts/claude-deepseek.sh --help
#
# Undo with scripts/claude-deepseek-revert.sh.
#
# The environment variables follow https://api-docs.deepseek.com/quick_start/agent_integrations/claude_code/
# and are written to ~/.claude/deepseek.env (mode 0600), then sourced from your shell rc so every new
# shell keeps them. Claude Code is installed with npm when it is missing. Get a key at
# https://platform.deepseek.com/ .
set -euo pipefail

say() { echo "claude-deepseek: $*"; }
die() { echo "claude-deepseek: $*" >&2; exit 1; }

api_key="${DEEPSEEK_API_KEY:-}"
project=""
persist=1
env_file="$HOME/.claude/deepseek.env"

while [ $# -gt 0 ]; do
  case "$1" in
    -k|--api-key) [ $# -ge 2 ] || die "--api-key requires a value"; api_key="$2"; shift 2 ;;
    -p|--project) [ $# -ge 2 ] || die "--project requires a value"; project="$2"; shift 2 ;;
    --no-persist) persist=0; shift ;;
    -h|--help) sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown argument: $1 (see --help)" ;;
  esac
done

# --- Claude Code CLI ---------------------------------------------------------
if ! command -v claude > /dev/null 2>&1; then
  say "claude CLI not found; installing with npm"
  command -v node > /dev/null 2>&1 || die "Node.js 18+ is required to install Claude Code (https://nodejs.org)"
  npm install -g @anthropic-ai/claude-code
fi

# --- API key -----------------------------------------------------------------
# Reuse the key from a previous run so re-running (e.g. to switch models) is seamless.
if [ -z "$api_key" ] && [ -f "$env_file" ]; then
  api_key="$(sed -n "s/^export ANTHROPIC_AUTH_TOKEN='\(.*\)'/\1/p" "$env_file" | tail -n 1)"
  [ -n "$api_key" ] && say "reusing API key from $env_file"
fi
if [ -z "$api_key" ]; then
  if [ -t 0 ]; then
    read -r -s -p "DeepSeek API key: " api_key || true
    echo
  else
    die "no API key: set DEEPSEEK_API_KEY or pass --api-key KEY"
  fi
fi
[ -n "$api_key" ] || die "API key is empty"
case "$api_key" in *"'"*|*$'\n'*|*$'\r'*) die "API key contains invalid characters" ;; esac

# --- DeepSeek environment ----------------------------------------------------
write_env_file() {
  local file="$1" old_umask
  mkdir -p "$(dirname "$file")"
  old_umask="$(umask)"
  umask 077
  cat > "$file" <<EOF
# Managed by scripts/claude-deepseek.sh (revert with scripts/claude-deepseek-revert.sh).
# Default/Opus -> deepseek-v4-pro (DeepSeek-V4-Pro-0813); Sonnet/Haiku/subagent -> deepseek-flash.
export ANTHROPIC_BASE_URL='https://api.deepseek.com/anthropic'
export ANTHROPIC_AUTH_TOKEN='$api_key'
export ANTHROPIC_MODEL='deepseek-v4-pro'
export ANTHROPIC_DEFAULT_OPUS_MODEL='deepseek-v4-pro'
export ANTHROPIC_DEFAULT_SONNET_MODEL='deepseek-flash[1m]'
export ANTHROPIC_DEFAULT_HAIKU_MODEL='deepseek-flash'
export CLAUDE_CODE_SUBAGENT_MODEL='deepseek-flash'
export CLAUDE_CODE_EFFORT_LEVEL='max'
export CLAUDE_CODE_AUTO_COMPACT_WINDOW='786432'
# DeepSeek has no server-side auto-mode classifier checks; use Claude Code's own and skip the notice.
export CLAUDE_CODE_AUTO_MODE_SERVER='0'
EOF
  umask "$old_umask"
}

if [ "$persist" -eq 1 ]; then
  write_env_file "$env_file"
  say "wrote $env_file"

  MARKER_START='# >>> claude-deepseek >>>'
  MARKER_END='# <<< claude-deepseek <<<'
  SOURCE_LINE='[ -f "$HOME/.claude/deepseek.env" ] && . "$HOME/.claude/deepseek.env"'

  found_rc=0
  for rc in "$HOME/.zshrc" "$HOME/.bashrc"; do
    if [ -f "$rc" ]; then
      found_rc=1
      if grep -qF "$MARKER_START" "$rc"; then
        say "config already present in $rc"
      else
        printf '%s\n%s\n%s\n' "$MARKER_START" "$SOURCE_LINE" "$MARKER_END" >> "$rc"
        say "persisted config to $rc"
      fi
    fi
  done
  if [ "$found_rc" -eq 0 ]; then
    case "${SHELL:-}" in
      *bash*) rc="$HOME/.bashrc" ;;
      *) rc="$HOME/.zshrc" ;;
    esac
    printf '%s\n%s\n%s\n' "$MARKER_START" "$SOURCE_LINE" "$MARKER_END" >> "$rc"
    say "persisted config to $rc"
  fi

  # shellcheck disable=SC1090
  . "$env_file"
else
  tmp="$(mktemp)"
  write_env_file "$tmp"
  # shellcheck disable=SC1090
  . "$tmp"
  rm -f "$tmp"
fi

# --- start -------------------------------------------------------------------
if [ -n "$project" ]; then
  cd "$project" || die "cannot cd to $project"
fi
say "starting Claude Code with the DeepSeek API (model ${ANTHROPIC_MODEL})"
exec claude
