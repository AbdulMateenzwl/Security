package com.security.project.domain.task.entity;

/** The kind of change recorded in a task's activity log. */
public enum TaskAction {
    CREATED,
    STATUS_CHANGED,
    PRIORITY_CHANGED,
    ASSIGNED,
    UNASSIGNED,
    DUE_DATE_CHANGED,
    TITLE_CHANGED,
    DESCRIPTION_CHANGED,
    DELETED
}
