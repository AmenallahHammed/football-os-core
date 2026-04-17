package fos

default allow_workspace = false

allow {
    input.resource.action == "workspace.document.general.upload"
    coaching_staff_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.general.read"
    coaching_staff_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.general.delete"
    coaching_staff_roles[input.actor.role]
}

allow {
    startswith(input.resource.action, "workspace.document.medical.")
    medical_roles[input.actor.role]
}

allow {
    startswith(input.resource.action, "workspace.document.admin.")
    input.actor.role == "ROLE_CLUB_ADMIN"
}

allow {
    input.resource.action == "workspace.document.report.read"
    coaching_staff_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.report.upload"
    report_upload_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.report.delete"
    report_upload_roles[input.actor.role]
}

allow {
    startswith(input.resource.action, "workspace.document.contract.")
    input.actor.role == "ROLE_CLUB_ADMIN"
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
