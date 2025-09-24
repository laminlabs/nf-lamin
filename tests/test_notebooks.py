from pathlib import Path

import nbproject_test as test
from noxfile import GROUPS

DOCS = Path(__file__).parents[1] / "docs/"


def test_postrun():
    for filename in GROUPS["postrun"]:
        print(filename)
        test.execute_notebooks(DOCS / filename, write=True, print_outputs=True)


def test_plugin():
    for filename in GROUPS["plugin"]:
        print(filename)
        test.execute_notebooks(DOCS / filename, write=True, print_outputs=True)
