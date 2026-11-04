project_id            = "tech-challenge-507123"
region                = "us-central1"
network               = "default"
ci_service_account_id = "github-actions-cd"

# State do tech-challenge-database aplicado em homologação: de onde sai o DB_URL.
database_state_bucket = "tech-challenge-507123-tfstate"
database_state_prefix = "tech-challenge/database/staging"
