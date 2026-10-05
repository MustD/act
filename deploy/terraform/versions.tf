terraform {
  required_version = "~> 1.15"

  required_providers {
    digitalocean = {
      source  = "digitalocean/digitalocean"
      version = "~> 2.104"
    }
  }
}
