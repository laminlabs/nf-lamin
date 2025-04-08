import nox
from laminci import upload_docs_artifact
from laminci.nox import build_docs, login_testuser1, run_pre_commit, run

nox.options.default_venv_backend = "none"


@nox.session
def lint(session: nox.Session) -> None:
    run_pre_commit(session)


@nox.session()
def build(session):
    run(session, "uv pip install --system laminci nbproject-test")
    login_testuser1(session)
    build_docs(session)
    upload_docs_artifact(aws=True)
