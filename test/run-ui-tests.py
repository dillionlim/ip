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
# How wide the rules separating one case from the next are drawn.
BANNER_WIDTH = 70

HEADING = re.compile(r"^##\s+(TC-\d+)\s*[-–—:]\s*(.+?)\s*$")
AIM = re.compile(r"^\*\*Aim:?\*\*:?\s*(.*)$")
SECTION = re.compile(r"^\*\*(Given the data file|Input|Then restart and type"
                     r"|Expected output|Expected files after the run):?\*\*\s*$")
SECTION_KEY = {"Given the data file": "seed", "Input": "input",
               "Then restart and type": "restart", "Expected output": "expected",
               "Expected files after the run": "files"}


class Crashed(Exception):
    """Raised when a run cannot count as a pass, whatever it printed.

    Either it exited with a nonzero status, or it wrote to standard error
    while exiting cleanly.  Both mean something went wrong that comparing
    standard output would not show.
    """

    def __init__(self, reason, printed=""):
        super().__init__(reason)
        self.printed = printed


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


def new_case(heading):
    """Returns an empty case named by a matched heading line."""
    return {"id": heading.group(1), "title": heading.group(2),
            "aim": "", "seed": None, "input": None,
            "restart": None, "expected": None, "files": None}


def read_into_case(case, lines, index):
    """Reads whatever the line at index starts into the case, and returns the next index.

    Returns index + 1 when the line starts nothing, so the caller moves on.
    """
    aim = AIM.match(lines[index])
    if aim:
        case["aim"] = aim.group(1).strip()
        return index + 1
    section = SECTION.match(lines[index])
    if section:
        case[SECTION_KEY[section.group(1)]], after = read_fenced_block(lines, index + 1)
        return after
    return index + 1


def check_complete(cases):
    """Raises if any case is missing a block that every case must have."""
    for case in cases:
        if case["input"] is None or case["expected"] is None:
            raise ValueError(f"{case['id']} is missing an Input or Expected output block")


