CREATE TABLE role_permission (
    role_id         uuid NOT NULL,
    permission_id   uuid NOT NULL,
    CONSTRAINT role_permission_pkey PRIMARY KEY (role_id, permission_id),
    CONSTRAINT role_permission_role_id_fkey FOREIGN KEY (role_id)
        REFERENCES role (id) ON DELETE CASCADE,
    CONSTRAINT role_permission_permission_id_fkey FOREIGN KEY (permission_id)
        REFERENCES permission (id) ON DELETE CASCADE
);
