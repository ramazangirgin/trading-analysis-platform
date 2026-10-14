variable "tenancy_ocid" {
  description = "OCID of your Oracle Cloud tenancy (profile menu > Tenancy)."
  type        = string
}

variable "user_ocid" {
  description = "OCID of the user that owns the API key (profile menu > User settings)."
  type        = string
}

variable "fingerprint" {
  description = "Fingerprint of the API key (User settings > API keys)."
  type        = string
}

variable "private_key_path" {
  description = "Path of the API key's private PEM file, e.g. ~/.oci/oci_api_key.pem."
  type        = string
}

variable "region" {
  description = "Home region identifier, e.g. eu-frankfurt-1. Always Free resources live in the home region."
  type        = string
}

variable "compartment_ocid" {
  description = "OCID of the compartment for all resources. Empty means the tenancy (root compartment)."
  type        = string
  default     = ""
}

variable "ssh_public_key_path" {
  description = "Path of the SSH public key that may log in as ubuntu."
  type        = string
}

variable "ssh_private_key_path" {
  description = "Path of the matching SSH private key; written into the Ansible inventory."
  type        = string
}

variable "availability_domain_index" {
  description = "Index of the availability domain to use (0 is the first). Try another when A1 capacity is sold out."
  type        = number
  default     = 0

  validation {
    condition     = var.availability_domain_index >= 0 && floor(var.availability_domain_index) == var.availability_domain_index
    error_message = "availability_domain_index must be a whole number, 0 or more."
  }
}

variable "ocpus" {
  description = "OCPUs of the VM. Always Free allows 4 in total."
  type        = number
  default     = 4

  validation {
    condition     = var.ocpus >= 1 && var.ocpus <= 4
    error_message = "ocpus must be between 1 and 4 (Always Free limit)."
  }
}

variable "memory_in_gbs" {
  description = "Memory of the VM in GB. Always Free allows 24 in total."
  type        = number
  default     = 24

  validation {
    condition     = var.memory_in_gbs >= 1 && var.memory_in_gbs <= 24
    error_message = "memory_in_gbs must be between 1 and 24 (Always Free limit)."
  }
}

variable "boot_volume_size_in_gbs" {
  description = "Boot volume size in GB. Always Free allows 200 in total."
  type        = number
  default     = 100

  validation {
    condition     = var.boot_volume_size_in_gbs >= 50 && var.boot_volume_size_in_gbs <= 200
    error_message = "boot_volume_size_in_gbs must be between 50 and 200 (Always Free limit)."
  }
}

variable "instance_name" {
  description = "Display name of the VM and prefix of the network resources."
  type        = string
  default     = "trading-analysis-platform"
}

variable "tailscale_hostname" {
  description = "Hostname of the VM on the tailnet; the UI is served at https://<hostname>.<tailnet>.ts.net."
  type        = string
  default     = "trading-analysis-platform"
}

variable "ssh_ingress_cidr" {
  description = "Source CIDR allowed to reach SSH (port 22) on the public IP."
  type        = string
  default     = "0.0.0.0/0"
}

variable "ssh_public" {
  description = "Open SSH on the public IP. Set false once Tailscale is up: the inventory then uses the tailnet hostname."
  type        = bool
  default     = true
}
