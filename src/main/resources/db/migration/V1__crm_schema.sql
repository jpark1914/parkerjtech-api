CREATE TABLE demo_workspace (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (expires_at > created_at)
);
CREATE INDEX workspace_expiry_idx ON demo_workspace(expires_at);

CREATE TABLE crm_lead (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES demo_workspace(id),
    name VARCHAR(100) NOT NULL CHECK (LENGTH(TRIM(name)) > 0),
    company VARCHAR(150) NOT NULL CHECK (LENGTH(TRIM(company)) > 0),
    service_interest VARCHAR(30) NOT NULL CHECK (service_interest IN ('WEBSITE','CUSTOM_APPLICATION','BUSINESS_AUTOMATION')),
    source VARCHAR(20) NOT NULL CHECK (source IN ('WEBSITE','SOCIAL_MEDIA','REFERRAL','OTHER')),
    estimated_value DECIMAL(12,2) NOT NULL CHECK (estimated_value >= 0 AND estimated_value <= 1000000),
    contact_preference VARCHAR(10) CHECK (contact_preference IN ('EMAIL','PHONE')),
    stage VARCHAR(15) NOT NULL CHECK (stage IN ('NEW','CONTACTED','QUALIFIED','WON','LOST')),
    priority VARCHAR(10) NOT NULL CHECK (priority IN ('LOW','MEDIUM','HIGH')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(workspace_id,id)
);
CREATE INDEX lead_workspace_idx ON crm_lead(workspace_id,created_at,id);

CREATE TABLE crm_task (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES demo_workspace(id),
    lead_id UUID NOT NULL,
    description VARCHAR(500) NOT NULL,
    due_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(10) NOT NULL CHECK (status IN ('OPEN')),
    automation_rule_key VARCHAR(60) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY(workspace_id,lead_id) REFERENCES crm_lead(workspace_id,id),
    UNIQUE(workspace_id,lead_id,automation_rule_key)
);
CREATE INDEX task_workspace_idx ON crm_task(workspace_id,due_at,id);

CREATE TABLE crm_activity_event (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES demo_workspace(id),
    lead_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL CHECK (type IN ('LEAD_CREATED','PRIORITY_ASSIGNED','FOLLOW_UP_CREATED')),
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY(workspace_id,lead_id) REFERENCES crm_lead(workspace_id,id)
);
CREATE INDEX activity_workspace_idx ON crm_activity_event(workspace_id,created_at,lead_id);
