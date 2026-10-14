output "public_ip" {
  description = "Public IP of the VM."
  value       = oci_core_instance.this.public_ip
}

output "instance_id" {
  description = "OCID of the VM."
  value       = oci_core_instance.this.id
}

output "ssh_command" {
  description = "Command that logs in to the VM."
  value       = "ssh -i ${pathexpand(var.ssh_private_key_path)} ubuntu@${var.ssh_public ? oci_core_instance.this.public_ip : var.tailscale_hostname}"
}

output "ui_url_hint" {
  description = "Where the UI is served once deployed; replace <your tailnet> with your tailnet's name."
  value       = "https://${var.tailscale_hostname}.<your tailnet>.ts.net"
}
