#!/usr/bin/env bash
# Configure Claude Code to use a local Ollama model and start it.
#
#   scripts/claude-ollama.sh                       configure + start (the only installed model, or prompts)
#   scripts/claude-ollama.sh --model NAME          configure + start with NAME (e.g. qwen3.8)
#   scripts/claude-ollama.sh --context N           context window in tokens (default 65536; 0 keeps the server's)
#   scripts/claude-ollama.sh --host URL            Ollama server (default $OLLAMA_HOST or http://localhost:11434)
#   scripts/claude-ollama.sh --project DIR         configure + start in DIR
#   scripts/claude-ollama.sh --no-persist          use Ollama only for this session (no shell change)
#   scripts/claude-ollama.sh --help
#
# Undo with scripts/claude-reset.sh.
#
# Ollama serves the Anthropic Messages API itself (https://docs.ollama.com/integrations/claude-code),
# so Claude Code talks to it directly. The variables are written to ~/.claude/ollama.env, then sourced
# from your shell rc so every new shell keeps them. Ollama's default context is far smaller than
# Claude Code's system prompt and tools, so the script creates a copy of the model with a larger
# num_ctx (<model>-claude, which shares the original's weights) and uses that.
set -euo pipefail

say() { echo "claude-ollama: $*"; }
die() { echo "claude-ollama: $*" >&2; exit 1; }

model=""
context=65536
host="${OLLAMA_HOST:-http://localhost:11434}"
project=""
persist=1
env_file="$HOME/.claude/ollama.env"
deepseek_env_file="$HOME/.claude/deepseek.env"

while [ $# -gt 0 ]; do
  case "$1" in
    -m|--model) [ $# -ge 2 ] || die "--model requires a value"; model="$2"; shift 2 ;;
    -c|--context) [ $# -ge 2 ] || die "--context requires a value"; context="$2"; shift 2 ;;
    --host) [ $# -ge 2 ] || die "--host requires a value"; host="$2"; shift 2 ;;
    -p|--project) [ $# -ge 2 ] || die "--project requires a value"; project="$2"; shift 2 ;;
    --no-persist) persist=0; shift ;;
    -h|--help) sed -n '2,19p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown argument: $1 (see --help)" ;;
  esac
done

case "$context" in ''|*[!0-9]*) die "--context must be a number of tokens" ;; esac
case "$host" in http://*|https://*) ;; *) host="http://$host" ;; esac
host="${host%/}"

# Both scripts set ANTHROPIC_BASE_URL from the shell rc, and the agents (scripts/agent/lib.sh) treat
# a present deepseek.env as "use DeepSeek", so the two must not be configured at the same time.
if [ "$persist" -eq 1 ] && [ -f "$deepseek_env_file" ]; then
  die "DeepSeek is configured ($deepseek_env_file); run scripts/claude-reset.sh first, or use --no-persist"
fi

# --- Claude Code CLI and Ollama ----------------------------------------------
if ! command -v claude > /dev/null 2>&1; then
  say "claude CLI not found; installing with npm"
  command -v node > /dev/null 2>&1 || die "Node.js 18+ is required to install Claude Code (https://nodejs.org)"
  npm install -g @anthropic-ai/claude-code
fi
command -v ollama > /dev/null 2>&1 || die "ollama CLI not found (https://ollama.com/download)"
curl -fsS "$host/api/version" > /dev/null 2>&1 \
  || die "no Ollama server at $host; start it with 'ollama serve' (or the Ollama app)"
export OLLAMA_HOST="$host"

# --- model -------------------------------------------------------------------
# Reuse the model from a previous run so re-running (e.g. to change the context) is seamless.
if [ -z "$model" ] && [ -f "$env_file" ]; then
  model="$(sed -n "s/^# base model: //p" "$env_file" | tail -n 1)"
  [ -n "$model" ] && say "reusing model $model from $env_file"
fi
if [ -z "$model" ]; then
  installed=()
  while IFS= read -r name; do
    case "$name" in *-claude|*-claude:*) ;; *) installed+=("$name") ;; esac
  done < <(ollama list | awk 'NR > 1 { print $1 }')
  case "${#installed[@]}" in
    0) die "no Ollama model installed; pull one first, e.g. 'ollama pull qwen3.8'" ;;
    1) model="${installed[0]}" ;;
    *)
      [ -t 0 ] || die "several models installed (${installed[*]}); pass --model NAME"
      PS3="Ollama model: "
      select model in "${installed[@]}"; do [ -n "$model" ] && break; done
      ;;
  esac
fi
model="${model%:latest}"
case "$model" in *"'"*|*[[:space:]]*) die "model name contains invalid characters" ;; esac
ollama show "$model" > /dev/null 2>&1 || die "model $model is not installed; run 'ollama pull $model'"
curl -fsS "$host/api/show" -d "{\"model\":\"$model\"}" | grep -q '"capabilities":\[[^]]*"tools"' \
  || say "warning: $model does not report tool support; Claude Code needs tool calling"

claude_model="$model"
if [ "$context" -gt 0 ]; then
  claude_model="${model%%:*}-claude"
  [ "${model#*:}" != "$model" ] && claude_model="${claude_model}:${model#*:}"
  modelfile="$(mktemp)"
  printf 'FROM %s\nPARAMETER num_ctx %s\n' "$model" "$context" > "$modelfile"
  out="$(ollama create "$claude_model" -f "$modelfile" 2>&1)" || { rm -f "$modelfile"; die "could not create $claude_model: $out"; }
  rm -f "$modelfile"
  say "created $claude_model ($model with a $context-token context)"
fi

# --- Ollama environment ------------------------------------------------------
write_env_file() {
  local file="$1"
  mkdir -p "$(dirname "$file")"
  cat > "$file" <<EOF
# Managed by scripts/claude-ollama.sh (revert with scripts/claude-reset.sh).
# base model: $model
# Every Claude Code model slot (default, Opus, Sonnet, Haiku, subagents) -> $claude_model.
export ANTHROPIC_BASE_URL='$host'
# Ollama ignores the token, but Claude Code needs one to skip its login; an API key would win over it.
export ANTHROPIC_AUTH_TOKEN='ollama'
export ANTHROPIC_API_KEY=''
export ANTHROPIC_MODEL='$claude_model'
export ANTHROPIC_DEFAULT_OPUS_MODEL='$claude_model'
export ANTHROPIC_DEFAULT_SONNET_MODEL='$claude_model'
export ANTHROPIC_DEFAULT_HAIKU_MODEL='$claude_model'
export CLAUDE_CODE_SUBAGENT_MODEL='$claude_model'
EOF
  if [ "$context" -gt 0 ]; then
    echo "export CLAUDE_CODE_AUTO_COMPACT_WINDOW='$context'" >> "$file"
  fi
  cat >> "$file" <<'EOF'
# Ollama has no server-side auto-mode classifier checks; use Claude Code's own and skip the notice.
export CLAUDE_CODE_AUTO_MODE_SERVER='0'
# Keep everything local: no telemetry, error reporting or update checks to Anthropic.
export CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC='1'
EOF
}

if [ "$persist" -eq 1 ]; then
  write_env_file "$env_file"
  say "wrote $env_file"

  MARKER_START='# >>> claude-ollama >>>'
  MARKER_END='# <<< claude-ollama <<<'
  SOURCE_LINE='[ -f "$HOME/.claude/ollama.env" ] && . "$HOME/.claude/ollama.env"'

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
say "starting Claude Code with Ollama at $host (model ${ANTHROPIC_MODEL})"
exec claude
