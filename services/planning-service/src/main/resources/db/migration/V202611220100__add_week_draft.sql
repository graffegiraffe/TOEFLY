-- Where the tasks of a week came from (docs/06-ai-agent.md §5.2, docs/12 phase 5c decision 1): the rules, a valid draft
-- of WeekGenerator or its fallback; the weekly review it follows, the focus subcategories and the AI stamp (§2.9).
ALTER TABLE week
    ADD COLUMN draft_source varchar(12) NOT NULL DEFAULT 'RULES',
    ADD COLUMN review_id    uuid,
    ADD COLUMN focus        jsonb       NOT NULL DEFAULT '[]',
    ADD COLUMN ai_stamp     jsonb;
