CREATE TABLE item (
    id          uuid         PRIMARY KEY,
    owner_id    uuid         NOT NULL,
    title       varchar(200) NOT NULL,
    created_at  timestamptz  NOT NULL,
    announced   boolean      NOT NULL DEFAULT false
);
CREATE INDEX item_owner_id_idx ON item (owner_id);
