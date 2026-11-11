-- Each released result version is a distinct event. A later amendment remains unread.
CREATE TABLE lab_result_view_state (
    user_id UUID NOT NULL REFERENCES app_user(id),
    result_id UUID NOT NULL REFERENCES lab_result(id),
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, result_id)
);
