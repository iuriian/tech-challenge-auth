-- Executar uma vez com o usuário administrador do Cloud SQL.
-- A senha vem do Secret Manager (db-auth-password), nunca deste arquivo.
CREATE ROLE auth_ro LOGIN PASSWORD :'senha';

GRANT CONNECT ON DATABASE oficina TO auth_ro;
GRANT USAGE ON SCHEMA public TO auth_ro;

-- Uma tabela por tipo de identidade: cliente responde pelo papel CLIENTE,
-- funcionario pelos papéis ATENDENTE, MECANICO e ADMIN. O GRANT é por coluna
-- justamente para que este serviço não alcance e-mail, endereço ou qualquer
-- outro dado que não participe da emissão do token.
GRANT SELECT (id, nome, cpf, status) ON public.cliente TO auth_ro;
GRANT SELECT (id, nome, cpf, cargo) ON public.funcionario TO auth_ro;

ALTER ROLE auth_ro SET default_transaction_read_only = on;
ALTER ROLE auth_ro CONNECTION LIMIT 10;
