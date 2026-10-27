#!/usr/bin/env bash
# The GitHub side of the repository (docs/ci-and-releases.md, "Setting up the repository on
# GitHub"): repository settings, the "main" ruleset, Actions permissions, security features, the
# labels the agent scripts and release notes use, and the "production" environment of the Deploy
# workflow. Needs gh logged in as an admin. Safe to run again: everything is create-or-update.
#
#   mise run github:setup
#
#   REPO=owner/name                 default: the repository of the current directory
#   DEPLOY_HOST=<ip or dns>         \
#   ACME_EMAIL=<address>             | the production secrets; each one is set only when given,
#   DEPLOY_KEY=<private key file>    | so a second run without them keeps the stored values
#   PUBLIC_DOMAIN=analystock.app    /  (DEPLOY_KNOWN_HOSTS comes from your known_hosts)
#   DEPLOY_USER, DEPLOY_PORT        optional variables (deploy and 22 when unset)
set -euo pipefail

REPO=${REPO:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}
ME_LOGIN=$(gh api user --jq .login)
ME_ID=$(gh api user --jq .id)
ROOT=$(cd "$(dirname "$0")/.." && pwd)
step() { printf '\n== %s\n' "$*"; }

step "Repository settings ($REPO)"
gh api -X PATCH "repos/$REPO" --silent \
  -F has_issues=true \
  -F allow_merge_commit=true -F allow_squash_merge=true -F allow_rebase_merge=true \
  -F allow_auto_merge=false -F allow_update_branch=false -F delete_branch_on_merge=false \
  -f merge_commit_title=MERGE_MESSAGE -f merge_commit_message=PR_TITLE

step "Actions: workflows get a read-only token by default and cannot approve pull requests"
gh api -X PUT "repos/$REPO/actions/permissions" --silent -F enabled=true -f allowed_actions=all
gh api -X PUT "repos/$REPO/actions/permissions/workflow" --silent \
  -f default_workflow_permissions=read -F can_approve_pull_request_reviews=false

step "Security: Dependabot alerts, secret scanning with push protection, private vulnerability reports"
gh api -X PUT "repos/$REPO/vulnerability-alerts" --silent
gh api -X PATCH "repos/$REPO" --silent --input - <<'JSON'
{"security_and_analysis": {"secret_scanning": {"status": "enabled"},
                           "secret_scanning_push_protection": {"status": "enabled"}}}
JSON
gh api -X PUT "repos/$REPO/private-vulnerability-reporting" --silent

step "Ruleset main: pull requests only, CI passed, up to date, code owner approval, admins may bypass"
# integration_id 15368 is the GitHub Actions app, so only a check from Actions satisfies "CI passed".
ruleset=$(cat <<'JSON'
{
  "name": "main",
  "target": "branch",
  "enforcement": "active",
  "conditions": {"ref_name": {"include": ["~DEFAULT_BRANCH"], "exclude": []}},
  "bypass_actors": [{"actor_id": 5, "actor_type": "RepositoryRole", "bypass_mode": "pull_request"}],
  "rules": [
    {"type": "deletion"},
    {"type": "non_fast_forward"},
    {"type": "pull_request", "parameters": {
      "allowed_merge_methods": ["merge", "squash", "rebase"],
      "required_approving_review_count": 1,
      "require_code_owner_review": true,
      "dismiss_stale_reviews_on_push": false,
      "require_last_push_approval": false,
      "required_review_thread_resolution": false}},
    {"type": "required_status_checks", "parameters": {
      "strict_required_status_checks_policy": true,
      "do_not_enforce_on_create": false,
      "required_status_checks": [{"context": "CI passed", "integration_id": 15368}]}}
  ]
}
JSON
)
id=$(gh api "repos/$REPO/rulesets" --jq '.[] | select(.name == "main") | .id')
if [ -n "$id" ]; then
  gh api -X PUT "repos/$REPO/rulesets/$id" --silent --input - <<<"$ruleset"
