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

The runner compiles `src/main/java/*.java` into `bin/`, then replays each case's
commands through the program and compares what was printed against the expected
output below. It prints the whole session as it goes, and stops at the first
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

## Coverage

These cases cover Level-0 through Level-6, and the `A-Classes`, `A-Inheritance`,
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
deadline return book /by June 6th
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
[D][ ] return book (by: June 6th)
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
2.[D][ ] return book (by: June 6th)
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

## TC-07 - Times are kept as typed

**Aim:** Level-4 states that dates and times need not be parsed yet, so whatever the user types after `/by` is stored and echoed back unchanged, punctuation included.

**Input**
```text
deadline do homework /by no idea :-p
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
[D][ ] do homework (by: no idea :-p)
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Bye. Hope to see you again soon!
____________________________________________________________
```

---

## TC-08 - An unrecognised command is rejected

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
I don't know that one. I understand: todo, deadline, event, list, mark, unmark, delete, bye.
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
deadline return book /by Sunday
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
A deadline needs a description and a /by time. Try: deadline return book /by Sunday
____________________________________________________________

____________________________________________________________
An event needs a description, a /from time and a /to time. Try: event project meeting /from Mon 2pm /to 4pm
____________________________________________________________

____________________________________________________________
Got it. I've added this task:
[D][ ] return book (by: Sunday)
Now you have 1 task in the list.
____________________________________________________________

____________________________________________________________
Here are the tasks in your list:
1.[D][ ] return book (by: Sunday)
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
