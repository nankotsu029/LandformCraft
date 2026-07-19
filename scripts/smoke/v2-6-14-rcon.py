#!/usr/bin/env python3
"""Minimal Minecraft RCON client for V2-6-14 smoke automation."""
from __future__ import annotations

import argparse
import socket
import struct
import sys
import time


def _send(sock: socket.socket, request_id: int, request_type: int, payload: str) -> tuple[int, int, str]:
    payload_bytes = payload.encode("utf-8")
    # length covers id + type + payload + two null terminators
    length = 10 + len(payload_bytes)
    packet = struct.pack("<iii", length, request_id, request_type) + payload_bytes + b"\x00\x00"
    sock.sendall(packet)
    length_data = _recv_exact(sock, 4)
    (response_length,) = struct.unpack("<i", length_data)
    data = _recv_exact(sock, response_length)
    response_id, response_type = struct.unpack("<ii", data[:8])
    payload_out = data[8:-2]
    return response_id, response_type, payload_out.decode("utf-8", errors="replace")


def _recv_exact(sock: socket.socket, size: int) -> bytes:
    chunks = bytearray()
    while len(chunks) < size:
        chunk = sock.recv(size - len(chunks))
        if not chunk:
            raise ConnectionError("RCON connection closed")
        chunks.extend(chunk)
    return bytes(chunks)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=25575)
    parser.add_argument("--password", required=True)
    parser.add_argument("--timeout", type=float, default=120.0)
    parser.add_argument("command")
    args = parser.parse_args()

    deadline = time.time() + args.timeout
    last_error: Exception | None = None
    while time.time() < deadline:
        try:
            with socket.create_connection((args.host, args.port), timeout=5.0) as sock:
                sock.settimeout(args.timeout)
                response_id, _, _ = _send(sock, 1, 3, args.password)
                if response_id == -1:
                    raise PermissionError("RCON authentication failed")
                _, _, body = _send(sock, 2, 2, args.command)
                sys.stdout.write(body)
                if body and not body.endswith("\n"):
                    sys.stdout.write("\n")
                return 0
        except Exception as exc:  # noqa: BLE001 - retry until smoke deadline
            last_error = exc
            time.sleep(1.0)
    print(f"RCON failed: {last_error}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
