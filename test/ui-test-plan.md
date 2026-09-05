# Tally UI test plan

Manual-style tests of Tally's console behaviour, run automatically.

> **Attribution:** this plan was written by Claude (AI). The AI chose the test
> cases and wrote their expected outputs, deriving them from the increment
> specifications on the course website rather than from the program's output.

## How to run

```bash
python3 test/run-ui-tests.py           # every case
python3 test/run-ui-tests.py TC-03     # one case
```

The runner builds the project through Gradle, then replays each case's commands
through the program and compares what was printed against the expected output
below. Building the same way the real build does means the cases run against
the classes and dependencies that actually ship. It prints the whole session as it goes, and stops at the first
failure, reporting the expected output, the actual output, and a diff.

## How to write a case

Each case needs a `## TC-nn - Title` heading, an `**Aim:**` line, an `**Input**`
fenced block, and an `**Expected output**` fenced block. Input is one command per
line, exactly as the user would type it. Expected output is everything the
program prints, including the greeting banner.

Two things are ignored when comparing, so they cannot cause a false failure:
trailing spaces on a line, and blank lines at the very end of the output.

A case whose input does not end with `bye` tests what happens when the input
stream closes instead.

A case may add an optional `**Expected files after the run**` block, in which
each line reads `filename >>> one line that file should hold`. That checks what
was left on disk, not just what was printed. Console output alone would miss a
change that says the right thing and then destroys the data.

A case may add an optional `**Given the data file**` block before the input, to
write that content into the data file before the run. That is how a damaged file
is tested.

A case may add an optional `**Then restart and type**` block between the input
and the expected output. The runner then runs the program a second time against
the same data file, and the expected output covers both runs one after the
other. That is how the saved tally is checked.

Every case gets a data file of its own, deleted before the case runs, so no case
can inherit tasks another one saved.

## Coverage

These cases cover Level-0 through Level-9, and the `A-Classes`, `A-Inheritance`,
`A-Exceptions` and `A-Collections` extensions.

Cases that exercise a rejected command also issue a good command afterwards and
list the tally at the end. Checking only the error message would miss a bad
command that printed the right complaint but still altered the stored tasks.

---

## TC-01 - Greet and exit

**Aim:** Tally greets the user on startup and says goodbye when told `bye`, which is Level-0's requirement.

**Input**
```text
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-02 - Exit when the input stream closes

**Aim:** Closing the input without typing `bye` ends the conversation cleanly rather than throwing, so piping a file into Tally does not crash it.

**Input**
```text
todo read book
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] read book
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-03 - List an empty tally

**Aim:** Asking to list before adding anything says so in words, rather than printing an empty block that reads as a fault.

**Input**
```text
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Nothing on your tally yet.
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-04 - Add each task type and list them

**Aim:** `todo`, `deadline` and `event` each build the right task type, and the listing tags them `[T]`, `[D]` and `[E]` with their times, per Level-4.

**Input**
```text
todo read book
deadline return book /by 2019-06-06
event project meeting /from Aug 6th 2pm /to 4pm
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] read book
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[D][ ] return book (by: Jun 06 2019)
Now you have 2 tasks in the list.
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[E][ ] project meeting (from: Aug 6th 2pm to: 4pm)
Now you have 3 tasks in the list.
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[T][ ] read book
2.[D][ ] return book (by: Jun 06 2019)
3.[E][ ] project meeting (from: Aug 6th 2pm to: 4pm)
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-05 - Mark and unmark a task

**Aim:** `mark` fills a task's checkbox and `unmark` clears it again, and the change survives to the next listing, which is Level-3's requirement.

