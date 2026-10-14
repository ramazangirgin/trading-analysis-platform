resource "local_file" "inventory" {
  filename        = "${path.module}/../ansible/inventory.ini"
  file_permission = "0600"
  content = templatefile("${path.module}/inventory.ini.tftpl", {
    ansible_host         = var.ssh_public ? oci_core_instance.this.public_ip : var.tailscale_hostname
    ssh_private_key_path = pathexpand(var.ssh_private_key_path)
    tailscale_hostname   = var.tailscale_hostname
  })
}
