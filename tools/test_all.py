"""Runs every automated test: datagen (must be clean), JUnit + build, GameTests, then the dedicated-server e2e suite.

Usage (from the mod root): python -I tools/test_all.py [--client]
--client also runs the client e2e scenario (opens a game window for about two minutes).
"""
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = str(ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew"))


def step(name, args):
    print(f"== {name}", flush=True)
    ok = subprocess.run(args, cwd=ROOT).returncode == 0
    print(f"   {'OK' if ok else 'FAILED'}", flush=True)
    return ok


def main():
    results = {"datagen": step("datagen", [GRADLE, "runData", "--console=plain", "-q"])}
    git = subprocess.run(["git", "status", "--porcelain", "src/generated"], cwd=ROOT, capture_output=True, text=True)
    dirty = git.stdout.strip() if git.returncode == 0 else f"git failed: {git.stderr.strip()}"
    results["generated resources committed"] = not dirty
    print(f"== generated resources committed\n   {'OK' if not dirty else 'FAILED: ' + dirty[:300]}")
    shutil.rmtree(ROOT / "run-gametest", ignore_errors=True)  # GameTests start from the spec defaults (configs, player files)
    log = ROOT / "run-gametest" / "logs" / "latest.log"
    results["build + JUnit + GameTests"] = step("build + JUnit + GameTests", [GRADLE, "build", "runGameTestServer", "--console=plain", "-q"])
    if log.exists():
        m = re.findall(r"All \d+ required tests passed|\d+ required tests failed", log.read_text("utf-8", "replace"))
        print(f"   {m[-1] if m else 'no GameTest summary'}")
    results["e2e dedicated server"] = step("e2e dedicated server", [sys.executable, "-I", str(ROOT / "tools" / "e2e" / "run_e2e.py")])
    if "--client" in sys.argv:
        results["e2e client"] = step("e2e client", [sys.executable, "-I", str(ROOT / "tools" / "e2e" / "run_client_e2e.py")])
    print("\n" + "\n".join(f"{'PASS' if ok else 'FAIL'}  {name}" for name, ok in results.items()))
    return 0 if all(results.values()) else 1


if __name__ == "__main__":
    sys.exit(main())