**Input**
```text
todo read book
todo return book
mark 2
list
unmark 2
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] read book
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] return book
Now you have 2 tasks in the list.
____________________________________________________________

____________________________________________________________
Nice! I've marked this task as done:
[T][X] return book
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[T][ ] read book
2.[T][X] return book
____________________________________________________________

____________________________________________________________
OK, I've marked this task as not done yet:
[T][ ] return book
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[T][ ] read book
2.[T][ ] return book
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-06 - Task count wording

**Aim:** The count after adding reads "1 task" for one task and "2 tasks" for more, so the confirmation is grammatical at every size.

**Input**
```text
todo first
todo second
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] first
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] second
Now you have 2 tasks in the list.
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-07 - A date that cannot be read is refused

**Aim:** A deadline whose `/by` is not a yyyy-mm-dd date is rejected with a message naming what was typed and the form to use, rather than being stored as loose text. Level-8 replaced the old behaviour of keeping whatever was typed. A good deadline afterwards shows the rejected one left nothing behind.

**Input**
```text
deadline do homework /by no idea :-p
deadline do homework /by 2019-10-15
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
I could not read "no idea :-p" as a date. Write it as yyyy-mm-dd, for example 2019-10-15.
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[D][ ] do homework (by: Oct 15 2019)
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[D][ ] do homework (by: Oct 15 2019)
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-08 - An unrecognized command is rejected

**Aim:** Text that is not a known command is refused with an explanation rather than being stored, which is the first of the two errors Level-5 requires. Interleaving a good command afterwards shows the rejected line left no trace on the tally.

**Input**
```text
blah
todo read book
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
I don't know that one. I understand: todo, deadline, event, window, list, mark, unmark, delete, find, free, bye.
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] read book
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[T][ ] read book
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-09 - A todo with no description is rejected

**Aim:** `todo` with nothing after it is refused with a message naming the fix, which is the second of the two errors Level-5 requires. The tally stays empty afterwards, proving the bad command added nothing.

**Input**
```text
todo
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
A todo needs a description. Try: todo read book
____________________________________________________________

____________________________________________________________
Nothing on your tally yet.
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-10 - A deadline or event missing its times is rejected

**Aim:** `deadline` without `/by`, and `event` without `/to`, are refused rather than throwing. A good deadline afterwards confirms the rejected lines did not disturb the tally.

**Input**
```text
deadline return book
event project meeting /from Mon 2pm
deadline return book /by 2019-10-15
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
A deadline needs a description and a /by date. Try: deadline return book /by 2019-10-15
____________________________________________________________

____________________________________________________________
An event needs a description, a /from time and a /to time, in that order. Try: event project meeting /from Mon 2pm /to 4pm
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[D][ ] return book (by: Oct 15 2019)
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[D][ ] return book (by: Oct 15 2019)
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-11 - A bad task number is rejected

**Aim:** `mark` with no number, with text instead of a number, and with a number past the end of the list are each refused with their own explanation. The task marked in between shows the tally still works, and the final listing shows the rejected commands changed nothing.

**Input**
```text
todo read book
mark
mark abc
mark 4
mark 1
mark 0
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] read book
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
mark needs the number of a task. Try: mark 2
____________________________________________________________

____________________________________________________________
mark needs the number of a task. Try: mark 2
____________________________________________________________

____________________________________________________________
There is no task 4 on your tally. Type list to see what is there.
____________________________________________________________

____________________________________________________________
Nice! I've marked this task as done:
[T][X] read book
____________________________________________________________

____________________________________________________________
There is no task 0 on your tally. Type list to see what is there.
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[T][X] read book
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-12 - Delete a task

**Aim:** `delete` removes the named task, reports which one went, and the remaining tasks are renumbered so the positions stay contiguous. Deleting the middle of three proves later tasks shift up rather than leaving a gap.

**Input**
```text
todo read book
todo return book
todo borrow book
delete 2
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] read book
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] return book
Now you have 2 tasks in the list.
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] borrow book
Now you have 3 tasks in the list.
____________________________________________________________

____________________________________________________________
Noted. I've removed this task:
[T][ ] return book
Now you have 2 tasks in the list.
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[T][ ] read book
2.[T][ ] borrow book
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-13 - Deleting the last task empties the tally

