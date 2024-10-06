import asyncio
import subprocess
from pathlib import Path
from typing import List

BASE_PATH = "./"
DEP_ROOT = "target/dependency/"
REPO_ID = "cdn.rsprox.net"
REPO_URL = f"s3://{REPO_ID}/runelite/launcher"


def index_dependencies(deps: List[str]) -> dict:
    indexed = {}

    for line in deps:
        line = line.strip().replace("\n", "")
        if not line or "The following files have been resolved" in line:
            continue

        sections = line.split(':')
        sections = sections[0:-1]  # remove scope (e.g. compile, runtime, test)

        group_id = sections[0]
        artifact = sections[1]
        classifier = sections[3] if len(sections) == 5 else None
        version = sections[-1]

        idx = f"{group_id}:{artifact}"
        if idx not in indexed:
            indexed[idx] = {
                "group": group_id,
                "artifact": artifact,
                "version": version,
                "classifiers": [classifier] if classifier else []
            }
        else:
            indexed[idx]["classifiers"].append(classifier)

    return indexed


def generate_maven_command(dep: dict) -> str:
    artifact = dep["artifact"]
    version = dep["version"]
    classifiers = dep["classifiers"]

    command = f"mvn deploy:deploy-file -Durl={REPO_URL} -DrepositoryId={REPO_ID} -Dfile={DEP_ROOT}{artifact}-{version}.jar -DgeneratePom=false -DpomFile={DEP_ROOT}{artifact}-{version}.pom"

    if len(classifiers) > 0:
        command += f" -Dfiles={','.join([f'{DEP_ROOT}{artifact}-{version}-{classifier}.jar' for classifier in classifiers])}"
        command += f" -Dclassifiers={','.join([c for c in classifiers])}"
        command += f" -Dtypes={','.join(['jar' for i in range(len(classifiers))])}"

    return command + " -Dpackaging=jar --settings settings.xml"


async def run_command(command: str):
    subprocess.run(command.split(' '))


async def amain():
    dependencies = open(Path(BASE_PATH) / "deps.txt", 'r').readlines()
    indexed = index_dependencies(dependencies)
    commands = [generate_maven_command(dep) for _, dep in indexed.items()]

    to_run = [run_command(cmd) for cmd in commands]
    await asyncio.gather(*to_run)


if __name__ == '__main__':
    asyncio.run(amain())