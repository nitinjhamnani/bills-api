CREATE TABLE tenant.role_permission (
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL
);



ALTER TABLE ONLY tenant.role_permission
    ADD CONSTRAINT role_permission_pkey PRIMARY KEY (role_id, permission_id);



ALTER TABLE ONLY tenant.role_permission
    ADD CONSTRAINT role_permission_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES tenant.permission(id) ON DELETE CASCADE;



ALTER TABLE ONLY tenant.role_permission
    ADD CONSTRAINT role_permission_role_id_fkey FOREIGN KEY (role_id) REFERENCES tenant.role(id) ON DELETE CASCADE;




