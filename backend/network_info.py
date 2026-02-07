"""Network utility for discovering LAN IP address."""

import socket


def get_local_ip() -> str:
    """Return the local LAN IP address via UDP socket trick."""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            # Doesn't actually send data — just forces OS to pick the right interface
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"


def get_connection_url(port: int) -> str:
    """Return the full HTTP URL for connecting to this machine."""
    return f"http://{get_local_ip()}:{port}"
