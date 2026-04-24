package fos

resource_not_archived {
    input.resource.state != "ARCHIVED"
}

# Workspace document policies

allow {
    input.resource.action == "workspace.document.general.upload"
    resource_not_archived
    coaching_staff_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.general.read"
    resource_not_archived
    coaching_staff_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.general.delete"
    resource_not_archived
    coaching_staff_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.general.edit"
    resource_not_archived
    coaching_staff_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.medical.upload"
    resource_not_archived
    medical_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.medical.read"
    resource_not_archived
    medical_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.medical.delete"
    resource_not_archived
    club_admin_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.medical.edit"
    resource_not_archived
    club_admin_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.admin.upload"
    resource_not_archived
    club_admin_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.admin.read"
    resource_not_archived
    club_admin_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.admin.delete"
    resource_not_archived
    club_admin_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.admin.edit"
    resource_not_archived
    club_admin_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.report.upload"
    resource_not_archived
    report_upload_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.report.read"
    resource_not_archived
    coaching_staff_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.report.delete"
    resource_not_archived
    report_upload_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.report.edit"
    resource_not_archived
    report_upload_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.contract.upload"
    resource_not_archived
    club_admin_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.contract.read"
    resource_not_archived
    club_admin_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.contract.delete"
    resource_not_archived
    club_admin_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.contract.edit"
    resource_not_archived
    club_admin_roles[input.actor.role]
}

# Workspace event policies

allow {
    input.resource.action == "workspace.event.create"
    resource_not_archived
    event_manage_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.event.update"
    resource_not_archived
    event_manage_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.event.delete"
    resource_not_archived
    event_manage_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.event.read"
    resource_not_archived
    coaching_staff_roles[input.actor.role]
}

# Workspace profile tab policies

allow {
    input.resource.action == "workspace.profile.tab.documents"
    resource_not_archived
    coaching_staff_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.profile.tab.reports"
    resource_not_archived
    coaching_staff_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.profile.tab.medical"
    resource_not_archived
    medical_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.profile.tab.admin"
    resource_not_archived
    club_admin_roles[input.actor.role]
}

coaching_staff_roles := {
    "ROLE_HEAD_COACH",
    "ROLE_ASSISTANT_COACH",
    "ROLE_GOALKEEPER_COACH",
    "ROLE_PHYSICAL_TRAINER",
    "ROLE_ANALYST",
    "ROLE_CLUB_ADMIN"
}

medical_roles := {
    "ROLE_MEDICAL_STAFF",
    "ROLE_CLUB_ADMIN"
}

report_upload_roles := {
    "ROLE_HEAD_COACH",
    "ROLE_CLUB_ADMIN",
    "ROLE_ANALYST"
}

event_manage_roles := {
    "ROLE_HEAD_COACH",
    "ROLE_CLUB_ADMIN"
}

club_admin_roles := {
    "ROLE_CLUB_ADMIN"
}
