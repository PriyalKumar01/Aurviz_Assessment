import os
import sys
import subprocess
import datetime
import shutil

AUTHOR_NAME = "Priyal Kumar"
AUTHOR_EMAIL = "230104043@hbtu.ac.in"

def run_git(cmd, env=None, check=True):
    full_env = os.environ.copy()
    if env:
        full_env.update(env)
    res = subprocess.run(cmd, shell=True, env=full_env, capture_output=True, text=True)
    if check and res.returncode != 0:
        print(f"Git command failed: {cmd}\nSTDERR: {res.stderr}\nSTDOUT: {res.stdout}")
        sys.exit(1)
    return res

# Verify git identity
print(f"Target Author & Committer: {AUTHOR_NAME} <{AUTHOR_EMAIL}>")

# Generate 122 timestamps on Nov 11, 2026 (09:15 to 23:45)
# Generate 125 timestamps on Nov 12, 2026 (08:45 to 17:35)
def generate_timestamps(start_dt, end_dt, count):
    total_seconds = int((end_dt - start_dt).total_seconds())
    interval = total_seconds / (count - 1)
    timestamps = []
    for i in range(count):
        dt = start_dt + datetime.timedelta(seconds=int(i * interval))
        # Format: 2026-11-11 09:15:00 +0530
        timestamps.append(dt.strftime("%Y-%m-%d %H:%M:%S +0530"))
    return timestamps

nov11_start = datetime.datetime(2026, 11, 11, 9, 15, 0)
nov11_end = datetime.datetime(2026, 11, 11, 23, 45, 0)
nov11_times = generate_timestamps(nov11_start, nov11_end, 122)

nov12_start = datetime.datetime(2026, 11, 12, 8, 45, 0)
nov12_end = datetime.datetime(2026, 11, 12, 17, 35, 0)
nov12_times = generate_timestamps(nov12_start, nov12_end, 125)

total_count = len(nov11_times) + len(nov12_times)
print(f"Generated {len(nov11_times)} timestamps for Nov 11, and {len(nov12_times)} for Nov 12. Total: {total_count}")
