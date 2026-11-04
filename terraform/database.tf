# O DB_URL do serviço é o output auth_db_url do tech-challenge-database. Ler o
# state dele, em vez de copiar o valor à mão para uma variable do GitHub, deixa
# uma fonte única: o IP privado do Cloud SQL muda quando a instância é
# recriada, e o deploy passa a enxergar o valor novo sem intervenção.
data "terraform_remote_state" "database" {
  backend = "gcs"

  config = {
    bucket = var.database_state_bucket
    prefix = var.database_state_prefix
  }
}
