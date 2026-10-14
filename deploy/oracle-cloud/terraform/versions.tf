terraform {
  required_version = ">= 1.14"

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "= 9.9.0"
    }
    local = {
      source  = "hashicorp/local"
      version = "= 2.9.1"
    }
  }
}
