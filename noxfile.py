import os
import shutil
from pathlib import Path

import nox
from laminci import convert_executable_md_files, upload_docs_artifact
from laminci.nox import build_docs, login_testuser1, run, run_pre_commit, run_pytest

# we'd like to aggregate coverage information across sessions
# and for this the code needs to be located in the same
# directory in every github action runner
# this also allows to break out an installation section
nox.options.default_venv_backend = "none"

IS_PR = os.getenv("GITHUB_EVENT_NAME") != "push"

GROUPS = {}
GROUPS["postrun"] = ["nextflow-postrun.ipynb"]
GROUPS["plugin"] = ["nextflow.ipynb"]


@nox.session
def lint(session: nox.Session) -> None:
    run_pre_commit(session)


@nox.session
@nox.parametrize(
    "group",
    [
        "postrun",
        "plugin",
    ],
)
def build(session, group):
    session.run(
        "uv",
        "pip",
        "install",
        "--system",
        "lamindb[jupyter,bionty]",
    )
    session.run(*"pip install -e .[dev]".split())
    convert_executable_md_files("./docs")
    login_testuser1(session)
    run(session, f"pytest -s ./tests/test_notebooks.py::test_{group}")

    # move artifacts into right place
    target_dir = Path(f"./docs_{group}")
    target_dir.mkdir(exist_ok=True)
    for filename in GROUPS[group]:
        shutil.copy(Path("docs") / filename, target_dir / filename)


@nox.session
def docs(session):
    for group in [
        "postrun",
        "plugin",
    ]:
        for path in Path(f"./docs_{group}").glob("*"):
            path.rename(f"./docs/{path.name}")
    build_docs(session, strict=False)
    upload_docs_artifact(aws=True)
