#!/usr/bin/env python3
"""Minimal Source-RCON client for the local test server (no dependencies).

Reads rcon.port and rcon.password from <repo>/test-server/server.properties (the password lives only there).
Sends each argument as one console command and prints the reply. Long replies arrive in several packets.

Usage:
  python dev/rcon.py "stage tree" "progressivestages validate"
  python dev/rcon.py --timeout 600 "chunky start"
Exit code: 0 = all commands sent, 1 = connection or auth failure.
"""
import argparse
import os
import re
import socket
import struct
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROPS = os.path.join(REPO, "test-server", "server.properties")
LOGIN, COMMAND = 3, 2
COLOR = re.compile("§.")


def props(path):
    out = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.rstrip("\r\n").split("=", 1)
                out[k.strip()] = v.strip()
    return out


class Rcon:
    def __init__(self, host, port, password, timeout):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.next_id = 1
        rid = self._send(LOGIN, password)
        got, _ = self._recv()
        if got != rid:
            raise PermissionError("RCON authentication failed")

    def _send(self, kind, body):
        rid = self.next_id
        self.next_id += 1
        data = struct.pack("<ii", rid, kind) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(data)) + data)
        return rid

    def _read(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise ConnectionError("RCON connection closed")
            buf += chunk
        return buf

    def _recv(self):
        size = struct.unpack("<i", self._read(4))[0]
        data = self._read(size)
        rid, _kind = struct.unpack("<ii", data[:8])
        return rid, data[8:-2].decode("utf-8", "replace")

    def command(self, cmd):
        """Vanilla splits long replies into 4096-byte packets without an end marker (and drops the
        connection on an empty command), so after the first packet it reads until a short silence."""
        rid = self._send(COMMAND, cmd)
        parts = []
        timeout = self.sock.gettimeout()
        try:
            while True:
                got, body = self._recv()
                if got == rid:
                    parts.append(body)
                if len(body) < 4000:
                    break
                self.sock.settimeout(0.5)
        except socket.timeout:
            pass
        finally:
            self.sock.settimeout(timeout)
        return "".join(parts)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("commands", nargs="+")
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--timeout", type=float, default=120.0, help="socket timeout per reply in seconds")
    ap.add_argument("--raw", action="store_true", help="keep section-sign colour codes")
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    p = props(PROPS)
    try:
        r = Rcon(a.host, int(p.get("rcon.port", "25575")), p.get("rcon.password", ""), a.timeout)
    except (OSError, PermissionError) as e:
        print(f"RCON connect failed: {e}", file=sys.stderr)
        sys.exit(1)
    for c in a.commands:
        out = r.command(c)
        if not a.raw:
            out = COLOR.sub("", out)
        print(f"> {c}")
        print(out if out.endswith("\n") or not out else out + "\n", end="")
    sys.exit(0)


if __name__ == "__main__":
    main()
