-- ============================================================================
-- Role de leitura do servico de autenticacao.
--
-- QUANDO: depois do primeiro deploy da app com a migration V16, que cria as
-- views cliente e funcionario. Antes disso os GRANTs falham com
-- "relation does not exist".
--
-- COMO: pelo workflow .github/workflows/db-grants.yml (Actions -> "Banco -
-- papel auth_ro" -> Run workflow). Ele liga a instancia se preciso, roda este
-- arquivo num pod descartavel do GKE -- a unica rota ate o IP privado do banco
-- -- e devolve a instancia ao estado em que estava.
--
-- O arquivo e idempotente: rodar de novo realinha a senha e os GRANTs, nao
-- falha. E o que permite que o workflow seja repetivel.
--
-- Localmente, pelo tunel do tech-challenge-database, se for preciso depurar:
--
--   # terminal 1 (tech-challenge-database)
--   ./scripts/cloudsql.sh tunnel
--
--   # terminal 2 (este repositorio)
--   PGPASSWORD="$(gcloud secrets versions access latest --secret=tech-challenge-staging-db-password)" \
--   psql "host=localhost port=5433 dbname=oficina user=oficina_app sslmode=require" \
--     -v senha="$(gcloud secrets versions access latest --secret=db-auth-password)" \
--     -f sql/auth_ro.sql
--
-- Em qualquer rota, quem roda e o oficina_app: ele e dono das tabelas e das
-- views e, como membro de cloudsqlsuperuser, pode criar roles. A senha do
-- auth_ro vem do Secret Manager (db-auth-password, criado pelo
-- tech-challenge-database), nunca deste arquivo.
--
-- POR QUE SQL E NAO google_sql_user: usuarios criados pela API do Cloud SQL
-- entram no grupo cloudsqlsuperuser, o que anularia o GRANT por coluna abaixo.
-- ============================================================================

-- CREATE ROLE falharia na segunda execucao; o ALTER logo abaixo e que define a
-- senha, nas duas rotas.
SELECT 'CREATE ROLE auth_ro NOLOGIN'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'auth_ro')
\gexec

ALTER ROLE auth_ro LOGIN PASSWORD :'senha';

GRANT CONNECT ON DATABASE oficina TO auth_ro;
GRANT USAGE ON SCHEMA public TO auth_ro;

-- cliente e funcionario sao VIEWS publicadas pela app (V16) como contrato de
-- leitura deste servico: cliente responde pelo papel CLIENTE, funcionario
-- pelos papeis ATENDENTE, MECANICO e ADMIN. O GRANT e por coluna para que este
-- servico nao alcance e-mail, endereco ou qualquer outro dado que nao
-- participe da emissao do token. O auth_ro nao tem acesso as tabelas
-- clientes e funcionarios.
GRANT SELECT (id, nome, cpf, status) ON public.cliente TO auth_ro;
GRANT SELECT (id, nome, cpf, cargo) ON public.funcionario TO auth_ro;

ALTER ROLE auth_ro SET default_transaction_read_only = on;

-- Casa com --max-instances=3 (cd-homolog.yml / cd-prod.yml) x
-- maximumPoolSize=2 (DataSourceFactory). Somado as 12 conexoes da app,
-- fica dentro das ~22 disponiveis na db-f1-micro.
ALTER ROLE auth_ro CONNECTION LIMIT 6;
