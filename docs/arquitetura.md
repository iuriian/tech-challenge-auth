# Arquitetura — tech-challenge-auth

Diagramas do serviço de autenticação. Para o propósito, a stack e os passos de
execução e deploy, ver o [README](../README.md).

Os diagramas são mermaid, renderizados pelo próprio GitHub. A fonte fica neste
arquivo para que uma mudança de arquitetura apareça como diff revisável, não como
binário trocado.

## Camadas (hexagonal)

Quatro camadas, com as dependências sempre apontando para o domínio. As regras não
são convenção: estão expressas como teste executável em
`src/test/kotlin/.../architecture/ArquiteturaTest.kt` (Konsist) — o domínio não
conhece nenhuma outra camada nem framework algum, as portas da aplicação não expõem
detalhes técnicos, e a infraestrutura não depende da apresentação.

```mermaid
flowchart TB
    subgraph P["presentation"]
        SV["Servidor<br/>Ktor CIO"]
        AR["AuthRoutes"]
    end
    subgraph A["application"]
        UC["AutenticarClienteUseCase"]
        PE["porta<br/>EmissorDeToken"]
        PC["porta<br/>ChavesPublicas"]
    end
    subgraph D["domain"]
        CPF["CPF"]
        CLI["Cliente<br/>StatusCliente"]
        PR["porta<br/>ClienteRepository"]
    end
    subgraph I["infrastructure"]
        JR["JdbcClienteRepository"]
        NT["NimbusTokenAdapter"]
        CF["TokenSettings<br/>DatabaseSettings"]
        DI["Modules (Koin)"]
    end

    SV --> AR
    AR --> UC
    AR --> PC
    UC --> PR
    UC --> PE
    UC --> CPF
    UC --> CLI
    JR -.implementa.-> PR
    NT -.implementa.-> PE
    NT -.implementa.-> PC
    DI -.monta o grafo.-> AR

    classDef dom fill:#1f6f43,stroke:#0d3b24,color:#fff
    classDef app fill:#1f4e79,stroke:#102a43,color:#fff
    classDef inf fill:#6b4c9a,stroke:#3b2a57,color:#fff
    classDef pre fill:#8a5a1b,stroke:#4d3110,color:#fff
    class CPF,CLI,PR dom
    class UC,PE,PC app
    class JR,NT,CF,DI inf
    class SV,AR pre
```

As setas pontilhadas são as inversões: `JdbcClienteRepository` e
`NimbusTokenAdapter` vivem na infraestrutura mas implementam contratos declarados
nas camadas de dentro. Trocar Postgres por outro banco, ou Nimbus por outra
biblioteca de JOSE, não toca domínio nem aplicação.

## Runtime no GCP

```mermaid
flowchart LR
    CONS["Serviços consumidores<br/>(app no GKE)"]
    GW["API Gateway<br/>oficina-auth-gw"]
    CR["Cloud Run<br/>oficina-auth<br/>SA auth-fn"]
    SM["Secret Manager<br/>jwt-signing-key<br/>db-auth-password"]
    VPC["VPC connector<br/>auth-connector"]
    DB[("Cloud SQL<br/>papel auth_ro")]
    AR["Artifact Registry<br/>oficina-auth"]

    CONS -->|"POST /auth/token"| GW
    GW -->|"IAM: SA auth-gateway<br/>roles/run.invoker"| CR
    CR -->|"monta como env var"| SM
    CR -->|"egress private-ranges-only"| VPC
    VPC -->|"SELECT, só leitura"| DB
    AR -.->|"imagem da revisão"| CR
    CONS -.->|"GET /.well-known/jwks.json<br/>valida o token offline"| GW
```

O Cloud Run roda com `--no-allow-unauthenticated`: o único chamador autorizado é a
service account do gateway. O acesso ao banco é só leitura em três níveis — o papel
`auth_ro` tem `SELECT` em quatro colunas e `default_transaction_read_only`, e o pool
do Hikari sobe com `isReadOnly`.

O Terraform cria o serviço do Cloud Run uma única vez, só para que o endereço exista
e possa ser apontado no backend do gateway; todo o runtime pertence ao deploy no
GitHub Actions. Essa divisão quebra o ciclo
`ISSUER → hostname do gateway → backend → URL do serviço`.

## Fluxo de uma emissão

```mermaid
sequenceDiagram
    participant C as Consumidor
    participant G as API Gateway
    participant S as Cloud Run (auth)
    participant DB as Cloud SQL
    C->>G: POST /auth/token {cpf}
    G->>S: repassa autenticado por IAM
    S->>S: CPF.parse — dígitos verificadores
    S->>DB: SELECT id, nome, status FROM cliente
    DB-->>S: cadastro
    S->>S: status ATIVO? assina RS256 com kid
    S-->>C: 200 {access_token, token_type, expires_in}
```

Cliente inexistente e cliente inativo produzem a mesma resposta `401`
(`invalid_grant`), de propósito: a diferença revelaria quais CPFs têm cadastro.

## Fluxo de entrega

```mermaid
flowchart LR
    F["feature/*"] -->|"PR: ci.yml"| DV["develop"]
    DV --> R["release/*"]
    R -->|"cd-homolog.yml<br/>build + push + deploy"| H["Homologação"]
    R -->|"PR: ci.yml"| M["main"]
    M -->|"cd-prod.yml<br/>promove por digest"| PR2["Produção"]
    H -.->|"digest da revisão no ar"| PR2
```

Produção nunca constrói imagem. O `cd-prod.yml` lê o digest do que está servindo em
homologação e faz deploy dele, então o artefato aprovado é bit a bit o artefato
promovido.
