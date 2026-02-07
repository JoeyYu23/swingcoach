"""Unit tests for network_info module."""

import re
import sys
import os

# Ensure backend is on sys.path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from network_info import get_local_ip, get_connection_url


def test_get_local_ip_returns_valid_ip():
    ip = get_local_ip()
    # Should be a dotted-quad IPv4 address
    parts = ip.split(".")
    assert len(parts) == 4
    for part in parts:
        assert part.isdigit()
        assert 0 <= int(part) <= 255


def test_get_connection_url_includes_port():
    url = get_connection_url(8001)
    assert url.startswith("http://")
    assert ":8001" in url


def test_get_connection_url_uses_local_ip():
    ip = get_local_ip()
    url = get_connection_url(9999)
    assert ip in url
