CREATE TABLE auth_user (
    username VARCHAR(80) PRIMARY KEY,
    password_hash VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE FUNCTION set_auth_user_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END;
$$;
CREATE TRIGGER trg_auth_user_updated_at BEFORE UPDATE ON auth_user
FOR EACH ROW EXECUTE FUNCTION set_auth_user_updated_at();
