-- Executar uma vez com o usuário administrador do Cloud SQL.
-- A senha vem do Secret Manager (db-auth-password), nunca deste arquivo.
CREATE ROLE auth_ro LOGIN PASSWORD :'senha';

GRANT CONNECT ON DATABASE oficina TO auth_ro;
GRANT USAGE ON SCHEMA public TO auth_ro;
GRANT SELECT (id, nome, cpf, status) ON public.cliente TO auth_ro;

ALTER ROLE auth_ro SET default_transaction_read_only = on;
ALTER ROLE auth_ro CONNECTION LIMIT 10;