**Aim:** Removing the only task leaves the tally empty rather than in a broken state, and the count reads "0 tasks". Listing afterwards falls back to the empty-tally message, and marking into the empty tally is refused.

**Input**
```text
todo read book
delete 1
list
mark 1
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] read book
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Noted. I've removed this task:
[T][ ] read book
Now you have 0 tasks in the list.
____________________________________________________________

____________________________________________________________
Nothing on your tally yet.
____________________________________________________________

____________________________________________________________
There is no task 1 on your tally. Type list to see what is there.
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-14 - A bad delete number is rejected

**Aim:** `delete` with no number and with a number past the end are refused, and the error names `delete` rather than another command. A good delete afterwards shows the tally was untouched by the rejected attempts.

**Input**
```text
todo read book
delete
delete 5
delete 1
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] read book
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
delete needs the number of a task. Try: delete 2
____________________________________________________________

____________________________________________________________
There is no task 5 on your tally. Type list to see what is there.
____________________________________________________________

____________________________________________________________
Noted. I've removed this task:
[T][ ] read book
Now you have 0 tasks in the list.
____________________________________________________________

____________________________________________________________
Nothing on your tally yet.
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-15 - An event with /to before /from is rejected

**Aim:** Writing `/to` before `/from` is refused rather than recorded with the start and end swapped. Regression test: this input previously produced `(from: 4pm to: 2pm)` silently. The correctly ordered event afterwards shows the markers still work when written the right way round.

**Input**
```text
event meeting /to 4pm /from 2pm
event meeting /from 2pm /to 4pm
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
An event needs a description, a /from time and a /to time, in that order. Try: event project meeting /from Mon 2pm /to 4pm
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[E][ ] meeting (from: 2pm to: 4pm)
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[E][ ] meeting (from: 2pm to: 4pm)
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-16 - The tally survives a restart

**Aim:** Tasks and their done state are written to disk as they change and read back when Tally next starts, which is Level-7's requirement. Marking before the restart shows the checkbox state is saved too, not just the descriptions.

**Input**
```text
todo read book
deadline return book /by 2019-06-06
mark 1
bye
```

**Then restart and type**
```text
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] read book
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[D][ ] return book (by: Jun 06 2019)
Now you have 2 tasks in the list.
____________________________________________________________

____________________________________________________________
Nice! I've marked this task as done:
[T][X] read book
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________

____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[T][X] read book
2.[D][ ] return book (by: Jun 06 2019)
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-17 - A deletion survives a restart

**Aim:** The file is rewritten when a task is removed, not only when one is added, so a deleted task does not come back on the next start.

**Input**
```text
todo read book
todo return book
delete 1
bye
```

**Then restart and type**
```text
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] read book
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] return book
Now you have 2 tasks in the list.
____________________________________________________________

____________________________________________________________
Noted. I've removed this task:
[T][ ] read book
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________

____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[T][ ] return book
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-18 - A damaged data file is reported, not obeyed

**Aim:** A line that is not in the saved format is skipped and named, while every line that reads is kept. Level-7's stretch goal. One unreadable line costing the user every other task is a worse answer than losing the one line, so long as the reply says which line went and where the original was kept.

**Given the data file**
```text
T | 1 | read book
this line is nonsense
D | 0 | return book | 2019-06-06
```

**Input**
```text
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
I could not read line 2 of tally.txt, so that task is not on your tally. I copied it to tally.txt.broken so you can repair it.
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[T][X] read book
2.[D][ ] return book (by: Jun 06 2019)
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-19 - A saved tally is read back and can be worked on

**Aim:** A well-formed data file written by an earlier run is loaded in full, with each type and its done state restored, and the loaded tasks can then be numbered and marked like any other. This is the read half of Level-7, tested without relying on Tally having written the file itself.

**Given the data file**
```text
T | 1 | read book
D | 0 | return book | 2019-06-06
E | 0 | project meeting | Aug 6th 2pm | 4pm
```

