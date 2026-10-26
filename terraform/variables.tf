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
  default     = "oficina-vpc"
  description = "VPC criada no tech-challenge-infra-database"
}

variable "connector_cidr" {
  type        = string
  default     = "10.8.0.0/28"
  description = "Faixa /28 livre na VPC (não pode sobrepor subnets nem ranges do GKE)"
}

variable "function_name" {
  type    = string
  default = "oficina-auth"
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