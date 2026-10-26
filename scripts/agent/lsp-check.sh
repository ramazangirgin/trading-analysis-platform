#!/usr/bin/env bash
# Does a headless agent get Claude Code's LSP tool from the repository plugin (scripts/agent/lsp/),
# and how long does each language server take to its first answer? Issue #251.
#
#   scripts/agent/lsp-check.sh [--twice]
#
# Runs one short, read-only agent per language (Java, TypeScript with a .vue file, Python) with
# AGENT_LSP on and prints one line each: the tool names the stream lists, whether the agent got an
# answer from LSP, the wall time, the turns and tokens, and the first line of the answer. With
# --twice the Java check runs a second time: the jdtls index of the worktree is warm then. Needs the
# servers (docs/agentic-development.md, "Trying the LSP tool") and the developer's Claude Code login.
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

twice=false
case "${1:-}" in
  "") ;;
  --twice) twice=true ;;
  *) die "usage: $0 [--twice]" ;;
esac

AGENT_LSP=1

schema='{"type": "object", "required": ["tool_seen", "answer"], "properties": {
  "tool_seen": {"type": "boolean"}, "answer": {"type": "string"}}}'

# check <name> <prompt text>
check() {
  local name=$1 file=$AGENT_TMP/lsp-check-$1.md out=$AGENT_TMP/lsp-check-$1.json started elapsed listed
  {
    echo "This is a check of the LSP tool. Call the \`$AGENT_LSP_TOOL\` tool as described below, do not use"
    echo "Grep or Bash to find the answer, and make no other call than the ones asked."
    echo
    echo "$2"
    echo
    echo "Answer with tool_seen: true when the $AGENT_LSP_TOOL tool was available and answered, false when it"
    echo "was missing or failed, and answer: the first lines of what it returned (or its error message)."
  } >"$file"
  started=$(date +%s)
  run_agent "$AGENT_MODEL" "$file" "$out" "${AGENT_REVIEW_TOOLS[@]}" -- --max-turns 12 --json-schema "$schema"
  elapsed=$(($(date +%s) - started))
  listed=no
  if py "$AGENT_DIR/agent_json.py" tools "${out}l" | grep -qx "$AGENT_LSP_TOOL"; then listed=yes; fi
  printf '%s: %s in the tool list: %s; answered: %s; %ss; %s turns; %s; %s\n' "$name" "$AGENT_LSP_TOOL" "$listed" \
    "$(py "$AGENT_DIR/agent_json.py" get "$out" structured_output | py -c 'import json, sys; print((json.load(sys.stdin) or {}).get("tool_seen"))')" \
    "$elapsed" "$(py "$AGENT_DIR/agent_json.py" get "$out" num_turns)" \
    "$(py "$AGENT_DIR/agent_json.py" usage-line "$out")" \
    "$(py "$AGENT_DIR/agent_json.py" get "$out" structured_output | py -c 'import json, sys; print(((json.load(sys.stdin) or {}).get("answer") or "").splitlines()[:1] or "")' | cut -c1-160)"
}

java_prompt="Operation findReferences on the class DomainExceptionHandler in
artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/error/DomainExceptionHandler.java
(find its line with the Read tool, limit 60 lines)."

check java "$java_prompt"
if $twice; then check java-warm "$java_prompt"; fi
check typescript "Two calls. Operation findReferences on the exported function request in
artifact/frontend/src/shared/api/client.ts (find its line first), then operation documentSymbol on
artifact/frontend/src/app/App.vue. Say in the answer whether the second one returned symbols."
check python "Operation findReferences on the function build_config in
artifact/ta-runner/ta_runner/engine/compat.py (find its line first)."
