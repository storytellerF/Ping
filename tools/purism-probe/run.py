"""Compare Core geometry on Android using model3/MOC3 and sampled motion3 curves.

Run under an exclusive device lease. Model assets remain outside the repository.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile


def evaluate(segments, time):
    """Evaluate linear, Bezier, stepped and inverse-stepped Cubism segments."""
    x0, y0 = segments[:2]
    if time <= x0:
        return y0
    index = 2
    while index < len(segments):
        kind = segments[index]
        index += 1
        if kind == 1:
            x1, y1, x2, y2, x3, y3 = segments[index:index + 6]
            index += 6
            if time <= x3:
                def bezier(a, b, c, d, t):
                    s = 1 - t
                    return s*s*s*a + 3*s*s*t*b + 3*s*t*t*c + t*t*t*d
                lo, hi = 0.0, 1.0
                for _ in range(40):
                    middle = (lo + hi) / 2
                    if bezier(x0, x1, x2, x3, middle) < time:
                        lo = middle
                    else:
                        hi = middle
                return bezier(y0, y1, y2, y3, (lo + hi) / 2)
            x0, y0 = x3, y3
        elif kind in (0, 2, 3):
            x1, y1 = segments[index:index + 2]
            index += 2
            if time <= x1:
                if kind == 2:
                    return y0
                if kind == 3:
                    return y1
                return y0 + (y1 - y0) * ((time - x0) / (x1 - x0) if x1 > x0 else 0)
            x0, y0 = x1, y1
        else:
            raise ValueError(f"Unsupported segment kind {kind}")
    return y0


def trajectory(model_path, output, samples):
    model = json.loads(model_path.read_text(encoding="utf-8-sig"))
    references = model["FileReferences"]
    motions = [model_path.parent / entry["File"]
               for group in references.get("Motions", {}).values() for entry in group]
    skipped = set()
    with output.open("w", encoding="utf-8", newline="\n") as stream:
        stream.write("FRAME\nEND\n")
        for path in motions:
            motion = json.loads(path.read_text(encoding="utf-8-sig"))
            duration = motion["Meta"]["Duration"]
            for frame in range(samples):
                time = duration * frame / (samples - 1)
                stream.write("FRAME\n")
                for curve in motion["Curves"]:
                    target, name = curve["Target"], curve["Id"]
                    if target not in ("Parameter", "PartOpacity"):
                        skipped.add(f"{target}:{name}")
                        continue
                    value = evaluate(curve["Segments"], time)
                    if not math.isfinite(value) or any(c.isspace() for c in name):
                        raise ValueError("Invalid motion value or parameter ID")
                    stream.write(f"{target} {name} {value:.9g}\n")
                stream.write("END\n")
    return model_path.parent / references["Moc"], len(motions), sorted(skipped)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--models", type=Path, required=True)
    parser.add_argument("--build", type=Path, required=True)
    parser.add_argument("--ndk", type=Path, required=True)
    parser.add_argument("--aar", type=Path, required=True,
                        help="External official Core AAR used only as a comparison baseline")
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--samples", type=int, default=61)
    args = parser.parse_args()
    if args.samples < 2:
        parser.error("--samples must be at least 2")
    def adb(*parts, check=True):
        return subprocess.run(["adb", "-s", args.serial, *map(str, parts)],
                              check=check, capture_output=True, text=True)
    abi = adb("shell", "getprop", "ro.product.cpu.abi").stdout.strip()
    if abi != "arm64-v8a":
        raise RuntimeError("This probe build currently targets arm64-v8a")
    remote = adb("shell", "mktemp", "-d", "/data/local/tmp/purism-probe.XXXXXX").stdout.strip()
    if not re.fullmatch(r"/data/local/tmp/purism-probe\.[A-Za-z0-9]+", remote):
        raise RuntimeError("Unexpected device temporary directory")
    report = {"abi": abi, "purismRevision": "1069334965522df5d0b791e97f01e26b19c45456",
              "officialAarSha256": hashlib.sha256(args.aar.read_bytes()).hexdigest(),
              "samplesPerMotion": args.samples, "results": []}
    try:
        with tempfile.TemporaryDirectory(prefix="purism-probe-") as local:
            local = Path(local)
            with zipfile.ZipFile(args.aar) as aar:
                original = local / "official.so"
                original.write_bytes(aar.read(f"jni/{abi}/libLive2DCubismCoreJNI.so"))
            adb("push", original, f"{remote}/official.so")
            adb("push", args.build / "core-probe", f"{remote}/core-probe")
            adb("push", args.build / "_deps/purism-build/libPurismCore-v5.so", f"{remote}/purism.so")
            runtime = args.ndk / "toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"
            adb("push", runtime, f"{remote}/libc++_shared.so")
            adb("shell", "chmod", "700", f"{remote}/core-probe")
            models = sorted(args.models.rglob("*.model3.json"))
            if not models:
                raise RuntimeError("No model3 files found")
            for model in models:
                row = {"model": model.stem, "passed": False}
                try:
                    path = local / "trajectory.txt"
                    moc, motions, skipped = trajectory(model, path, args.samples)
                    row.update(motions=motions, skippedModelCurves=skipped,
                               mocSha256=hashlib.sha256(moc.read_bytes()).hexdigest())
                    adb("push", moc, f"{remote}/model.moc3")
                    adb("push", path, f"{remote}/trajectory.txt")
                    result = adb("shell", f"LD_LIBRARY_PATH={remote}", f"{remote}/core-probe",
                                 f"{remote}/official.so", f"{remote}/purism.so",
                                 f"{remote}/model.moc3", f"{remote}/trajectory.txt", check=False)
                    row["exitCode"] = result.returncode
                    json_lines = [line for line in result.stdout.splitlines() if line.startswith("{")]
                    if json_lines:
                        row.update(json.loads(json_lines[-1]))
                    else:
                        row["error"] = result.stdout.strip()
                    if result.stderr.strip():
                        row["stderr"] = result.stderr.strip()
                    row["passed"] = result.returncode == 0
                except (ValueError, OSError, subprocess.SubprocessError) as error:
                    row["error"] = str(error)
                report["results"].append(row)
                print(json.dumps(row), flush=True)
    finally:
        # Only delete the unique directory returned by mktemp above.
        adb("shell", "rm", "-rf", remote)
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return 0 if report["results"] and all(row["passed"] for row in report["results"]) else 1


if __name__ == "__main__":
    raise SystemExit(main())
