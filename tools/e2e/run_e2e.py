"""End-to-end suite on a real NeoForge dedicated server.

Usage (from the mod root):  python -I tools/e2e/run_e2e.py [--keep-world]

Phase 1 starts a fresh seeded world, checks world generation (Overworld, Underworld, Nether), portal routing, locks,
structure distances and tick time, then stops the server. Phase 2 restarts on the same world and checks that the
generated chunks reload identically and that the log stays free of errors. Exit code 0 = all checks passed.
"""
import json
import re
import shutil
import socket
import struct
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RUN = ROOT / "run-e2e"
LOG = RUN / "logs" / "latest.log"
RCON_PORT, RCON_PASSWORD = 25599, "mite-e2e"
SEED = "20261017"
GRADLE = str(ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew"))

PROPERTIES = f"""enable-rcon=true
rcon.port={RCON_PORT}
rcon.password={RCON_PASSWORD}
server-port=25598
level-seed={SEED}
online-mode=false
view-distance=4
simulation-distance=4
spawn-protection=0
max-tick-time=-1
"""


class Rcon:
    """Minimal Source RCON client (Minecraft dialect)."""

    def __init__(self):
        self.sock = socket.create_connection(("127.0.0.1", RCON_PORT), timeout=300)
        self.req = 0
        self._send(3, RCON_PASSWORD)
        if self._recv()[0] == -1:
            raise RuntimeError("RCON login refused")

    def _send(self, kind, body):
        self.req += 1
        data = struct.pack("<ii", self.req, kind) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(data)) + data)

    def _recv(self):
        size = struct.unpack("<i", self._read(4))[0]
        data = self._read(size)
        req, _ = struct.unpack("<ii", data[:8])
        return req, data[8:-2].decode("utf-8", "replace")

    def _read(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise RuntimeError("RCON connection closed")
            buf += chunk
        return buf

    def cmd(self, command):
        self._send(2, command)
        return self._recv()[1]


class Suite:
    def __init__(self):
        self.results = []

    def check(self, name, ok, detail=""):
        self.results.append((name, bool(ok), detail))
        print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f" — {detail}" if detail else ""), flush=True)

    @property
    def failed(self):
        return [r for r in self.results if not r[1]]


def e2e_json(rcon, command):
    out = rcon.cmd(command)
    m = re.search(r"E2E (\{.*\})", out)
    if not m:
        raise RuntimeError(f"no E2E answer for {command!r}: {out[:300]}")
    return json.loads(m.group(1))


