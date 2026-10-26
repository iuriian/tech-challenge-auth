output "issuer" {
  description = "Valor de ISSUER (função) e AUTH_ISSUER_URI (app no GKE)"
  value       = "https://${google_api_gateway_gateway.auth.default_hostname}"
}

output "function_url" {
  value = local.function_url
}

output "function_sa" {
  value = google_service_account.fn.email
}

output "gateway_sa" {
  value = google_service_account.gateway.email
}

output "build_sa" {
  value = google_service_account.build.id
}

output "vpc_connector" {
  value = google_vpc_access_connector.auth.name
}