else
  gh api -X POST "repos/$REPO/rulesets" --silent --input - <<<"$ruleset"
fi

step "Labels"
label() { gh label create "$1" --repo "$REPO" --color "$2" --description "$3" --force >/dev/null; }
label agent         5319E7 "Driven by the agent loop; remove to stop it"
label plan          FBCA04 "A plan under review"
label agent-dashboard 1D76DB "The issue that shows the active agent processes"
label enhancement   a2eeef "New feature or request"
label bug           d73a4a "Something isn't working"
label security      B60205 "Vulnerabilities, supply chain, secrets"
label documentation 0075ca "Improvements or additions to documentation"
label adr           0E8A16 "Architecture decision records"
label ci            0E8A16 "Continuous integration pipeline"
label dependencies  0366D6 "Dependency updates"

step "Environment production: you approve every deploy, deployments from main only"
gh api -X PUT "repos/$REPO/environments/production" --silent --input - <<JSON
{"prevent_self_review": false,
 "reviewers": [{"type": "User", "id": $ME_ID}],
 "deployment_branch_policy": {"protected_branches": false, "custom_branch_policies": true}}
JSON
if ! gh api "repos/$REPO/environments/production/deployment-branch-policies" \
     --jq '.branch_policies[].name' | grep -qx main; then
  gh api -X POST "repos/$REPO/environments/production/deployment-branch-policies" --silent \
    -f name=main -f type=branch
fi

env_secret() { gh secret set "$1" --repo "$REPO" --env production; }
if [ -n "${DEPLOY_KEY:-}" ]; then env_secret DEPLOY_SSH_KEY < "$DEPLOY_KEY"; fi
if [ -n "${DEPLOY_HOST:-}" ]; then
  printf '%s' "$DEPLOY_HOST" | env_secret DEPLOY_HOST
  pattern=$DEPLOY_HOST
  if [ -n "${DEPLOY_PORT:-}" ] && [ "$DEPLOY_PORT" != 22 ]; then pattern="[$DEPLOY_HOST]:$DEPLOY_PORT"; fi
  known=$(ssh-keygen -F "$pattern" | grep -v '^#' || true)
  if [ -z "$known" ]; then
    echo "No host key for $pattern in ~/.ssh/known_hosts: ssh to it once, check the fingerprint, run again." >&2
    exit 1
  fi
  printf '%s\n' "$known" | env_secret DEPLOY_KNOWN_HOSTS
fi
if [ -n "${ACME_EMAIL:-}" ]; then printf '%s' "$ACME_EMAIL" | env_secret ACME_EMAIL; fi
gh variable set PUBLIC_DOMAIN --repo "$REPO" --env production --body "${PUBLIC_DOMAIN:-analystock.app}"
if [ -n "${DEPLOY_USER:-}" ]; then gh variable set DEPLOY_USER --repo "$REPO" --env production --body "$DEPLOY_USER"; fi
if [ -n "${DEPLOY_PORT:-}" ]; then gh variable set DEPLOY_PORT --repo "$REPO" --env production --body "$DEPLOY_PORT"; fi

step "Check"
have=$(gh secret list --repo "$REPO" --env production --json name --jq '.[].name')
for s in DEPLOY_SSH_KEY DEPLOY_HOST DEPLOY_KNOWN_HOSTS ACME_EMAIL; do
  grep -qx "$s" <<<"$have" && echo "ok: secret $s" || echo "MISSING: secret $s (Deploy fails until it is set)"
done
grep -q "@$ME_LOGIN" "$ROOT/.github/CODEOWNERS" 2>/dev/null \
  && echo "ok: CODEOWNERS names @$ME_LOGIN" || echo "CHECK: .github/CODEOWNERS should name @$ME_LOGIN"
echo "Not settable by API: install the Renovate app on the repository (https://github.com/apps/renovate)."