**Input**
```text
list
mark 2
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[T][X] read book
2.[D][ ] return book (by: Jun 06 2019)
3.[E][ ] project meeting (from: Aug 6th 2pm to: 4pm)
____________________________________________________________

____________________________________________________________
Nice! I've marked this task as done:
[D][X] return book (by: Jun 06 2019)
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[T][X] read book
2.[D][X] return book (by: Jun 06 2019)
3.[E][ ] project meeting (from: Aug 6th 2pm to: 4pm)
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-20 - A damaged file is moved aside, not destroyed

**Aim:** A file holding two unreadable lines is copied before anything can write over it, so the original survives for the user to repair while the tasks that did load stay on the tally. Regression test: Tally used to discard every task in the file, and before that destroyed the file outright when the first command saved over it. Typing a command here is the point, since that is what used to do the damage.

**Given the data file**
```text
T | 0 | precious task
GARBAGE LINE
D | 0 | another | Friday
```

**Input**
```text
todo start again
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
I could not read lines 2 and 3 of tally.txt, so those tasks are not on your tally. I copied it to tally.txt.broken so you can repair it.
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] start again
Now you have 2 tasks in the list.
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

**Expected files after the run**
```text
tally.txt >>> T | 0 | precious task
tally.txt >>> T | 0 | start again
tally.txt.broken >>> T | 0 | precious task
tally.txt.broken >>> GARBAGE LINE
tally.txt.broken >>> D | 0 | another | Friday
```

---

## TC-21 - A date is read, shown and stored in the right forms

**Aim:** Level-8 asks that a date be accepted in one format and printed in another. The command supplies `2019-06-06`, the listing shows `Jun 06 2019`, and the data file keeps `2019-06-06`. Storing the form LocalDate reads back is what lets the deadline survive a restart, so all three are checked together.

**Input**
```text
deadline return book /by 2019-06-06
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[D][ ] return book (by: Jun 06 2019)
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[D][ ] return book (by: Jun 06 2019)
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

**Expected files after the run**
```text
tally.txt >>> D | 0 | return book | 2019-06-06
```

---

## TC-22 - A data file holding an unreadable date is damage

**Aim:** A deadline in the data file whose date is not yyyy-mm-dd cannot be turned into a task, so that line is skipped and named and the file is copied aside, rather than the user being asked about a file they did not type. Here it is the only line, so the tally is empty afterwards. This is the file-side counterpart of TC-07.

**Given the data file**
```text
D | 0 | return book | last Tuesday
```

**Input**
```text
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
I could not read line 1 of tally.txt, so that task is not on your tally. I copied it to tally.txt.broken so you can repair it.
____________________________________________________________

____________________________________________________________
Nothing on your tally yet.
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

**Expected files after the run**
```text
tally.txt.broken >>> D | 0 | return book | last Tuesday
```

---

## TC-23 - Find tasks by a word in their description

**Aim:** `find` shows only the tasks whose description contains the word, keeping each task's number from the whole tally so the user can act on what they see. Matching ignores case. Level-9's requirement.

**Input**
```text
todo read book
deadline return book /by 2019-06-06
todo buy bread
mark 1
find book
find BOOK
mark 2
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] read book
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[D][ ] return book (by: Jun 06 2019)
Now you have 2 tasks in the list.
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] buy bread
Now you have 3 tasks in the list.
____________________________________________________________

____________________________________________________________
Nice! I've marked this task as done:
[T][X] read book
____________________________________________________________

____________________________________________________________
Here are the matching tasks in your list:
1.[T][X] read book
2.[D][ ] return book (by: Jun 06 2019)
____________________________________________________________

____________________________________________________________
Here are the matching tasks in your list:
1.[T][X] read book
2.[D][ ] return book (by: Jun 06 2019)
____________________________________________________________

____________________________________________________________
Nice! I've marked this task as done:
[D][X] return book (by: Jun 06 2019)
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[T][X] read book
2.[D][X] return book (by: Jun 06 2019)
3.[T][ ] buy bread
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-24 - Find with nothing to show or nothing to look for

**Aim:** A search matching no task says so rather than printing an empty block, and `find` with no word is refused like any other command missing its argument. A good search afterwards shows neither disturbed the tally.

**Input**
```text
todo read book
find umbrella
find
find book
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] read book
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Nothing on your tally matches that.
____________________________________________________________

