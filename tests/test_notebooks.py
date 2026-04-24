from pathlib import Path

import nbproject_test as test
from noxfile import GROUPS

DOCS = Path(__file__).parents[1] / "docs/"


def test_guide():
    for filename in GROUPS["guide"]:
        print(filename)
        test.execute_notebooks(DOCS / filename, write=True, print_outputs=True)
