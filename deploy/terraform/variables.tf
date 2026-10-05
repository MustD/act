variable "edge_private_ip" {
  type        = string
  description = "Private VPC IP of the edge proxy. The only address the firewall lets reach ports 80 and 22."
}

variable "vpc_name" {
  type        = string
  description = "Name of the existing VPC the edge lives in. The droplet joins it."
}

variable "ssh_key_name" {
  type        = string
  description = "Name of an SSH key already in the DigitalOcean account. Installed for root by DO and for the deploy user by cloud-init."
}

variable "region" {
  type    = string
  default = "fra1"
}

variable "size" {
  type    = string
  default = "s-1vcpu-1gb"
}

variable "image" {
  type        = string
  default     = "ubuntu-26-04-x64"
  description = "Falls back to ubuntu-24-04-x64 if 26.04 is not offered in the region."
}
