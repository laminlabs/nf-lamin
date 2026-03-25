import os

import nox
from laminci import upload_docs_artifact
from laminci.nox import build_docs, run_pre_commit

IS_CI = "CI" in os.environ

# we'd like to aggregate coverage information across sessions
# and for this the code needs to be located in the same
# directory in every github action runner
# this also allows to break out an installation section
nox.options.default_venv_backend = "none" if IS_CI else "uv"


@nox.session
def lint(session: nox.Session) -> None:
    run_pre_commit(session)


@nox.session
def docs(session):
    build_docs(session, strict=False)
    upload_docs_artifact(aws=True)