____________________________________________________________
find needs something to look for. Try: find book
____________________________________________________________

____________________________________________________________
Here are the matching tasks in your list:
1.[T][ ] read book
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

## TC-25 - A window task is added, saved and read back

**Aim:** `window` records a task that may be done on any day of a period, shows both ends in the display format Level-8 asks for, and writes them in the yyyy-mm-dd form it reads back, so the task survives a restart.

**Input**
```text
window submit form /between 2026-09-08 /and 2026-09-12
list
bye
```

**Then restart and type**
```text
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[W][ ] submit form (window: Sep 08 2026 to Sep 12 2026)
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[W][ ] submit form (window: Sep 08 2026 to Sep 12 2026)
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________

____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[W][ ] submit form (window: Sep 08 2026 to Sep 12 2026)
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

**Expected files after the run**
```text
tally.txt >>> W | 0 | submit form | 2026-09-08 | 2026-09-12
```

## TC-26 - A window that ends before it starts is refused

**Aim:** the two ends are read as dates rather than kept as text so that a period running backwards can be refused, naming both ends. Nothing reaches the tally.

**Input**
```text
window submit form /between 2026-09-12 /and 2026-09-08
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
A window cannot end before it starts, and this one ends 2026-09-08 but starts 2026-09-12.
____________________________________________________________

____________________________________________________________
Nothing on your tally yet.
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

## TC-27 - A malformed window command is refused

**Aim:** a missing `/and`, a missing description and an unreadable date are each refused with their own explanation. The date complaint names the format rather than a command, since deadlines and windows both use it.

**Input**
```text
window submit form /between 2026-09-08
window /between 2026-09-08 /and 2026-09-12
window submit form /between soon /and 2026-09-12
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
A window needs a description, a /between date and an /and date, in that order. Try: window submit form /between 2026-09-08 /and 2026-09-12
____________________________________________________________

____________________________________________________________
A window needs a description, a /between date and an /and date, in that order. Try: window submit form /between 2026-09-08 /and 2026-09-12
____________________________________________________________

____________________________________________________________
I could not read "soon" as a date. Write it as yyyy-mm-dd, for example 2019-10-15.
____________________________________________________________

____________________________________________________________
Nothing on your tally yet.
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

## TC-28 - The next free day, and the next run of free days

**Aim:** `free` reports the earliest day nothing takes up, and `/for` the earliest run of that many in a row. A deadline takes up its own day and a window every day of its period, so the run has to clear both. `/from` fixes the day the search starts, which is what makes the answer checkable.

**Input**
```text
deadline report /by 2026-09-10
window certificate /between 2026-09-12 /and 2026-09-14
free /from 2026-09-09
free /for 3 /from 2026-09-09
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[D][ ] report (by: Sep 10 2026)
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[W][ ] certificate (window: Sep 12 2026 to Sep 14 2026)
Now you have 2 tasks in the list.
____________________________________________________________

____________________________________________________________
The next free day is Sep 09 2026.
____________________________________________________________

____________________________________________________________
The next 3 free days in a row start Sep 15 2026.
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

## TC-29 - A free search says when it saw less than the whole tally

**Aim:** an event whose ends are text names no day, so it cannot be counted; the reply says so rather than implying the day is certainly free. A run shorter than a day, a count that is not a number and an unreadable date are each refused.

