"""Resume a large binary push over ADB without retransmitting the existing prefix."""
import pathlib
import subprocess
import sys
import time

adb, serial, source, target = sys.argv[1:5]
source = pathlib.Path(source)
size_cmd = [adb, "-s", serial, "shell", "stat", "-c", "%s", target]
completed = subprocess.run(size_cmd, check=True, capture_output=True, text=True)
offset = int(completed.stdout.strip())
if offset > source.stat().st_size:
    raise SystemExit(f"Remote file is larger than source: {offset}")
print(f"Resuming {source.name} at byte {offset:,} / {source.stat().st_size:,}", flush=True)
proc = subprocess.Popen(
    [adb, "-s", serial, "exec-in", "sh", "-c", f"cat >> {target}"],
    stdin=subprocess.PIPE,
    stdout=subprocess.DEVNULL,
    stderr=subprocess.PIPE,
)
started = time.monotonic()
sent = 0
try:
    with source.open("rb") as stream:
        stream.seek(offset)
        while block := stream.read(256 * 1024):
            proc.stdin.write(block)
            sent += len(block)
            if sent % (16 * 1024 * 1024) < len(block):
                print(f"sent {sent:,} bytes from this resume", flush=True)
    proc.stdin.close()
    error = proc.stderr.read().decode("utf-8", "replace")
    status = proc.wait()
except Exception:
    proc.kill()
    raise
print(f"adb exec-in exit={status}; resumed bytes={sent:,}; elapsed={time.monotonic()-started:.1f}s", flush=True)
if error:
    print(error, file=sys.stderr, flush=True)
if status:
    raise SystemExit(status)
