-- Enterprise KYC documents. Files live in the private Supabase Storage bucket 'kyc-docs' under the
-- driver's own folder (RLS-enforced); we store only the storage path + review state here.
-- Per-doc status lets an admin request re-upload of a single document instead of rejecting the whole KYC.
CREATE TABLE kyc_documents (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    doc_type      text   NOT NULL,   -- DL_FRONT, DL_BACK, AADHAAR_FRONT, AADHAAR_BACK, RC_FRONT, RC_BACK, PERMIT, INSURANCE, FITNESS
    storage_path  text   NOT NULL,   -- object key inside the kyc-docs bucket
    status        text   NOT NULL DEFAULT 'UPLOADED',  -- UPLOADED / REUPLOAD_REQUESTED
    reject_reason text,
    uploaded_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, doc_type)
);
CREATE INDEX idx_kyc_documents_user ON kyc_documents (user_id);
