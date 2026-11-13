#!/usr/bin/env bash
set -eu

# Cookie and CSRF values stay in temporary files. Use a local demo backend.
crm_base_url=${CRM_BASE_URL:-http://localhost:8080}
crm_smoke_dir=$(mktemp -d "${TMPDIR:-/tmp}/parkerjtech-crm-smoke.XXXXXX")
trap 'rm -rf -- "$crm_smoke_dir"' EXIT

curl -fsS -c "$crm_smoke_dir/a.cookies" -b "$crm_smoke_dir/a.cookies" \
  "$crm_base_url/api/demo/session" -o "$crm_smoke_dir/session.json"
crm_csrf_token=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["csrfToken"])' "$crm_smoke_dir/session.json")
crm_csrf_header=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["csrfHeaderName"])' "$crm_smoke_dir/session.json")

cat > "$crm_smoke_dir/request.json" <<'JSON'
{
  "name": "Fictional Sam",
  "company": "Demo Company",
  "serviceInterest": "BUSINESS_AUTOMATION",
  "source": "REFERRAL",
  "estimatedValue": 5000,
  "contactPreference": "EMAIL"
}
JSON

crm_create_status=$(curl -fsS -c "$crm_smoke_dir/a.cookies" -b "$crm_smoke_dir/a.cookies" \
  -H 'Content-Type: application/json' -H "$crm_csrf_header: $crm_csrf_token" \
  --data-binary "@$crm_smoke_dir/request.json" \
  "$crm_base_url/api/demo/leads" -o "$crm_smoke_dir/lead.json" -w '%{http_code}')
test "$crm_create_status" = 201
crm_lead_id=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["id"])' "$crm_smoke_dir/lead.json")

curl -fsS -c "$crm_smoke_dir/a.cookies" -b "$crm_smoke_dir/a.cookies" \
  "$crm_base_url/api/demo/leads/$crm_lead_id" -o "$crm_smoke_dir/fetched.json"
for crm_resource in leads tasks activity; do
  curl -fsS -c "$crm_smoke_dir/a.cookies" -b "$crm_smoke_dir/a.cookies" \
    "$crm_base_url/api/demo/$crm_resource" -o "$crm_smoke_dir/$crm_resource.json"
done

curl -fsS -c "$crm_smoke_dir/b.cookies" -b "$crm_smoke_dir/b.cookies" \
  "$crm_base_url/api/demo/session" -o "$crm_smoke_dir/session-b.json"
for crm_resource in leads tasks activity; do
  curl -fsS -c "$crm_smoke_dir/b.cookies" -b "$crm_smoke_dir/b.cookies" \
    "$crm_base_url/api/demo/$crm_resource" -o "$crm_smoke_dir/b-$crm_resource.json"
done
crm_hidden_status=$(curl -sS -c "$crm_smoke_dir/b.cookies" -b "$crm_smoke_dir/b.cookies" \
  "$crm_base_url/api/demo/leads/$crm_lead_id" -o "$crm_smoke_dir/hidden.json" -w '%{http_code}')
test "$crm_hidden_status" = 404

python3 - "$crm_smoke_dir" <<'PY'
import json
import sys
from pathlib import Path

root = Path(sys.argv[1])
def load(name):
    return json.loads((root / (name + '.json')).read_text())

lead = load('lead')
assert lead['stage'] == 'NEW' and lead['priority'] == 'HIGH'
assert lead['estimatedValue'] == 5000 and lead['contactPreference'] == 'EMAIL'
assert load('fetched') == lead
assert load('leads')['items'] == [lead]
tasks = load('tasks')['items']
assert len(tasks) == 1 and tasks[0]['leadId'] == lead['id']
assert tasks[0]['status'] == 'OPEN' and tasks[0]['automationRuleKey'] == 'INITIAL_FOLLOW_UP'
events = load('activity')['items']
assert [e['type'] for e in events] == ['LEAD_CREATED', 'PRIORITY_ASSIGNED', 'FOLLOW_UP_CREATED']
assert all(e['leadId'] == lead['id'] for e in events)
assert load('session')['workspaceId'] != load('session-b')['workspaceId']
assert all(load('b-' + name)['items'] == [] for name in ('leads', 'tasks', 'activity'))
assert load('hidden')['code'] == 'LEAD_NOT_FOUND'
print('PASS: created/read a lead; 1 follow-up; 3 activity events; visitor isolation verified.')
PY
