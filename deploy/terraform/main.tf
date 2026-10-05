provider "digitalocean" {}

data "digitalocean_vpc" "edge" {
  name = var.vpc_name
}

resource "digitalocean_ssh_key" "deploy" {
  name       = "act-web-deploy"
  public_key = var.ssh_public_key
}

resource "digitalocean_droplet" "web" {
  name     = "act-web"
  region   = var.region
  size     = var.size
  image    = var.image
  vpc_uuid = data.digitalocean_vpc.edge.id
  ssh_keys = [digitalocean_ssh_key.deploy.fingerprint]

  user_data = templatefile("${path.module}/cloud-init.yaml.tftpl", {
    ssh_public_key = trimspace(var.ssh_public_key)
  })

  lifecycle {
    # The edge proxies to this droplet's private IP, so a replacement moves the upstream.
    # A cloud-init or image edit must not silently destroy and recreate it; replacing the
    # droplet is a deliberate act (`terraform apply -replace=...`) followed by updating the edge.
    ignore_changes = [user_data, image]
  }
}

resource "digitalocean_firewall" "web" {
  name        = "act-web"
  droplet_ids = [digitalocean_droplet.web.id]

  inbound_rule {
    protocol         = "tcp"
    port_range       = "80"
    source_addresses = ["${var.edge_private_ip}/32"]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "22"
    source_addresses = ["${var.edge_private_ip}/32"]
  }

  outbound_rule {
    protocol              = "tcp"
    port_range            = "1-65535"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }

  outbound_rule {
    protocol              = "udp"
    port_range            = "1-65535"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }

  outbound_rule {
    protocol              = "icmp"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }
}
