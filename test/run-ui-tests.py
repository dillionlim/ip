#!/usr/bin/env python3
"""Run Tally's UI test cases and check its output against the expected output.

The test cases live in test/ui-test-plan.md.  Each case supplies a list of
commands to type and the console output those commands should produce.  This
script compiles the program once, replays each case through it, and compares
what came back.

The whole session is printed as it runs so the transcript can be read
afterwards.  The first failure stops the run and reports both outputs, since a
later case is rarely meaningful once an earlier one is broken.

Usage:
    python3 test/run-ui-tests.py           # run every case
    python3 test/run-ui-tests.py TC-03     # run only the named case(s)

Attribution: written by Claude (AI), to requirements I gave it from the sample
prompt in the CS2103T Week 2 project instructions. The plan format, the parser
and the reporting were the AI's design.
"""

import atexit
import difflib
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PLAN = ROOT / "test" / "ui-test-plan.md"
# Windows cannot run the shell script, and the other two cannot run the batch file.
GRADLEW = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
MAIN_CLASS = "tally.Tally"
TIMEOUT_SECONDS = 20

HEADING = re.compile(r"^##\s+(TC-\d+)\s*[-–—:]\s*(.+?)\s*$")
AIM = re.compile(r"^\*\*Aim:?\*\*:?\s*(.*)$")
SECTION = re.compile(r"^\*\*(Given the data file|Input|Then restart and type"
                     r"|Expected output|Expected files after the run):?\*\*\s*$")
SECTION_KEY = {"Given the data file": "seed", "Input": "input",
               "Then restart and type": "restart", "Expected output": "expected",
               "Expected files after the run": "files"}


class Crashed(Exception):
    """Raised when the program exits with a nonzero status, however it printed."""


def read_fenced_block(lines, index):
    """Returns the body of the next ``` block at or after index, and the line after it."""
    while index < len(lines) and not lines[index].startswith("```"):
        index += 1
    if index >= len(lines):
        raise ValueError("expected a fenced code block but reached the end of the plan")
    index += 1
    body = []
    while index < len(lines) and not lines[index].startswith("```"):
        body.append(lines[index])
        index += 1
    return "\n".join(body), index + 1


def parse_plan(text):
    """Returns the test cases described in the plan, in the order they appear."""
    cases = []
    current = None
    lines = text.splitlines()
    index = 0
    while index < len(lines):
        line = lines[index]
        heading = HEADING.match(line)
        if heading:
            current = {"id": heading.group(1), "title": heading.group(2),
                       "aim": "", "seed": None, "input": None,
                       "restart": None, "expected": None, "files": None}
            cases.append(current)
            index += 1
            continue
        if current is not None:
            aim = AIM.match(line)
            if aim:
                current["aim"] = aim.group(1).strip()
                index += 1
                continue
            section = SECTION.match(line)
            if section:
                key = SECTION_KEY[section.group(1)]
                current[key], index = read_fenced_block(lines, index + 1)
                continue
        index += 1
    for case in cases:
        if case["input"] is None or case["expected"] is None:
            raise ValueError(f"{case['id']} is missing an Input or Expected output block")
    return cases


def normalise(text):
    """Returns text with line endings, trailing spaces and trailing blank lines removed.

    Comparing this way keeps a stray blank line at the end of the console output
    from failing a case that is otherwise correct, because a Markdown fence
    cannot preserve one.
    """
    lines = [line.rstrip() for line in text.replace("\r\n", "\n").split("\n")]
    while lines and lines[-1] == "":
        lines.pop()
    return "\n".join(lines)


def build_program():
    """Builds the app through Gradle and returns the classpath to launch it with.

    Going through Gradle rather than calling javac here means the tests run
    against the same classes, and the same dependencies, that the real build
    produces. Once the app depends on anything external, a hand-written javac
    command would be compiling a different program from the one that ships.
    """
    result = subprocess.run([str(GRADLEW), "classes", "--console=plain", "-q"],
                            cwd=ROOT, capture_output=True, text=True)
    if result.returncode != 0:
        print(result.stdout + result.stderr, end="")
        sys.exit("the build failed, so no test case was run")

    result = subprocess.run([str(GRADLEW), "printRuntimeClasspath", "--console=plain", "-q"],
                            cwd=ROOT, capture_output=True, text=True)
    if result.returncode != 0:
        print(result.stdout + result.stderr, end="")
        sys.exit("could not work out the classpath, so no test case was run")
    lines = [line for line in result.stdout.splitlines() if line.strip()]
    if not lines:
        sys.exit("Gradle reported an empty classpath, so no test case was run")
    print("Built with Gradle.\n")
    return lines[-1].strip()


def invoke(commands, data_file, classpath):
    """Returns what one run of the program printed when fed these commands."""
    stdin_text = commands + "\n" if commands else ""
    # -ea so the assertions in the code are checked as the cases run; without it every
    # assert is skipped and these sessions would prove nothing about them.
    result = subprocess.run(["java", "-ea", "-cp", classpath, MAIN_CLASS, str(data_file)],
                            input=stdin_text, capture_output=True, text=True,
                            timeout=TIMEOUT_SECONDS)
    if result.stderr.strip():
        print(result.stderr, end="")
    if result.returncode != 0:
        # Expected output followed by a crash is not a pass. An uncaught exception or a
        # failed assertion leaves the words already printed intact, so comparing only
        # what was printed would call this case correct.
        raise Crashed(f"the program exited with status {result.returncode}")
    return result.stdout


