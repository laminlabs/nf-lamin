import os
import re
import shutil
from pathlib import Path

import nox
from laminci import convert_executable_md_files, upload_docs_artifact
from laminci.nox import build_docs, login_testuser1, run, run_pre_commit

IS_CI = "CI" in os.environ

# we'd like to aggregate coverage information across sessions
# and for this the code needs to be located in the same
# directory in every github action runner
# this also allows to break out an installation section
nox.options.default_venv_backend = "none" if IS_CI else "uv"

GROUPS = {}
GROUPS["guide"] = ["guide.ipynb"]


def check_relative_reference_links() -> None:
    # lamin-docs moves docs/reference/*.md to docs/nf-lamin/ without rewriting
    # their links, so absolute links between them only resolve here
    pattern = re.compile(r"\{doc\}`(?:[^`<]*<)?/|\]\(/")
    offenders = [
        f"{path}:{lineno}: {line.strip()}"
        for path in sorted(Path("docs/reference").glob("*.md"))
        for lineno, line in enumerate(path.read_text().splitlines(), start=1)
        if pattern.search(line)
    ]
    if offenders:
        raise SystemExit("Use relative links between docs/reference/ pages:\n" + "\n".join(offenders))


@nox.session
def lint(session: nox.Session) -> None:
    run_pre_commit(session)


@nox.session
@nox.parametrize(
    "group",
    [
        "guide",
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
    # Fresh CI checkout still has the source .md files. Remove each one before
    # recovering the executed notebook so Sphinx does not see both.
    for group in [
        "guide",
    ]:
        for path in Path(f"./docs_{group}").glob("*"):
            md_path = Path("docs") / f"{path.stem}.md"
            if md_path.exists():
                md_path.unlink()
            path.rename(Path("docs") / path.name)
    check_relative_reference_links()
    build_docs(session, strict=True)
    upload_docs_artifact()
