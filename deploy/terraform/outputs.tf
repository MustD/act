output "private_ip" {
  value       = digitalocean_droplet.web.ipv4_address_private
  description = "What the edge's upstream points at, and what deploy tasks SSH to through the edge."
}

output "droplet_id" {
  value = digitalocean_droplet.web.id
}