**Input**
```text
event standup /from Mon 2pm /to 3pm
free /from 2026-09-09
free /for 0
free /for abc
free /from nope
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[E][ ] standup (from: Mon 2pm to: 3pm)
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
The next free day is Sep 09 2026.
Events whose times are not dates were not counted.
____________________________________________________________

____________________________________________________________
A free stretch has to be at least one day long.
____________________________________________________________

____________________________________________________________
free takes an optional /for count and an optional /from date. Try: free /for 3 /from 2026-09-08
____________________________________________________________

____________________________________________________________
I could not read "nope" as a date. Write it as yyyy-mm-dd, for example 2019-10-15.
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

## TC-30 - Text the data file could not carry back is refused

**Aim:** the data file separates a task's parts with ` | ` and has no way to escape it, so a description holding a bar would be written out and read back as a different number of parts. The whole character is refused, not just the separator as written: the file joins parts with a space either side, so a description merely ending in a bar builds the separator on being saved. Refusing it where it is typed is what keeps every task readable back. A date outside the yyyy-mm-dd form is refused for the same reason: the wider forms parse, then overflow the date arithmetic done later.

**Input**
```text
todo laundry | dry cleaning
event meet /from Mon 2pm | room 3 /to 4pm
deadline pay /by +999999999-12-31
todo laundry and dry cleaning
list
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
A task cannot contain "|", because that is how the file Tally keeps your tally in separates one part from the next.
____________________________________________________________

____________________________________________________________
A task cannot contain "|", because that is how the file Tally keeps your tally in separates one part from the next.
____________________________________________________________

____________________________________________________________
I could not read "+999999999-12-31" as a date. Write it as yyyy-mm-dd, for example 2019-10-15.
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[T][ ] laundry and dry cleaning
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[T][ ] laundry and dry cleaning
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

**Expected files after the run**
```text
tally.txt >>> T | 0 | laundry and dry cleaning
```

## TC-31 - free reads its markers as whole words, in either order

**Aim:** the markers are matched as complete words rather than as the start of one, so "/forgotten" is not read as "/for" and the date after "/from" no longer swallows the rest of the line and gets complained about as a date. Both markers are optional and carry their own meaning, so either order is accepted, while giving one twice is refused.

**Input**
```text
free /from 2026-09-08 /for 3
free /for 3 /from 2026-09-08
free /forgotten
free /forbidden 3
free /for 3 /for 4
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
The next 3 free days in a row start Sep 08 2026.
____________________________________________________________

____________________________________________________________
The next 3 free days in a row start Sep 08 2026.
____________________________________________________________

____________________________________________________________
free takes an optional /for count and an optional /from date. Try: free /for 3 /from 2026-09-08
____________________________________________________________

____________________________________________________________
free takes an optional /for count and an optional /from date. Try: free /for 3 /from 2026-09-08
____________________________________________________________

____________________________________________________________
free takes an optional /for count and an optional /from date. Try: free /for 3 /from 2026-09-08
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

## TC-32 - A window with two faults is told about the one it can be sure of

**Aim:** a description holding a bar cannot be written back whatever else is wrong with the command, so it is reported before the dates are read. This matches `deadline`, which already refused the bar first, and means the reply does not depend on which fault the parser happened to reach first.

**Input**
```text
window notes|draft /between soon /and 2026-09-12
window notes|draft /between 2026-09-12 /and 2026-09-08
window notes /between 2026-09-12 /and 2026-09-08
bye
```

**Expected output**
```text
____________________________________________________________
 _____     _ _
|_   _|_ _| | |_   _
  | |/ _` | | | | | |
  | | (_| | | | |_| |
  |_|\__,_|_|_|\__, |
               |___/
Hello! I'm Tally.
What can I do for you?
____________________________________________________________

____________________________________________________________
A task cannot contain "|", because that is how the file Tally keeps your tally in separates one part from the next.
____________________________________________________________

____________________________________________________________
A task cannot contain "|", because that is how the file Tally keeps your tally in separates one part from the next.
____________________________________________________________

____________________________________________________________
A window cannot end before it starts, and this one ends 2026-09-08 but starts 2026-09-12.
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```
