DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_name='category_feedback'
      AND column_name='confidence'
  ) THEN
    BEGIN
      ALTER TABLE category_feedback
        ALTER COLUMN confidence TYPE DOUBLE PRECISION
        USING confidence::double precision;
    EXCEPTION WHEN others THEN
      NULL;
    END;
  END IF;
END $$;
