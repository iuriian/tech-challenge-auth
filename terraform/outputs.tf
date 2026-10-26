output "issuer" {
  description = "Valor de ISSUER (serviço) e AUTH_ISSUER_URI (app no GKE)"
  value       = "https://${google_api_gateway_gateway.auth.default_hostname}"
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

output "vpc_connector" {
  value = google_vpc_access_connector.auth.name
}
