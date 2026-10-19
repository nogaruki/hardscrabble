"""Client end-to-end run: opens a copy of the e2e world in a real client and walks through dev.mite.e2e.ClientE2e.

Usage (from the mod root):  python -I tools/e2e/run_client_e2e.py
Needs the world produced by tools/e2e/run_e2e.py (run-e2e/world). Opens a game window for about two minutes, then quits.
Screenshots: run/screenshots/e2e_<step>.png. Exit code 0 = every step logged and no error in the client log.
"""
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORLD = ROOT / "run-e2e" / "world"
SAVE = ROOT / "run" / "saves" / "mite-e2e"
SHOTS = ROOT / "run" / "screenshots"
LOG = ROOT / "run" / "logs" / "latest.log"
GRADLE = str(ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew"))
STEPS = ["hud", "inventory", "items", "mobs", "models", "blocks", "armor", "bloodmoon", "moondog", "underworld", "nether"]
IGNORED = ("Unable to delete", "Unable to copy", "SDL_Init", "Advanced terminal features")


def main():
    if not WORLD.exists():
        print("run tools/e2e/run_e2e.py first (it creates run-e2e/world)")
        return 2
    shutil.rmtree(SAVE, ignore_errors=True)
    shutil.copytree(WORLD, SAVE, ignore=shutil.ignore_patterns("session.lock"))
    for old in SHOTS.glob("e2e_*.png"):
        old.unlink()
    LOG.unlink(missing_ok=True)

    proc = subprocess.Popen([GRADLE, "runE2eClient", "--console=plain", "-q"], cwd=ROOT,
                            stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
    deadline = time.time() + 900
    while proc.poll() is None and time.time() < deadline:
        if LOG.exists() and "CLIENT_E2E DONE" in LOG.read_text("utf-8", "replace"):
            break
        time.sleep(3)
    try:
        proc.wait(timeout=120)
    except subprocess.TimeoutExpired:
        proc.kill()

    text = LOG.read_text("utf-8", "replace") if LOG.exists() else ""
    done = set(re.findall(r"CLIENT_E2E (\w+) OK", text))
    errors = [l for l in text.splitlines() if re.search(r"/(ERROR|FATAL)\]|Exception|Using missing texture", l)
              and not any(i in l for i in IGNORED)]
    ok = True
    for s in STEPS:
        shot = SHOTS / f"e2e_{s}.png"
        passed = s in done and shot.exists()
        ok &= passed
        print(f"  [{'PASS' if passed else 'FAIL'}] step {s}" + ("" if passed else " (no log line or no screenshot)"))
    m = re.search(r"CLIENT_E2E underworld OK .*fog=([\d.]+)", text)  # 32-day fog cycle: day 120 is between clear (128) and dense (48)
    passed = bool(m and 48 <= float(m.group(1)) < 127)
    ok &= passed
    print(f"  [{'PASS' if passed else 'FAIL'}] underworld fog cycle" + ("" if passed else f" (got {m.group(1) if m else 'nothing'})"))
    for step, expect in (("bloodmoon", "moon=BLOOD"), ("moondog", "moon=MOON_DOG"), ("underworld", "dim=hardscrabble:underworld"), ("nether", "dim=minecraft:the_nether"), ("nether", "ahead=minecraft:glowstone"),
                         ("underworld", "-60, ")):
        m = re.search(rf"CLIENT_E2E {step} OK (.*)", text)
        passed = bool(m and expect in m.group(1))
        ok &= passed
        print(f"  [{'PASS' if passed else 'FAIL'}] {step}: {expect}" + ("" if passed else f" (got {m.group(1) if m else 'nothing'})"))
    print(f"  [{'PASS' if not errors else 'FAIL'}] client log has no errors" + ("" if not errors else ": " + " | ".join(e[-200:] for e in errors[:8])))
    ok &= not errors
    print(f"\nscreenshots in {SHOTS}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
