package fos

default allow = false

# Allow when actor has a recognized role and resource is ACTIVE
allow {
    input.actor.role != ""
    input.resource.state == "ACTIVE"
}

# Allow DRAFT resource operations for CLUB_ADMIN and OPERATOR
allow {
    input.resource.state == "DRAFT"
    role := input.actor.role
    role_can_access_draft[role]
}

role_can_access_draft := {"CLUB_ADMIN", "OPERATOR", "HEAD_COACH"}

# Deny all access to ARCHIVED resources
deny {
    input.resource.state == "ARCHIVED"
}
