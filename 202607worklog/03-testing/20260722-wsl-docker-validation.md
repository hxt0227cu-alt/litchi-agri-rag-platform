# WSL and Docker validation - 2026-07-22

## Environment

- WSL: 2.7.10.0
- Ubuntu: 24.04 LTS
- Kernel: 6.18.33.2-microsoft-standard-WSL2
- Docker Engine CE: 29.6.2
- Docker Compose Plugin: 5.3.1
- Docker Buildx: 0.35.0
- WSL allocation: 8 CPUs and 7.755 GiB visible memory

## Checks

| Check | Result |
| --- | --- |
| systemd PID 1 | Passed |
| Docker service enabled | Passed |
| Docker service active | Passed |
| Non-root Docker socket access | Passed |
| Storage driver | `overlayfs` |
| Cgroup driver and version | `systemd`, v2 |
| Runtime | `runc` |
| Project `docker compose config --quiet` | Passed |
| Image pull through Alibaba Cloud registry | Passed |
| Foreground container run and clean shutdown | Passed, exit code 0 |
| Smoke container cleanup | Passed, no residual container |

## Network finding

Direct Docker Hub access to `registry-1.docker.io:443` timed out. This is a network-path limitation rather than an Engine or socket failure. A permanent public registry accelerator was intentionally not configured because availability and supply-chain ownership could not be guaranteed. The smoke test used the Alibaba Cloud Kubernetes mirror and recorded the exact image digest.

WSL may reclaim a distribution when no Windows-side WSL client remains connected. For long-running local services, keep an Ubuntu/WSL terminal or project process active. Production services must run on the target Linux or Kubernetes host rather than relying on the local WSL lifecycle.
