# Firewall hook (sourced as root by init-firewall.sh) to make the shared host Docker
# socket usable by the non-root `node` user, so Testcontainers can talk to it.
if [ -S /var/run/docker.sock ]; then
    chmod 666 /var/run/docker.sock
    echo "Docker socket permissions updated for Testcontainers"
fi