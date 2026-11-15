-- Rebuild the child tables to replace unnamed inline CHECK constraints deterministically.
CREATE TABLE crm_task_v3 (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES demo_workspace(id),
    lead_id UUID NOT NULL,
    description VARCHAR(500) NOT NULL,
    due_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(10) NOT NULL,
    automation_rule_key VARCHAR(60) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CHECK ((status='OPEN' AND completed_at IS NULL) OR (status='COMPLETED' AND completed_at IS NOT NULL)),
    FOREIGN KEY(workspace_id,lead_id) REFERENCES crm_lead(workspace_id,id),
    UNIQUE(workspace_id,lead_id,automation_rule_key)
);

INSERT INTO crm_task_v3 (id,workspace_id,lead_id,description,due_at,status,automation_rule_key,created_at,completed_at)
SELECT id,workspace_id,lead_id,description,due_at,status,automation_rule_key,created_at,NULL
FROM crm_task;

DROP TABLE crm_task;
ALTER TABLE crm_task_v3 RENAME TO crm_task;
CREATE INDEX task_workspace_idx ON crm_task(workspace_id,due_at,id);

CREATE TABLE crm_activity_event_v3 (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES demo_workspace(id),
    lead_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL CHECK (type IN ('LEAD_CREATED','PRIORITY_ASSIGNED','FOLLOW_UP_CREATED','LEAD_UPDATED','LEAD_STAGE_CHANGED','FOLLOW_UP_COMPLETED')),
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY(workspace_id,lead_id) REFERENCES crm_lead(workspace_id,id)
);

INSERT INTO crm_activity_event_v3 (id,workspace_id,lead_id,type,description,created_at)
SELECT id,workspace_id,lead_id,type,description,created_at
FROM crm_activity_event;

DROP TABLE crm_activity_event;
ALTER TABLE crm_activity_event_v3 RENAME TO crm_activity_event;
CREATE INDEX activity_workspace_idx ON crm_activity_event(workspace_id,created_at,lead_id);
