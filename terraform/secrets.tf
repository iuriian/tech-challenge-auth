resource "google_secret_manager_secret" "jwt" {
  secret_id = "jwt-signing-key"

  replication {
    auto {}
  }

  depends_on = [google_project_service.apis]
}

# A chave de assinatura nasce no apply. Sem uma versão com o PEM, o
# RsaKeyLoader derruba o serviço na partida — gerar aqui tira o passo manual de
# openssl + `gcloud secrets versions add` do caminho do primeiro deploy.
#
# O PEM fica no state (GCS, acesso restrito), como já acontece com a senha do
# auth_ro no tech-challenge-database. Rotacionar é
# `terraform apply -replace=tls_private_key.jwt`, que publica uma versão nova e
# invalida os tokens em circulação — ver Limitações conhecidas no README.
resource "tls_private_key" "jwt" {
  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "google_secret_manager_secret_version" "jwt" {
  secret      = google_secret_manager_secret.jwt.id
  secret_data = tls_private_key.jwt.private_key_pem_pkcs8
}

data "google_secret_manager_secret" "db_auth" {
  secret_id = var.db_auth_secret_id
}

data "google_secret_manager_secret" "db_app" {
  secret_id = var.db_app_secret_id
}
