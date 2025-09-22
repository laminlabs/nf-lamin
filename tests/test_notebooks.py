from pathlib import Path

import nbproject_test as test


def test_notebooks():
    # assuming this is in the tests folder
    docs_folder = Path(__file__).parents[1] / "docs/"

    # Ensure the docs folder exists
    if not docs_folder.exists():
        raise FileNotFoundError(f"Docs folder not found: {docs_folder}")

    # Execute notebooks in the docs folder
    # This will find and execute all .ipynb files in the docs directory
    # Note: passing the directory directly to execute_notebooks handles
    # filtering for .ipynb files and ignores non-notebook files like nextflow.config
    test.execute_notebooks(docs_folder, write=True)
