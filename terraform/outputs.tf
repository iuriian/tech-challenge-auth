output "issuer" {
  description = "Valor de ISSUER (serviço) e AUTH_ISSUER_URI (app no GKE)"
  value       = "https://${google_api_gateway_gateway.auth.default_hostname}"
}

output "db_url" {
  description = "JDBC do Cloud SQL, lido pelo deploy direto do state do tech-challenge-database"
  value       = data.terraform_remote_state.database.outputs.auth_db_url
}

output "service_url" {
  description = "URL direta do Cloud Run, usada como backend do API Gateway"
  value       = google_cloud_run_v2_service.auth.uri
}

output "image_repo" {
  description = "Caminho da imagem no Artifact Registry (sem a tag)"
  value       = local.image_repo
}

output "service_sa" {
  value = google_service_account.fn.email
}

output "gateway_sa" {
  value = google_service_account.gateway.email
}

# O único valor que ainda precisa ser configurado à mão no GitHub. Project,
# region, ISSUER e DB_URL saem do tfvars e do state no próprio deploy.
output "github_environment" {
  description = "O que configurar nos Environments staging e production"
  value = {
    secret_GCP_SA_KEY = "chave da SA ${data.google_service_account.ci.email}"
  }
}
