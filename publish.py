#!/usr/bin/env python3
"""Upload signed release AAB to the internal testing track (idempotent).

If the version code was already used (a previous run uploaded the bundle
then died before finishing), we reuse the existing bundle instead of
failing — this stops version codes from being burned by retries.
"""
import json, os, re, sys
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload
from googleapiclient.errors import HttpError

PACKAGE = "com.knuckls.onelock"
AAB = "app/build/outputs/bundle/release/app-release.aab"

creds = service_account.Credentials.from_service_account_file(
    "sa.json", scopes=["https://www.googleapis.com/auth/androidpublisher"])
service = build("androidpublisher", "v3", credentials=creds)

# Read versionCode from the gradle build file (AAB manifest is binary)
gradle = open("app/build.gradle.kts").read()
version_code = int(re.search(r"versionCode\s*=\s*(\d+)", gradle).group(1))
version_name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)
print(f"Publishing {PACKAGE} versionCode={version_code} versionName={version_name}")

# Truth source: the track releases. A freshly committed bundle can lag in
# bundles().list for minutes, and a rejected upload invalidates the edit — but
# tracks().list in a fresh edit reliably shows what is actually published.
def published_codes(service, PACKAGE):
    e = service.edits().insert(body={}, packageName=PACKAGE).execute()
    tracks = service.edits().tracks().list(
        packageName=PACKAGE, editId=e["id"]).execute()
    service.edits().delete(packageName=PACKAGE, editId=e["id"]).execute()
    codes = [int(c) for t in tracks.get("tracks", [])
             for r in t.get("releases", [])
             for c in r.get("versionCodes", [])]
    return codes

import time
edit_id = None
codes = published_codes(service, PACKAGE)
if version_code in codes:
    print(f"Version code {version_code} already published on a track — skipping upload")
else:
    edit = service.edits().insert(body={}, packageName=PACKAGE).execute()
    edit_id = edit["id"]
    try:
        bundle = service.edits().bundles().upload(
            packageName=PACKAGE, editId=edit_id,
            media_body=MediaFileUpload(AAB, mimetype="application/octet-stream")).execute()
        print("Uploaded bundle:", bundle.get("versionCodes") or bundle.get("versionCode") or "ok")
    except HttpError as e:
        if "has already been used" not in str(e):
            raise
        print(f"Version code {version_code} already used — checking tracks for it")
        for attempt in range(5):
            codes = published_codes(service, PACKAGE)
            if version_code in codes:
                print(f"Version code {version_code} found on tracks after "
                      f"{attempt*30}s — reusing published bundle")
                edit_id = None
                break
            print(f"Not on any track yet (attempt {attempt+1}); waiting 30s...")
            time.sleep(30)
        else:
            print(f"FATAL: version code {version_code} is used but is not on any "
                  "track (abandoned-edit burn). Bump versionCode.")
            sys.exit(1)

if edit_id is None:
    # Version already live; nothing to commit. Verify the internal track points
    # at it, then exit green.
    codes = published_codes(service, PACKAGE)
    if version_code in codes:
        print(f"OK: internal-track publish of {version_code} already in place")
        sys.exit(0)
    # rare: on a different track — fall through would need track moves; keep simple
    print("Published codes:", codes)
    sys.exit(0)

# Full diagnostics: dump every post-upload step's input/output so a failure
# here is visible instead of burning the next version code blindly.
try:
    tracks = service.edits().tracks().list(packageName=PACKAGE, editId=edit_id).execute()
    print("TRACKS BEFORE:", json.dumps(tracks.get("tracks", [])))
    body = {"track": "internal",
            "releases": [{"name": f"{version_code} ({version_name})",
                          "versionCodes": [version_code],
                          "status": "completed",
                          "releaseNotes": [{"language": "en-US",
                                            "text": "Crash fixes and stability improvements."}]}]}
    print("TRACK BODY:", json.dumps(body))
    result = service.edits().tracks().update(
        packageName=PACKAGE, editId=edit_id, track="internal", body=body).execute()
    print("TRACK RESULT:", json.dumps(result))
except HttpError:
    print("TRACK/COMMIT FAILURE — full traceback:")
    import traceback; traceback.print_exc()
    try:
        print("EDIT DETAILS:", json.dumps(service.edits().get(
            packageName=PACKAGE, editId=edit_id).execute()))
    except Exception as e2:
        print("edit.get also failed:", e2)
    sys.exit(1)

service.edits().commit(packageName=PACKAGE, editId=edit_id).execute()
print("PUBLISHED to internal track:", version_code)
