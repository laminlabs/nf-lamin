import shutil

import nox
from laminci import upload_docs_artifact
from laminci.nox import build_docs, run_pre_commit

nox.options.default_venv_backend = "uv" if shutil.which("uv") else "none"


@nox.session
def lint(session: nox.Session) -> None:
    run_pre_commit(session)


@nox.session
def docs(session):
    build_docs(session, strict=False)
    upload_docs_artifact(aws=True)
