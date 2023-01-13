terraform {
  required_version = ">= 1.0.0"
}

resource "digitalocean_droplet" "w10k-scala" {
  image      = "ubuntu-22-10-x64"
  name       = "w10k-scala"
  region     = "nyc1"
  size       = "s-1vcpu-1gb"
  ssh_keys   = [data.digitalocean_ssh_key.do.id]
  monitoring = true

  connection {
    host        = self.ipv4_address
    user        = "root"
    type        = "ssh"
    private_key = file(var.pvt_key)
    timeout     = "2m"
  }

  provisioner "remote-exec" {
    inline = [
      "sudo apt-get update",
      "sudo apt install default-jre",
      "scp ../broadcast/target/scala-2.13/broadcast.jar",
      "scp ../client2client/target/scala-2.13/client2client.jar",
      "git clone https://github.com/david-wiles/w10k-scala.git"
    ]
  }
}

resource "digitalocean_domain" "default" {
  name       = format("w10k-scala.%s", var.domain)
  ip_address = digitalocean_droplet.w10k-scala.ipv4_address
}

