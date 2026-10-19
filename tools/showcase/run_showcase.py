"""README showcase: plays dev.mite.e2e.Showcase in a real client and publishes the pictures to docs/media/.

Usage (from the mod root):  python -I tools/showcase/run_showcase.py [group,group|all]
Needs the world produced by tools/e2e/run_e2e.py (run-e2e/world), copied to run/saves/showcase. Opens a 1280x720 game window,
plays the scenes, quits. Single shots -> docs/media/<group>/<scene>.png (palette PNG); frame folders -> docs/media/<group>/<scene>.gif.
Exit code 0 = every scene logged OK, all its files exist, and no error in the client log.
"""
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
WORLD = ROOT / "run-e2e" / "world"
SAVE = ROOT / "run" / "saves" / "showcase"
SHOTS = ROOT / "run" / "screenshots" / "showcase"
MEDIA = ROOT / "docs" / "media"
LOG = ROOT / "run" / "logs" / "latest.log"
GRADLE = str(ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew"))
IGNORED = ("Unable to delete", "Unable to copy", "SDL_Init", "Advanced terminal features")
GIF_WIDTH = 640
# Optional crop box (left, top, right, bottom) in 1280x720 screenshot pixels, keyed by "group/scene". A cropped GIF keeps its
# native size up to 800 px wide, so GUI text stays sharp.
CROPS = {
    "ui/hud": (330, 580, 950, 720),
    "ui/hud_malnourished": (0, 530, 1000, 720),
    "ui/craft_inventory": (370, 105, 1110, 615),
    "ui/craft_workbench": (370, 105, 1110, 615),
}


def publish_png(src, dst, crop=None):
    img = Image.open(src).convert("RGB")
    if crop:
        img = img.crop(crop)
    img.quantize(colors=256, method=Image.Quantize.MEDIANCUT).save(dst, optimize=True)


def encode_gif(frames, dst, frame_ms):
    """One palette for the whole GIF, built from sampled frames, and no dithering: unchanged pixels stay identical from frame
    to frame, so the encoder stores only what moves (a per-frame palette re-encodes every frame and triples the size)."""
    w, h = frames[0].size
    sample = frames[::max(1, len(frames) // 8)]
    montage = Image.new("RGB", (w, h * len(sample)))
    for i, f in enumerate(sample):
        montage.paste(f, (0, i * h))
    palette = montage.quantize(colors=255, method=Image.Quantize.MEDIANCUT)
    q = [f.quantize(palette=palette, dither=Image.Dither.NONE) for f in frames]
    q[0].save(dst, save_all=True, append_images=q[1:], duration=frame_ms, loop=0, optimize=True)


def publish_gif(frames_dir, dst, frame_ms, crop=None):
    frames = []
    for f in sorted(frames_dir.glob("f*.png")):
        img = Image.open(f).convert("RGB")
        width = GIF_WIDTH
        if crop:
            img = img.crop(crop)
            width = min(img.width, 800)
        if img.width != width:
            img = img.resize((width, round(img.height * width / img.width)), Image.LANCZOS)
        frames.append(img)
    if frames:
        encode_gif(frames, dst, frame_ms)


def reencode(path):
    """Re-encodes an already published GIF with encode_gif (python run_showcase.py --reencode docs/media)."""
    from PIL import ImageSequence
    im = Image.open(path)
    frames = [f.convert("RGB") for f in ImageSequence.Iterator(im)]
    encode_gif(frames, path, im.info.get("duration", 100))


def main():
    if len(sys.argv) > 2 and sys.argv[1] == "--reencode":
        for gif in Path(sys.argv[2]).rglob("*.gif"):
            reencode(gif)
        return 0
    groups = sys.argv[1] if len(sys.argv) > 1 else "all"
    if not WORLD.exists():
        print("run tools/e2e/run_e2e.py first (it creates run-e2e/world)")
        return 2
    shutil.rmtree(SAVE, ignore_errors=True)
    shutil.copytree(WORLD, SAVE, ignore=shutil.ignore_patterns("session.lock"))
    for g in (groups.split(",") if groups != "all" else [p.name for p in SHOTS.glob("*")]):
        shutil.rmtree(SHOTS / g, ignore_errors=True)
    LOG.unlink(missing_ok=True)

    proc = subprocess.Popen([GRADLE, "runShowcaseClient", f"-Pshowcase={groups}", "--console=plain", "-q"], cwd=ROOT,
                            stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
    deadline = time.time() + 3600
    while proc.poll() is None and time.time() < deadline:
        if LOG.exists() and "SHOWCASE DONE" in LOG.read_text("utf-8", "replace"):
            break
        time.sleep(3)
    try:
        proc.wait(timeout=120)
    except subprocess.TimeoutExpired:
        proc.kill()
    time.sleep(2)  # screenshots are written on a worker thread

    text = LOG.read_text("utf-8", "replace") if LOG.exists() else ""
    done = re.findall(r"SHOWCASE (\S+/\S+) OK every=(\d+)", text)
    errors = [l for l in text.splitlines() if re.search(r"/(ERROR|FATAL)\]|Exception|Using missing texture", l)
              and not any(i in l for i in IGNORED)]
    ok = "SHOWCASE DONE" in text
    for scene, every in done:
        group, name = scene.split("/", 1)
        (MEDIA / group).mkdir(parents=True, exist_ok=True)
        shot, frames = SHOTS / group / f"{name}.png", SHOTS / group / name
        if shot.exists():
            publish_png(shot, MEDIA / group / f"{name}.png", CROPS.get(scene))
            print(f"  [PASS] {scene}.png")
        elif frames.is_dir() and any(frames.glob("f*.png")):
            publish_gif(frames, MEDIA / group / f"{name}.gif", 50 * int(every), CROPS.get(scene))  # one tick = 50 ms
            print(f"  [PASS] {scene}.gif")
        else:
            ok = False
            print(f"  [FAIL] {scene}: no screenshot")
    if not ok:
        print("  [FAIL] showcase did not finish (no SHOWCASE DONE)" if "SHOWCASE DONE" not in text else "")
    print(f"  [{'PASS' if not errors else 'FAIL'}] client log has no errors" + ("" if not errors else ": " + " | ".join(e[-200:] for e in errors[:8])))
    print(f"\nmedia in {MEDIA}")
    return 0 if ok and not errors else 1


if __name__ == "__main__":
    sys.exit(main())