def parse_plan(text):
    """Returns the test cases described in the plan, in the order they appear."""
    cases = []
    lines = text.splitlines()
    index = 0
    while index < len(lines):
        heading = HEADING.match(lines[index])
        if heading:
            cases.append(new_case(heading))
            index += 1
        elif cases:
            index = read_into_case(cases[-1], lines, index)
        else:
            index += 1
    check_complete(cases)
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
    """Returns what one run of the program printed when fed these commands.

    The program is started in the data file's own folder and given the bare file
    name, which is the shape a user naturally types for a file beside them.  A
    path written that way names no parent directory of its own, and saving to
    one used to crash; running every case this way keeps that from coming back.
    """
    stdin_text = commands + "\n" if commands else ""
    # -ea so the assertions in the code are checked as the cases run; without it every
    # assert is skipped and these sessions would prove nothing about them.
    result = subprocess.run(["java", "-ea", "-cp", classpath, MAIN_CLASS, data_file.name],
                            cwd=data_file.parent,
                            input=stdin_text, capture_output=True, text=True,
                            timeout=TIMEOUT_SECONDS)
    noise = result.stderr.strip()
    if result.returncode != 0:
        # Expected output followed by a crash is not a pass. An uncaught exception or a
        # failed assertion leaves the words already printed intact, so comparing only
        # what was printed would call this case correct.
        crash = f"the program exited with status {result.returncode}"
        raise Crashed(f"{crash}:\n{noise}" if noise else crash, result.stdout)
    if noise:
        # A passing run says nothing here, so anything on it is a warning or a trace the
        # program carried on through, which a comparison of stdout alone would not see.
        raise Crashed(f"the program wrote to standard error:\n{noise}", result.stdout)
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

    The block has to name every file the run leaves behind, so that a half
    written file nobody cleaned up is a failure rather than something the check
    passes over in silence.
    """
    wanted = {}
    for entry in case["files"].split("\n"):
        if not entry.strip():
            continue
        name, _, content = entry.partition(" >>> ")
        wanted.setdefault(name.strip(), []).append(content)

    left = sorted(path.name for path in data_file.parent.iterdir())
    unexpected = [name for name in left if name not in wanted]
    if unexpected:
        return "the run left files the case does not account for: " + ", ".join(unexpected)

    for name, lines in wanted.items():
        path = data_file.parent / name
        if not path.exists():
            return f"{name} does not exist after the run"
        # splitlines rather than dropping every blank line, so that a stray blank
        # in the middle of the file is a difference like any other.
        actual = path.read_text(encoding="utf-8").splitlines()
        if actual != lines:
            return (f"{name} holds:\n    " + "\n    ".join(actual)
                    + "\n  but should hold:\n    " + "\n    ".join(lines))
    return None


def announce(case, number, total):
    """Prints what the case is about and what it is about to type."""
    print(f"{'-' * BANNER_WIDTH}\n[{number}/{total}] {case['id']} - {case['title']}")
    print(f"Aim: {case['aim']}\n")
    if case["seed"] is not None:
        print("--- Data file before the run ---")
        print(case["seed"])
    print("--- Typed by the user ---")
    print(case["input"] if case["input"] else "(nothing; input closes immediately)")
    if case["restart"] is not None:
        print("--- Then, after restarting Tally ---")
        print(case["restart"])


def report_complaint(case, complaint):
    """Prints why a case failed, for a failure that is a sentence rather than a diff."""
    print(f"\n{'=' * BANNER_WIDTH}\nFAILED: {case['id']} - {case['title']}\n{'=' * BANNER_WIDTH}")
    print(f"\nAim: {case['aim']}\n\n  {complaint}")
    print(f"\nStopping: {case['id']} failed, so the remaining cases were not run.")


def report_difference(case, expected, actual):
    """Prints both outputs and a diff, for a case whose output was not what it should be."""
    print(f"\n{'=' * BANNER_WIDTH}\nFAILED: {case['id']} - {case['title']}\n{'=' * BANNER_WIDTH}")
    print(f"\nAim: {case['aim']}\n")
    print(f"--- Input ---\n{case['input']}\n")
    print(f"--- Expected output ---\n{expected}\n")
    print(f"--- Actual output ---\n{actual}\n")
    print("--- Difference (- expected, + actual) ---")
    diff = difflib.unified_diff(expected.split("\n"), actual.split("\n"),
                                fromfile="expected", tofile="actual", lineterm="")
    print("\n".join(diff))
    print(f"\nStopping: {case['id']} failed, so the remaining cases were not run.")


def show_files_left(data_file):
    """Prints every file the run left in the workspace, and the lines it holds."""
    print("--- Files left behind ---")
    for path in sorted(data_file.parent.iterdir()):
        print(f"  {path.name}: " + " / ".join(
            l for l in path.read_text(encoding="utf-8").split("\n") if l))


def run_and_check(case, data_file, classpath):
    """Runs one case, reports it if it failed, and returns whether it passed."""
    try:
        actual = run_case(case, data_file, classpath)
    except Crashed as crash:
        if crash.printed.strip():
            print("\n--- Printed before it stopped ---")
            print(crash.printed, end="" if crash.printed.endswith("\n") else "\n")
        report_complaint(case, crash)
        return False
    print("\n--- Printed by Tally ---")
    print(actual, end="" if actual.endswith("\n") else "\n")

    expected_text = normalise(case["expected"])
    actual_text = normalise(actual)
    if expected_text != actual_text:
        report_difference(case, expected_text, actual_text)
        return False
    if case["files"] is not None:
        complaint = check_files(case, data_file)
        show_files_left(data_file)
        if complaint is not None:
            report_complaint(case, complaint)
            return False
    return True


def select_cases():
    """Returns the cases named on the command line, or every case if none were."""
    wanted = {argument.upper() for argument in sys.argv[1:]}
    cases = parse_plan(PLAN.read_text(encoding="utf-8"))
    if not wanted:
        return cases
    chosen = [case for case in cases if case["id"].upper() in wanted]
    if not chosen:
        sys.exit(f"no test case in {PLAN.name} matches {', '.join(sorted(wanted))}")
    return chosen


def main():
    cases = select_cases()
    classpath = build_program()

    workspace = Path(tempfile.mkdtemp(prefix="tally-ui-tests-"))
    atexit.register(shutil.rmtree, workspace, ignore_errors=True)
    data_file = workspace / "tally.txt"

    for number, case in enumerate(cases, start=1):
        announce(case, number, len(cases))
        if not run_and_check(case, data_file, classpath):
            return 1
        print(f"PASS: {case['id']}\n")

    print(f"{'-' * BANNER_WIDTH}\nAll {len(cases)} test case(s) passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
