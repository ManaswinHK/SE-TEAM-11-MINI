-- AES-256-GCM ciphertext is longer than the plain value (AAS-SR-003), so the encrypted columns are widened.
ALTER TABLE employee ALTER COLUMN email SET DATA TYPE VARCHAR(400);
ALTER TABLE employee ALTER COLUMN gender SET DATA TYPE VARCHAR(100);
ALTER TABLE survey_response ALTER COLUMN comments SET DATA TYPE VARCHAR(3000);