def run_case(case, data_file, classpath):
    """Returns what the program printed across this case's one or two runs.

    Each case gets a data file of its own, removed beforehand, so no case can
    inherit tasks saved by an earlier one. A case with a restart block runs the
    program twice against that same file, which is what proves the tally
    survives between runs.
    """
    for stale in data_file.parent.glob("tally.txt*"):
        stale.unlink()
    if case["seed"] is not None:
        data_file.parent.mkdir(parents=True, exist_ok=True)
        data_file.write_text(case["seed"] + "\n", encoding="utf-8")
    output = invoke(case["input"], data_file, classpath)
    if case["restart"] is not None:
        output += invoke(case["restart"], data_file, classpath)
    return output


def check_files(case, data_file):
    """Returns a complaint about the files left behind, or None if they are right.

    Each line of the block names a file and one line it should hold, written as
    "name >>> line". Checking the files as well as the console catches a change
    that prints the right thing and then damages what is on disk.
    """
    wanted = {}
    for entry in case["files"].split("\n"):
        if not entry.strip():
            continue
        name, _, content = entry.partition(" >>> ")
        wanted.setdefault(name.strip(), []).append(content)
    for name, lines in wanted.items():
        path = data_file.parent / name
        if not path.exists():
            return f"{name} does not exist after the run"
        actual = [l for l in path.read_text(encoding="utf-8").split("\n") if l != ""]
        if actual != lines:
            return (f"{name} holds:\n    " + "\n    ".join(actual)
                    + "\n  but should hold:\n    " + "\n    ".join(lines))
    return None


def report_failure(case, expected, actual):
    print(f"\n{'=' * 70}\nFAILED: {case['id']} - {case['title']}\n{'=' * 70}")
    print(f"\nAim: {case['aim']}\n")
    print(f"--- Input ---\n{case['input']}\n")
    print(f"--- Expected output ---\n{expected}\n")
    print(f"--- Actual output ---\n{actual}\n")
    print("--- Difference (- expected, + actual) ---")
    diff = difflib.unified_diff(expected.split("\n"), actual.split("\n"),
                                fromfile="expected", tofile="actual", lineterm="")
    print("\n".join(diff))
    print(f"\nStopping: {case['id']} failed, so the remaining cases were not run.")


def main():
    wanted = {argument.upper() for argument in sys.argv[1:]}
    cases = parse_plan(PLAN.read_text(encoding="utf-8"))
    if wanted:
        cases = [case for case in cases if case["id"].upper() in wanted]
        if not cases:
            sys.exit(f"no test case in {PLAN.name} matches {', '.join(sorted(wanted))}")

    classpath = build_program()

    workspace = Path(tempfile.mkdtemp(prefix="tally-ui-tests-"))
    atexit.register(shutil.rmtree, workspace, ignore_errors=True)
    data_file = workspace / "tally.txt"

    for number, case in enumerate(cases, start=1):
        print(f"{'-' * 70}\n[{number}/{len(cases)}] {case['id']} - {case['title']}")
        print(f"Aim: {case['aim']}\n")
        if case["seed"] is not None:
            print("--- Data file before the run ---")
            print(case["seed"])
        print("--- Typed by the user ---")
        print(case["input"] if case["input"] else "(nothing; input closes immediately)")
        if case["restart"] is not None:
            print("--- Then, after restarting Tally ---")
            print(case["restart"])
        try:
            actual = run_case(case, data_file, classpath)
        except Crashed as crash:
            print(f"\n{'=' * 70}\nFAILED: {case['id']} - {case['title']}\n{'=' * 70}")
            print(f"\nAim: {case['aim']}\n\n  {crash}")
            print(f"\nStopping: {case['id']} failed, so the remaining cases were not run.")
            return 1
        print("\n--- Printed by Tally ---")
        print(actual, end="" if actual.endswith("\n") else "\n")

        expected_text = normalise(case["expected"])
        actual_text = normalise(actual)
        if expected_text != actual_text:
            report_failure(case, expected_text, actual_text)
            return 1
        if case["files"] is not None:
            complaint = check_files(case, data_file)
            print("--- Files left behind ---")
            for path in sorted(data_file.parent.iterdir()):
                print(f"  {path.name}: " + " / ".join(
                    l for l in path.read_text(encoding="utf-8").split("\n") if l))
            if complaint is not None:
                print(f"\n{'=' * 70}\nFAILED: {case['id']} - {case['title']}\n{'=' * 70}")
                print(f"\nAim: {case['aim']}\n\n  {complaint}")
                print(f"\nStopping: {case['id']} failed, so the remaining cases were not run.")
                return 1
        print(f"PASS: {case['id']}\n")

    print(f"{'-' * 70}\nAll {len(cases)} test case(s) passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
