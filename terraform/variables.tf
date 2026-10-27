variable "project_id" {
  type        = string
  description = "Projeto GCP"
}

variable "region" {
  type    = string
  default = "us-central1"
}

variable "network" {
  type        = string
  default     = "default"
  description = "VPC do GKE, onde o Cloud SQL tem IP privado (peering criado no tech-challenge-database)"
}

variable "service_name" {
  type        = string
  default     = "oficina-auth"
  description = "Nome do serviço no Cloud Run e do repositório de imagens"
}

variable "bootstrap_image" {
  type        = string
  default     = "us-docker.pkg.dev/cloudrun/container/hello"
  description = "Imagem da primeira revisão, substituída pelo deploy do CI"
}

variable "db_auth_secret_id" {
  type        = string
  default     = "db-auth-password"
  description = "Secret com a senha do usuário auth_ro (criado no infra-database)"
}

variable "ci_service_account_id" {
  type        = string
  description = "account_id da SA de deploy usada pelo GitHub Actions"
}
