ALTER TABLE tasks
ADD CONSTRAINT tasks_estimated_duration_positive_check
CHECK (estimated_duration_minutes > 0);

