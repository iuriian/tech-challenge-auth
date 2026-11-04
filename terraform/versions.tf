terraform {
  required_version = ">= 1.6"

  required_providers {
    google      = { source = "hashicorp/google", version = "~> 8.0" }
    google-beta = { source = "hashicorp/google-beta", version = "~> 8.0" }
  }

  backend "gcs" {
    bucket = "tech-challenge-507123-tfstate"
    prefix = "tech-challenge/auth/staging"
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

provider "google-beta" {
  project = var.project_id
  region  = var.region
}