def start_server():
    LOG.unlink(missing_ok=True)
    proc = subprocess.Popen([GRADLE, "runE2eServer", "--console=plain", "-q"], cwd=ROOT,
                            stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
    deadline = time.time() + 900
    while time.time() < deadline:
        if proc.poll() is not None:
            raise RuntimeError(f"server exited early with code {proc.returncode}")
        if LOG.exists():
            text = LOG.read_text("utf-8", "replace")
            if "RCON running" in text or re.search(r"Done \([\d.,]+s\)!", text):
                for _ in range(60):
                    try:
                        return proc, Rcon()
                    except OSError:
                        time.sleep(1)
        time.sleep(2)
    proc.kill()
    raise RuntimeError("server did not start in time")


def stop_server(proc, rcon):
    try:
        rcon.cmd("stop")
    except Exception:
        pass
    try:
        proc.wait(timeout=180)
    except subprocess.TimeoutExpired:
        proc.kill()


def block(scan, bid):
    return scan["blocks"].get(bid, {"count": 0, "minY": None, "maxY": None})


def ores(scan, name):
    a, b = block(scan, f"hardscrabble:{name}"), block(scan, f"hardscrabble:deepslate_{name}")
    ys = [y for y in (a["maxY"], b["maxY"]) if y is not None]
    return a["count"] + b["count"], max(ys) if ys else None


def phase_one(suite, rcon):
    world = e2e_json(rcon, "mitee2e world")
    suite.check("difficulty locked to hard", world["difficulty"] == "hard" and world["difficultyLocked"], str(world))
    suite.check("villages locked on a new world", world["villagesOpen"] is False)
    suite.check("Underworld and Nether loaded", world["underworldLoaded"] and world["netherLoaded"])

    ow = e2e_json(rcon, "mitee2e scan minecraft:overworld 0 0 4")
    for name, top in (("silver_ore", 96), ("mithril_ore", 0)):
        count, max_y = ores(ow, name)
        suite.check(f"overworld has {name}", count > 0, f"{count} blocks")
        suite.check(f"{name} below Y {top}", max_y is None or max_y <= top, f"max Y {max_y}")
    suite.check("no adamantium in the overworld", ores(ow, "adamantium_ore")[0] == 0)
    suite.check("copper and iron generate", block(ow, "minecraft:copper_ore")["count"] + block(ow, "minecraft:deepslate_copper_ore")["count"] > 0
                and block(ow, "minecraft:iron_ore")["count"] + block(ow, "minecraft:deepslate_iron_ore")["count"] > 0)
    dia = [y for y in (block(ow, "minecraft:diamond_ore")["maxY"], block(ow, "minecraft:deepslate_diamond_ore")["maxY"]) if y is not None]
    suite.check("diamonds only below Y 0", not dia or max(dia) <= 0, f"max Y {max(dia) if dia else None}")

    feats = e2e_json(rcon, 'mitee2e features "hardscrabble:underworld"')
    all_feats = [f for step in feats.values() for f in step]
    suite.check("underworld biome carries the dungeon feature", "hardscrabble:underworld_dungeon" in all_feats, ", ".join(all_feats)[:200])
    uw = e2e_json(rcon, "mitee2e scan hardscrabble:underworld 0 0 4")
    suite.check("underworld height -128..128", uw["minY"] == -128 and uw["maxY"] in (127, 128), f"{uw['minY']}..{uw['maxY']}")
    mantle = block(uw, "hardscrabble:mantle")
    suite.check("mantle on the underworld floor", mantle["count"] > 0 and mantle["minY"] == -128, str(mantle))
    suite.check("bedrock roof", (block(uw, "minecraft:bedrock")["maxY"] or 0) >= 120, str(block(uw, "minecraft:bedrock")))
    suite.check("adamantium in the underworld", ores(uw, "adamantium_ore")[0] > 0, f"{ores(uw, 'adamantium_ore')[0]} blocks")
    suite.check("underworld dungeons (spawners)", block(uw, "minecraft:spawner")["count"] > 0, str(block(uw, "minecraft:spawner")))

    cont = e2e_json(rcon, "mitee2e containers hardscrabble:underworld 0 0 4")
    spawners = {k: v for k, v in cont.items() if k.startswith("spawner:")}
    suite.check("underworld spawners spawn Longdeads", spawners and all("longdead" in k for k in spawners), str(spawners))
    suite.check("underworld chests use the dungeon loot table",
                cont.get("chest:hardscrabble:chests/underworld_dungeon", 0) > 0, str({k: v for k, v in cont.items() if k.startswith("chest:")}))
    rooms = e2e_json(rcon, "mitee2e dungeons hardscrabble:underworld 0 0 4")
    # Sealed = the air around the spawner stays within a room (a tunnel may end in a small pocket); before the fix 18 of 20.
    suite.check("underworld dungeons open onto caves", rooms["open"] > rooms["sealed"], str(rooms))

    nether = e2e_json(rcon, "mitee2e scan minecraft:the_nether 0 0 2")
    core = block(nether, "hardscrabble:core")
    suite.check("nether core layer at the bottom", core["count"] > 0 and core["minY"] == nether["minY"], str(core))

    for dim, blk, y, expected in (("minecraft:overworld", "minecraft:bedrock", -63, "hardscrabble:underworld"),
                                  ("hardscrabble:underworld", "minecraft:bedrock", -125, "minecraft:the_nether"),
                                  ("minecraft:overworld", "minecraft:stone", 64, "minecraft:overworld"),
                                  ("minecraft:the_nether", "minecraft:obsidian", 64, "minecraft:overworld")):
        r = e2e_json(rcon, f'mitee2e route {dim} "{blk}" {y}')
        suite.check(f"portal on {blk} in {dim} leads to {expected}", r["to"] == expected, r["to"])

    out = rcon.cmd("locate structure minecraft:desert_pyramid")
    m = re.search(r"\((\d+) blocks away\)", out)
    suite.check("desert pyramids far from spawn (>= 1900)", m is None or int(m.group(1)) >= 1900, out.strip()[:120])
    out = rcon.cmd("locate structure minecraft:village_plains")
    suite.check("no village near spawn while locked", "blocks away" not in out or int(re.search(r"\((\d+) blocks away\)", out).group(1)) > 128,
                out.strip()[:120])
    suite.check("villages open once unlocked", e2e_json(rcon, "mitee2e unlock_villages")["villagesOpen"] is True)
    out = rcon.cmd("locate structure #minecraft:village")
    suite.check("villages generate once unlocked", "blocks away" in out, out.strip()[:120])

    out = rcon.cmd("locate biome minecraft:mushroom_fields")
    suite.check("no mushroom fields (L03)", "blocks away" not in out, out.strip()[:120])

    time.sleep(15)  # tick query covers the last 100 ticks: let the /locate spikes above (max-tick-time=-1) leave the window
    out = rcon.cmd("tick query")
    m = re.search(r"([\d.,]+)\s*ms", out)
    mspt = float(m.group(1).replace(",", ".")) if m else None
    suite.check("average tick time under 50 ms", mspt is not None and mspt < 50, out.strip().replace("\n", " ")[:160])
    m = re.search(r"P99:\s*([\d.,]+)\s*ms", out)  # ENF-PERF-01: 99th percentile <= 50 ms
    p99 = float(m.group(1).replace(",", ".")) if m else None
    suite.check("P99 tick time at most 50 ms (ENF-PERF-01)", p99 is not None and p99 <= 50, f"P99 {p99} ms")
    return ow, uw


def phase_two(suite, rcon, ow1, uw1):
    ow2 = e2e_json(rcon, "mitee2e scan minecraft:overworld 0 0 4")
    uw2 = e2e_json(rcon, "mitee2e scan hardscrabble:underworld 0 0 4")
    suite.check("overworld chunks reload identically", ow1["blocks"] == ow2["blocks"])
    suite.check("underworld chunks reload identically", uw1["blocks"] == uw2["blocks"])
    world = e2e_json(rcon, "mitee2e world")
    suite.check("difficulty lock persists", world["difficultyLocked"])


def log_errors():
    text = LOG.read_text("utf-8", "replace") if LOG.exists() else ""
    bad = [l for l in text.splitlines() if re.search(r"/(ERROR|FATAL)\]|Exception", l)
           and "Unable to delete" not in l and "Unable to copy" not in l]
    return bad


def main():
    keep = "--keep-world" in sys.argv
    RUN.mkdir(exist_ok=True)
    (RUN / "eula.txt").write_text("# EULA accepted by the project owner on 2026-10-17\neula=true\n")
    (RUN / "server.properties").write_text(PROPERTIES)
    if not keep:
        shutil.rmtree(RUN / "world", ignore_errors=True)
    suite = Suite()

    print("Phase 1: fresh world", flush=True)
    proc, rcon = start_server()
    try:
        ow, uw = phase_one(suite, rcon)
    finally:
        stop_server(proc, rcon)
    errors = log_errors()
    suite.check("phase 1 log has no errors", not errors, "; ".join(e[-160:] for e in errors[:5]))

    print("Phase 2: restart on the same world", flush=True)
    proc, rcon = start_server()
    try:
        phase_two(suite, rcon, ow, uw)
    finally:
        stop_server(proc, rcon)
    errors = log_errors()
    suite.check("phase 2 log has no errors", not errors, "; ".join(e[-160:] for e in errors[:5]))

    report = RUN / "e2e-report.json"
    report.write_text(json.dumps([{"check": n, "ok": ok, "detail": d} for n, ok, d in suite.results], indent=2, ensure_ascii=False))
    print(f"\n{len(suite.results) - len(suite.failed)}/{len(suite.results)} checks passed — report: {report}")
    return 1 if suite.failed else 0


if __name__ == "__main__":
    sys.exit(main())
