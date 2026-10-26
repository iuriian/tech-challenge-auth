# tech-challenge-auth

## Propósito

Serviço de autenticação dos clientes da oficina. Recebe um CPF, confere o cadastro
no banco e devolve um JWT RS256 assinado, que os demais serviços do sistema validam
**offline** pelo JWKS publicado aqui — sem nenhuma chamada de volta a este serviço a
cada requisição.

É o único componente do sistema que emite identidade. Os outros serviços não
conhecem CPF, não falam com a tabela de clientes e não guardam segredo de
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
| Nuvem | Cloud Run, API Gateway, Artifact Registry, Secret Manager, VPC connector | — | — |
| CI/CD | GitHub Actions | — | Um workflow por responsabilidade |

## Arquitetura

Arquitetura hexagonal em quatro camadas, com as dependências sempre apontando para o
domínio, servida no Cloud Run atrás de um API Gateway. As regras de camada são teste
executável (Konsist), não convenção.

Diagramas em **[docs/arquitetura.md](docs/arquitetura.md)**:

- [Camadas (hexagonal)](docs/arquitetura.md#camadas-hexagonal) — os pacotes, as
  portas e onde estão as inversões de dependência
- [Runtime no GCP](docs/arquitetura.md#runtime-no-gcp) — gateway, Cloud Run, Secret
  Manager, VPC connector e Cloud SQL
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
| `POST` | `/auth/token` | `200` com o token, `400` CPF inválido, `401` cliente inexistente ou inativo, `503` falha interna |
| `GET` | `/.well-known/jwks.json` | `200` com o JWK Set (`public, max-age=300`) |
| `GET` | `/.well-known/openid-configuration` | `200` com os metadados do emissor (`public, max-age=3600`) |

```bash
curl -X POST "$ISSUER/auth/token" \
  -H 'Content-Type: application/json' \
  -d '{"cpf":"529.982.247-25"}'
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

Claims do token: `iss`, `sub` (o `id` do cliente), `aud`, `iat`, `nbf`, `exp`,
`jti`, mais `name` e `roles: ["CLIENTE"]`. O header leva `kid` — o thumbprint da
chave — para que o consumidor escolha a chave certa no JWKS.

### Coleção do Postman

**[docs/oficina-auth.postman_collection.json](docs/oficina-auth.postman_collection.json)**

Importe no Postman e ajuste a variável `issuer` — `http://localhost:8081` para a
execução local, ou o output `issuer` do Terraform para homologação e produção. A
coleção cobre os três resultados do endpoint de token (`200`, `400` e `401`) mais o
JWKS e o discovery.

## Execução local

Requer JDK 21 (o Gradle resolve o toolchain sozinho), Docker e OpenSSL.

### 1. Banco

```bash
docker run -d --name oficina-db -p 5432:5432 \
  -e POSTGRES_PASSWORD=local -e POSTGRES_DB=oficina postgres:16
```

```bash
docker exec -i oficina-db psql -U postgres -d oficina <<'SQL'
CREATE TABLE cliente (
  id     uuid PRIMARY KEY,
  nome   text NOT NULL,
  cpf    text NOT NULL,
  status text NOT NULL
);
INSERT INTO cliente VALUES
  (gen_random_uuid(), 'Cliente Ativo',   '52998224725', 'ATIVO'),
  (gen_random_uuid(), 'Cliente Inativo', '11144477735', 'INATIVO');
SQL
```

O `SELECT` casa o CPF com e sem máscara (`WHERE cpf IN (?, ?)`), então qualquer um
dos dois formatos serve na coluna.

### 2. Chave de assinatura

PKCS#8, no mínimo 2048 bits — é o que o `RsaKeyLoader` exige:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
```

### 3. Subir

```bash
export ISSUER=http://localhost:8081
export JWT_PRIVATE_KEY="$(cat jwt-private.pem)"
export DB_URL=jdbc:postgresql://localhost:5432/oficina
export DB_USER=postgres
export DB_PASSWORD=local

./gradlew run
```

`./gradlew run` escuta na **8081** de propósito, deixando a 8080 livre para o resto
do stack na máquina. Para validar:

```bash
curl -s localhost:8081/.well-known/jwks.json | jq '.keys[0].kid'
curl -s -X POST localhost:8081/auth/token \
  -H 'Content-Type: application/json' -d '{"cpf":"529.982.247-25"}' | jq
```

### Rodar como container

```bash
./gradlew shadowJar
docker build -t oficina-auth:local .
docker run --rm -p 8080:8080 \
  -e ISSUER=http://localhost:8080 \
  -e JWT_PRIVATE_KEY="$(cat jwt-private.pem)" \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/oficina \
  -e DB_USER=postgres -e DB_PASSWORD=local \
  oficina-auth:local
```

### Variáveis de ambiente

| Variável | Obrigatória | Padrão | Observação |
| --- | --- | --- | --- |
| `ISSUER` | sim | — | URL do emissor; precisa começar com `http`. A barra final é descartada |
| `JWT_PRIVATE_KEY` | sim | — | PEM PKCS#8. Aceita `\n` escapado, para caber em variável de ambiente |
| `DB_URL` | sim | — | JDBC do Postgres |
| `DB_USER` | sim | — | `auth_ro` em produção |
| `DB_PASSWORD` | sim | — | vem do Secret Manager |
| `AUDIENCE` | não | `oficina` | claim `aud` |
| `TOKEN_TTL_SECONDS` | não | `3600` | precisa ser positivo |
| `PORT` | não | `8080` | injetada pelo Cloud Run; valor inválido cai no padrão em vez de derrubar a instância |

Ausência de uma obrigatória derruba a aplicação na partida, não na primeira
requisição. `TokenSettings` e `DatabaseSettings` sobrescrevem `toString()` para que
chave e senha nunca apareçam em log.

## Deploy

### 1. Infraestrutura (uma vez por ambiente)

A VPC e o secret da senha do banco **não** nascem aqui — vêm do
`tech-challenge-infra-database` e são lidos como `data`. **Aplique aquele primeiro.**

```bash
cd terraform
cp terraform.tfvars.example terraform.tfvars   # preencha project_id e ci_service_account_id
terraform init
terraform apply
```

O Terraform cria o serviço do Cloud Run uma única vez, só para que o endereço exista
e possa ser apontado no backend do gateway; todo o runtime (imagem, variáveis,
secrets, escala) pertence ao deploy no GitHub Actions, e por isso o `template` está
inteiro em `ignore_changes`. Essa divisão quebra o ciclo
`ISSUER → hostname do gateway → backend → URL do serviço`, que impediria descrever
tudo num `apply` só. A primeira revisão sobe com a imagem de bootstrap até o
primeiro deploy do pipeline.

### 2. Passos manuais depois do apply

Sem estes três, o primeiro deploy sobe e morre na partida.

O `apply` cria o secret `jwt-signing-key` **vazio**. Sem uma versão com o PEM, a
leitura da configuração falha:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
gcloud secrets versions add jwt-signing-key --data-file=jwt-private.pem
rm jwt-private.pem
```

O papel de leitura no banco, com o usuário administrador do Cloud SQL — a senha vem
do Secret Manager, nunca do arquivo:

```bash
psql -v senha="$(gcloud secrets versions access latest --secret=db-auth-password)" \
  -f sql/auth_ro.sql
```

E os environments `staging` e `production` no GitHub, com as variáveis
`GCP_PROJECT_ID`, `REGION`, `AUTH_ISSUER` (o output `issuer`) e `DB_URL`, mais o
secret `GCP_SA_KEY`.

### 3. Pipeline

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
- **`JdbcClienteRepository` sem teste de integração.** Os testes usam fakes de JDBC,
  então o SQL não é executado contra um Postgres de verdade; um erro de coluna ou
  tipo só apareceria em runtime. O smoke test do CD cobre o JWKS e o `400`, não o
  caminho de sucesso.
