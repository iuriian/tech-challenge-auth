# tech-challenge-auth

## Propósito

Serviço de autenticação dos usuários da oficina. Recebe um CPF, confere o cadastro
no banco e devolve um JWT RS256 assinado, que os demais serviços do sistema validam
**offline** pelo JWKS publicado aqui — sem nenhuma chamada de volta a este serviço a
cada requisição.

Autentica os quatro papéis do sistema, cada um com a sua origem de cadastro:

| Papel no claim `roles` | View | Critério |
| --- | --- | --- |
| `CLIENTE` | `cliente` | `status = ATIVO` |
| `ATENDENTE` | `funcionario` | `cargo = ATENDENTE` |
| `MECANICO` | `funcionario` | `cargo = MECANICO` |
| `ADMIN` | `funcionario` | `cargo = ADMIN` |

`cliente` e `funcionario` são **views** publicadas pela aplicação
(`Tech-Challenge`, migration V16) sobre as tabelas `clientes` e `funcionarios`. Elas
são o contrato de leitura deste serviço: a aplicação pode mudar as tabelas à vontade,
desde que preserve as colunas das views, e este serviço nunca enxerga as tabelas.

O endpoint é um só: quem chama manda apenas o CPF e não escolhe o papel — ele é
consequência de onde o cadastro está. A view `cliente` é consultada primeiro,
então um CPF presente nas duas recebe o papel de menor privilégio; a view
`funcionario` só é lida quando a primeira não produz ninguém apto. Ver
[Fluxo de uma emissão](docs/arquitetura.md#fluxo-de-uma-emissão).

É o único componente do sistema que emite identidade. Os outros serviços não
conhecem CPF, não falam com os cadastros e não guardam segredo de
assinatura: confiam no `iss`/`aud` do token e na chave pública do JWKS.

Não há senha. O CPF é o único fator, por decisão de escopo do desafio — o serviço
prova identidade contra o cadastro, não posse de um segredo. Ver
[Limitações conhecidas](#limitações-conhecidas).

## Tecnologias utilizadas

| Camada | Tecnologia | Versão | Por quê |
| --- | --- | --- | --- |
| Linguagem | Kotlin (JVM) | 2.2.20 | — |
| Runtime | JDK | 21 | Toolchain resolvida pelo Gradle |
| HTTP | Ktor + engine CIO | 3.6.0 | Coroutines puras, sem Netty: menos classes na partida, o que importa com `min-instances=0` |
| Injeção de dependência | Koin | 4.1.1 | Grafo explícito em código, sem reflexão nem processamento de anotação |
| JWT / JWKS | Nimbus JOSE+JWT | 10.5 | RS256 e serialização de JWK Set |
| Banco | PostgreSQL (driver JDBC) | 42.7.8 | Cloud SQL |
| Pool de conexões | HikariCP | 6.3.0 | — |
| JSON | Jackson (módulo Kotlin) | 2.20.0 | — |
| Build | Gradle (Kotlin DSL) + Shadow | 9.3.0 / 9.2.2 | Fat JAR com o servidor embutido |
| Testes | JUnit 5 + Ktor test host | 5.13.4 | — |
| Teste de arquitetura | Konsist | 0.17.3 | As regras de camada como teste executável |
| Cobertura | Jacoco | 0.8.13 | Mínimo de 90% de instruções e 80% de branches |
| Análise estática | detekt | 1.23.8 | — |
| Formatação | Spotless + ktlint | 7.0.4 / 1.6.0 | Dono único do estilo; regras vêm do `.editorconfig` |
| Imagem | `gcr.io/distroless/java21-debian12:nonroot` | — | Sem shell nem gerenciador de pacotes |
| Infraestrutura | Terraform | >= 1.6 | Estado em GCS |
| Nuvem | Cloud Run (Direct VPC egress), API Gateway, Artifact Registry, Secret Manager | — | Sem conector de VPC: nenhum custo fixo de rede |
| CI/CD | GitHub Actions | — | Um workflow por responsabilidade |

## Arquitetura

Arquitetura hexagonal em quatro camadas, com as dependências sempre apontando para o
domínio, servida no Cloud Run atrás de um API Gateway. As regras de camada são teste
executável (Konsist), não convenção.

![Camadas da aplicação: presentation (Servidor Ktor, AuthRoutes), application (AutenticarClienteUseCase e as portas EmissorDeToken e ChavesPublicas), domain (CPF, Cliente, StatusCliente e a porta ClienteRepository) e infrastructure (JdbcClienteRepository, NimbusTokenAdapter, configuração e módulos Koin). As setas cheias apontam sempre para dentro; as pontilhadas marcam os adaptadores implementando as portas das camadas internas.](docs/arquitetura-camadas.svg)

As setas pontilhadas são as inversões: `JdbcClienteRepository` e
`NimbusTokenAdapter` vivem na infraestrutura mas implementam contratos declarados
nas camadas de dentro. Trocar Postgres por outro banco, ou Nimbus por outra
biblioteca de JOSE, não toca domínio nem aplicação.

Os demais diagramas estão em **[docs/arquitetura.md](docs/arquitetura.md)**:

- [Runtime no GCP](docs/arquitetura.md#runtime-no-gcp) — gateway, Cloud Run, Secret
  Manager, Direct VPC egress e Cloud SQL
- [Fluxo de uma emissão](docs/arquitetura.md#fluxo-de-uma-emissão) — o caminho de um
  `POST /auth/token`
- [Fluxo de entrega](docs/arquitetura.md#fluxo-de-entrega) — o gitflow e a promoção
  por digest

## API — Swagger e Postman

O contrato é descrito em **[`terraform/openapi.yaml.tftpl`](terraform/openapi.yaml.tftpl)**,
que é a fonte única: o mesmo arquivo é o que o Terraform publica como configuração do
API Gateway, então o que está documentado é literalmente o que o gateway aceita.

Para visualizar renderizado, abra no Swagger Editor:

**[Abrir no Swagger Editor](https://editor.swagger.io/?url=https://raw.githubusercontent.com/iuriian/tech-challenge-auth/main/terraform/openapi.yaml.tftpl)**

> O link resolve pelo arquivo em `main`, então só funciona depois que a feature é
> mesclada. Antes disso, use **File → Import file** no
> [editor.swagger.io](https://editor.swagger.io/) apontando para a cópia local, ou
> suba um Swagger UI contra o arquivo do working tree:
>
> ```bash
> docker run --rm -p 8090:8080 \
>   -e SWAGGER_JSON=/spec/openapi.yaml.tftpl \
>   -v "$PWD/terraform:/spec:ro" swaggerapi/swagger-ui
> ```

O campo `x-google-backend.address` aparece como `${service_url}` — é o placeholder
que o `templatefile` do Terraform substitui pela URL real do Cloud Run no `apply`.
Isso não afeta a renderização dos endpoints.

### Endpoints

| Método | Caminho | Resposta |
| --- | --- | --- |
| `POST` | `/auth/token` | `200` com o token, `400` CPF inválido, `401` sem cadastro apto, `503` falha interna ou banco desligado |
| `GET` | `/.well-known/jwks.json` | `200` com o JWK Set (`public, max-age=300`) |
| `GET` | `/.well-known/openid-configuration` | `200` com os metadados do emissor (`public, max-age=3600`) |

```bash
curl -X POST "$ISSUER/auth/token" \
  -H 'Content-Type: application/json' \
  -d '{"cpf":"527.044.726-61"}'
```

```json
{
  "access_token": "eyJraWQiOiI...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

O CPF é aceito com ou sem máscara. Os erros seguem o formato do OAuth 2.0
(`invalid_request`, `invalid_grant`, `temporarily_unavailable`):

```json
{ "error": "invalid_request", "error_description": "CPF inválido" }
```

Claims do token: `iss`, `sub` (o `id` do cadastro), `aud`, `iat`, `nbf`,
`exp`, `jti`, mais `name` e `roles`. O header leva `kid` — o thumbprint da chave —
para que o consumidor escolha a chave certa no JWKS.

O `roles` é sempre uma lista de **um** elemento, com um dos quatro papéis da tabela
acima — `["CLIENTE"]`, `["ATENDENTE"]`, `["MECANICO"]` ou `["ADMIN"]`. A lista
existe porque é o formato que o `JwtGrantedAuthoritiesConverter` do consumidor
espera, não porque um token acumule papéis.

### Coleção do Postman

**[docs/oficina-auth.postman_collection.json](docs/oficina-auth.postman_collection.json)**

Importe no Postman e ajuste a variável `issuer` — `http://localhost:8081` para a
execução local, ou o output `issuer` do Terraform para homologação e produção.

Os CPFs das variáveis são os dos dados da aplicação, então a mesma coleção serve
para os dois ambientes:

| Variável | CPF | Cadastro | Resposta |
| --- | --- | --- | --- |
| `cpf_cliente` | 527.044.726-61 | Carlos Pereira (cliente) | `200`, `["CLIENTE"]` |
| `cpf_atendente` | 529.982.247-25 | João Silva (funcionário) | `200`, `["ATENDENTE"]` |
| `cpf_mecanico` | 286.255.878-87 | Maria Souza (funcionário) | `200`, `["MECANICO"]` |
| `cpf_sem_cadastro` | 123.456.789-09 | nenhum | `401` |

Não há requisição de `ADMIN` porque nenhum cadastro pode ter esse cargo hoje — ver
[Limitações conhecidas](#limitações-conhecidas).

## Execução local

Requer JDK 21 (o Gradle resolve o toolchain sozinho), Docker e OpenSSL.

### 1. Banco

Escolha uma das duas opções.

**Opção A — banco da aplicação (recomendada).** Usa os mesmos dados e as mesmas
views do staging. No repositório `Tech-Challenge`:

```bash
docker compose up -d db
./gradlew bootRun          # uma vez: o Flyway cria as tabelas, as views e os seeds
```

Depois de a aplicação subir pela primeira vez, ela pode ser parada; o banco
continua em `localhost:5432` com usuário `user` e senha `password`.

**Opção B — banco isolado.** Útil para rodar o auth sem o repositório da aplicação.
Sobe na porta **5433** com outro nome de container, para não conflitar com o
`oficina-db` da aplicação:

```bash
docker run -d --name oficina-auth-db -p 5433:5432 \
  -e POSTGRES_PASSWORD=local -e POSTGRES_DB=oficina postgres:16
```

```bash
docker exec -i oficina-auth-db psql -U postgres -d oficina <<'SQL'
-- Mesmo formato das views publicadas pela aplicação (V16).
CREATE TABLE cliente (
  id     uuid PRIMARY KEY,
  nome   text NOT NULL,
  cpf    text NOT NULL,
  status text NOT NULL
);
CREATE TABLE funcionario (
  id    uuid PRIMARY KEY,
  nome  text NOT NULL,
  cpf   text NOT NULL,
  cargo text NOT NULL
);
-- Mesmos CPFs dos dados da aplicação, para que a coleção do Postman
-- funcione igual nos dois bancos.
INSERT INTO cliente VALUES
  (gen_random_uuid(), 'Carlos Pereira', '52704472661', 'ATIVO'),
  (gen_random_uuid(), 'Maria Oliveira', '11144477735', 'ATIVO'),
  (gen_random_uuid(), 'João da Silva',  '39053344705', 'ATIVO');
INSERT INTO funcionario VALUES
  (gen_random_uuid(), 'João Silva',  '52998224725', 'ATENDENTE'),
  (gen_random_uuid(), 'Maria Souza', '28625587887', 'MECANICO'),
  (gen_random_uuid(), 'Carlos Lima', '45317828791', 'ATENDENTE');
SQL
```

O `SELECT` casa o CPF com e sem máscara (`WHERE cpf IN (?, ?)`), então qualquer um
dos dois formatos serve na coluna. Os dois cadastros são lidos com quatro colunas
nomeadas, nunca `SELECT *` — é exatamente o que o `GRANT` por coluna do `auth_ro`
concede, e o teste de cada repositório trava isso.

### 2. Chave de assinatura

PKCS#8, no mínimo 2048 bits — é o que o `RsaKeyLoader` exige:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
```

### 3. Subir

```bash
export ISSUER=http://localhost:8081
export JWT_PRIVATE_KEY="$(cat jwt-private.pem)"

# Opção A (banco da aplicação)
export DB_URL=jdbc:postgresql://localhost:5432/oficina
export DB_USER=user
export DB_PASSWORD=password

# Opção B (banco isolado)
# export DB_URL=jdbc:postgresql://localhost:5433/oficina
# export DB_USER=postgres
# export DB_PASSWORD=local

./gradlew run
```

`./gradlew run` escuta na **8081** de propósito: é o `AUTH_ISSUER_URI` padrão da
aplicação, então a aplicação local valida os tokens deste serviço sem configuração
extra. Para validar:

```bash
curl -s localhost:8081/.well-known/jwks.json | jq '.keys[0].kid'
curl -s -X POST localhost:8081/auth/token \
  -H 'Content-Type: application/json' -d '{"cpf":"527.044.726-61"}' | jq
```

Para ver qual papel saiu no token, trocando o CPF pelos da tabela do Postman:

```bash
curl -s -X POST localhost:8081/auth/token \
  -H 'Content-Type: application/json' -d '{"cpf":"529.982.247-25"}' |
  jq -r .access_token | cut -d. -f2 | base64 -d | jq '{sub, name, roles}'
```

```json
{ "sub": "3f5f33b0-...", "name": "João Silva", "roles": ["ATENDENTE"] }
```

### Rodar como container

```bash
./gradlew shadowJar
docker build -t oficina-auth:local .
docker run --rm -p 8081:8080 \
  -e ISSUER=http://localhost:8081 \
  -e JWT_PRIVATE_KEY="$(cat jwt-private.pem)" \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/oficina \
  -e DB_USER=user -e DB_PASSWORD=password \
  oficina-auth:local
```

### Variáveis de ambiente

| Variável | Obrigatória | Padrão | Observação |
| --- | --- | --- | --- |
| `ISSUER` | sim | — | URL do emissor; precisa começar com `http`. A barra final é descartada |
| `JWT_PRIVATE_KEY` | sim | — | PEM PKCS#8. Aceita `\n` escapado, para caber em variável de ambiente |
| `DB_URL` | sim | — | JDBC do Postgres. No Cloud SQL, com `?sslmode=require` |
| `DB_USER` | sim | — | `auth_ro` em homologação e produção |
| `DB_PASSWORD` | sim | — | vem do Secret Manager (`db-auth-password`) |
| `AUDIENCE` | não | `oficina` | claim `aud` |
| `TOKEN_TTL_SECONDS` | não | `3600` | precisa ser positivo |
| `PORT` | não | `8080` | injetada pelo Cloud Run; valor inválido cai no padrão em vez de derrubar a instância |

Ausência de uma obrigatória derruba a aplicação na partida, não na primeira
requisição. `TokenSettings` e `DatabaseSettings` sobrescrevem `toString()` para que
chave e senha nunca apareçam em log.

## Deploy

### 1. Dependências entre repositórios

Este serviço depende de recursos criados em outros três lugares. Aplique na ordem:

| Ordem | Repositório | Entrega para este serviço |
| --- | --- | --- |
| 1 | `Tech-Challenge` (`terraform/`) | VPC `default` e SA `github-actions-cd`, lidas como `data` |
| 2 | `tech-challenge-database` | Cloud SQL com IP privado, peering e o secret `db-auth-password`, lido como `data` |
| 3 | **este repositório** | Cloud Run, API Gateway e o output `issuer` |
| 4 | `Tech-Challenge` (app) | as views `cliente` e `funcionario` (migration V16) |

O passo 4 vem depois deste porque a aplicação precisa do `issuer` para validar os
tokens, e este serviço precisa das views para emitir. Por isso o deploy da imagem
(seção 3) só acontece depois do passo 4.

### 2. Infraestrutura (uma vez por ambiente)

```bash
cd terraform
cp terraform.tfvars.example terraform.tfvars   # preencha project_id e ci_service_account_id
terraform init
terraform apply
terraform output issuer                        # anote: vai para este repo e para a aplicação
```

O Terraform cria o serviço do Cloud Run uma única vez, só para que o endereço exista
e possa ser apontado no backend do gateway; todo o runtime (imagem, variáveis,
secrets, rede, escala) pertence ao deploy no GitHub Actions, e por isso o `template`
está inteiro em `ignore_changes`. Essa divisão quebra o ciclo
`ISSUER → hostname do gateway → backend → URL do serviço`, que impediria descrever
tudo num `apply` só. A primeira revisão sobe com a imagem de bootstrap até o
primeiro deploy do pipeline.

### 3. Passos manuais depois do apply

Sem estes três, o primeiro deploy sobe e morre na partida, ou responde `503`.

**Chave de assinatura.** O `apply` cria o secret `jwt-signing-key` **vazio**. Sem
uma versão com o PEM, a leitura da configuração falha:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
gcloud secrets versions add jwt-signing-key --data-file=jwt-private.pem
rm jwt-private.pem
```

**Papel de leitura no banco.** Depois do deploy da aplicação com a migration V16
(as views precisam existir), com o usuário `oficina_app`. O banco só tem IP privado,
então a conexão passa pelo túnel do `tech-challenge-database`:

```bash
# terminal 1 (tech-challenge-database)
./scripts/cloudsql.sh tunnel

# terminal 2 (este repositório)
PGPASSWORD="$(gcloud secrets versions access latest --secret=tech-challenge-staging-db-password)" \
psql "host=localhost port=5433 dbname=oficina user=oficina_app sslmode=require" \
  -v senha="$(gcloud secrets versions access latest --secret=db-auth-password)" \
  -f sql/auth_ro.sql
```

**Environments `staging` e `production` no GitHub:**

| Tipo | Nome | Valor |
| --- | --- | --- |
| variable | `GCP_PROJECT_ID` | ID do projeto |
| variable | `REGION` | `us-central1` |
| variable | `AUTH_ISSUER` | output `issuer`, sem barra final |
| variable | `DB_URL` | output `auth_db_url` do `tech-challenge-database` |
| secret | `GCP_SA_KEY` | chave da SA `github-actions-cd` |

A aplicação precisa da variable `AUTH_ISSUER_URI` com **exatamente** o mesmo valor de
`AUTH_ISSUER`: o Spring compara o `iss` do token caractere por caractere.

### 4. Pipeline

Gitflow, com um workflow por responsabilidade — ver o
[fluxo de entrega](docs/arquitetura.md#fluxo-de-entrega).

| Workflow | Dispara em | Faz |
| --- | --- | --- |
| `ci.yml` | PR para `develop` ou `main`; `workflow_call` | Um job por checagem: formatação, análise estática, testes com cobertura, fat JAR |
| `cd-homolog.yml` | push em `release/**`; manual | Chama o CI, constrói e publica a imagem, faz deploy em homologação, roda smoke test |
| `cd-prod.yml` | push em `main`; manual | Resolve o digest da revisão no ar em homologação, promove **essa** imagem para produção, roda smoke test |

Produção nunca constrói imagem. O `cd-prod.yml` lê o digest do que está servindo em
homologação e faz deploy dele, então o artefato aprovado é bit a bit o artefato
promovido.

O deploy configura a rede com **Direct VPC egress** (`--network=default
--subnet=default --vpc-egress=private-ranges-only`) e limita a escala a
`--max-instances=3`, o que dá no máximo 6 conexões no banco (3 × pool de 2) — o
mesmo valor do `CONNECTION LIMIT` do `auth_ro`. Se o deploy falhar por permissão
na subnet, conceda `roles/compute.networkUser` na subnet `default` à SA do CI.

### Rollback

As dez imagens mais recentes ficam retidas no Artifact Registry (política de
`cleanup` em `terraform/registry.tf`), então basta apontar a revisão anterior:

```bash
gcloud run deploy oficina-auth --region=$REGION --image=$REPO@$DIGEST_ANTERIOR
```

## Qualidade

```bash
./gradlew check        # formatação + análise estática + testes + cobertura
./gradlew qualidade    # só Spotless e detekt
./gradlew test         # testes, já gerando o relatório do Jacoco
```

- **Spotless/ktlint** é o único formatador; o estilo vem exclusivamente do
  `.editorconfig`. O ruleset de formatação do detekt fica desligado para não haver
  dois donos do mesmo arquivo.
- **detekt** com baseline em `config/detekt/detekt-baseline.xml`.
- **Jacoco** com mínimo de 90% de instruções e 80% de branches, verificado no
  `check`. O relatório sai em `build/reports/jacoco/test/` e o CI o publica como
  artefato em toda execução. O `main()` do `Servidor.kt` está fora da medição: sobe
  um servidor de verdade e nenhum teste o executa sem subir o processo.

## Limitações conhecidas

- **CPF como único fator.** Quem souber um CPF válido de cliente ativo obtém token.
  O endpoint é público no gateway e não tem rate limit — uma quota no API Gateway é o
  próximo passo natural.
- **Sem rotação de chave.** O JWKS publica uma única chave. Rotacionar hoje invalida
  os tokens em circulação, porque não há período de sobreposição com duas chaves.
- **Banco desligado fora de uso.** Por custo, a instância Cloud SQL fica desligada
  quando o ambiente não está em uso, e o `cloudsql-power.yml` do
  `tech-challenge-database` a desliga todas as noites. Nesse estado o
  `POST /auth/token` responde `503`; o JWKS e o discovery continuam no ar.
- **Repositórios JDBC sem teste de integração.** `JdbcClienteRepository` e
  `JdbcFuncionarioRepository` são testados com fakes de JDBC, então o SQL não é
  executado contra um Postgres de verdade; um erro de coluna ou tipo só apareceria
  em runtime. O smoke test do CD cobre o JWKS e o `400`, não o caminho de sucesso.
- **Status de cliente fixo.** A tabela `clientes` da aplicação não tem coluna de
  status, então a view `cliente` publica todo cliente pessoa física como `ATIVO`. O
  caminho de cliente inativo está implementado e testado aqui, mas só passa a ser
  exercitado quando a aplicação ganhar inativação.
- **`ADMIN` depende da aplicação.** A tabela `funcionarios` aceita apenas
  `CHECK (cargo IN ('ATENDENTE', 'MECANICO'))`, e o enum de cargo da aplicação também
  só conhece esses dois. Este serviço já traduz `ADMIN`, mas nenhum cadastro pode
  receber esse cargo enquanto a constraint e o enum não forem ampliados lá.
- **Um papel por token.** `roles` sai sempre com um elemento. Quem é cliente e
  funcionário com o mesmo CPF nunca obtém token de funcionário, porque `cliente` é
  consultada primeiro. Emitir os dois papéis juntos exigiria decidir qual `id` vai
  no `sub`.
