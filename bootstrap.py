import hashlib
import json
import os
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Tuple

BASE_PATH = "."
BASE_REPO_URL = "https://cdn.rsprox.net/runelite/launcher"


def get_size_and_hash(path: str) -> Tuple[int, str]:
    with open(path, "rb") as f:
        sha256 = hashlib.sha256(f.read()).hexdigest()
        f.seek(0, os.SEEK_END)
        size = f.tell()
        return size, sha256


def get_launcher_version() -> str:
    namespace = "{http://maven.apache.org/POM/4.0.0}"
    tree = ET.parse(f"./pom.xml")
    root = tree.getroot()
    return root.find(f"{namespace}version").text


def main():
    with open("./bootstrap-base.json", 'r') as f:
        bootstrap_base = json.load(f)
    launcher_version = get_launcher_version()
    launcher_size, launcher_sha256 = get_size_and_hash(f"{BASE_PATH}/target/launcher-{launcher_version}.jar")

    artifacts = [
        {
            "name": f"launcher-{launcher_version}.jar",
            "path": f"{BASE_REPO_URL}/net/runelite/launcher/{launcher_version}/launcher-{launcher_version}.jar",
            "size": launcher_size,
            "hash": launcher_sha256
        }
    ]
    dependency_hashes = {
        f"launcher-{launcher_version}.jar": launcher_sha256
    }

    dependencies = open(Path(BASE_PATH) / "deps.txt", 'r').readlines()

    for line in dependencies:
        line = line.strip().replace("\n", "")
        if not line or "The following files have been resolved" in line:
            continue
        artifact_dict = {}
        sections = line.split(':')
        sections = sections[0:-1]  # remove scope (e.g. compile, runtime, test)

        group_id = sections[0]
        artifact = sections[1]
        classifier = sections[3] if len(sections) == 5 else None
        version = sections[-1]

        jar_name = f"{artifact}-{version}.jar" if not classifier else f"{artifact}-{version}-{classifier}.jar"
        artifact_dict["name"] = jar_name

        local_jar_path = f"{BASE_PATH}/target/dependency/{jar_name}"
        remote_jar_path = f"{BASE_REPO_URL}/{group_id.replace('.', '/')}/{artifact}/{version}/{jar_name}"
        artifact_dict["path"] = remote_jar_path

        size, sha256 = get_size_and_hash(local_jar_path)
        artifact_dict["size"] = size
        artifact_dict["hash"] = sha256

        if classifier:
            platform = {}

            if "windows" in classifier:
                platform["name"] = "win"
            elif "macos" in classifier:
                platform["name"] = "macos"
            elif "linux" in classifier:
                platform["name"] = "linux"

            if "arm64" in classifier:
                platform["arch"] = "aarch64"
            elif "x86" in classifier:
                platform["arch"] = "x86"
            elif "x64" in classifier:
                platform["arch"] = "x86_64"

            if len(platform) > 0:
                artifact_dict["platform"] = [platform]

        artifacts.append(artifact_dict)
        dependency_hashes[jar_name] = sha256

    document = {
        **bootstrap_base,
        "artifacts": artifacts,
        "launcher": {
            "mainClass": "net.runelite.launcher.Launcher",
            "version": launcher_version
        }
    }
    print(json.dumps(dict(sorted(document.items())), indent=4))


if __name__ == '__main__':
    main()