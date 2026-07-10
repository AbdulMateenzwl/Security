-- ---------------------------------------------------------------------------
-- V5 — Tasks and activity log (in-chat collaboration boards)
--
-- Design notes:
--   * status / priority / action are VARCHAR + CHECK (not native enums) to match
--     JPA @Enumerated(STRING) and pass Hibernate ddl-auto: validate.
--   * labels live in a task_labels collection table (JPA @ElementCollection),
--     not a native TEXT[] column — cleaner to validate and map.
--   * Every task mutation writes a task_activity_log row for an audit trail.
--   * Tasks are always scoped to a chat; authorization (membership, creator/admin)
--     is enforced in the service layer.
-- ---------------------------------------------------------------------------

CREATE TABLE tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    assigned_to UUID REFERENCES users(id),
    created_by UUID NOT NULL REFERENCES users(id),
    due_date TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_task_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE')),
    CONSTRAINT chk_task_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))
);

CREATE TABLE task_labels (
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    label VARCHAR(50) NOT NULL
);

CREATE TABLE task_activity_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    performed_by UUID NOT NULL REFERENCES users(id),
    action VARCHAR(30) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_task_action CHECK (action IN (
        'CREATED', 'STATUS_CHANGED', 'PRIORITY_CHANGED',
        'ASSIGNED', 'UNASSIGNED', 'DUE_DATE_CHANGED',
        'TITLE_CHANGED', 'DESCRIPTION_CHANGED', 'DELETED'))
);

CREATE INDEX idx_tasks_chat_id ON tasks(chat_id);
CREATE INDEX idx_tasks_assigned_to ON tasks(assigned_to);
CREATE INDEX idx_task_labels_task ON task_labels(task_id);
CREATE INDEX idx_task_activity_task_id ON task_activity_log(task_id);
