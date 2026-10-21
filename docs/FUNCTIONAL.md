# What Guardia does

This describes the system as a support desk would describe it: what it is for, what it knows
about, the rules it applies, and what you should see when you use it. The engineering — why it
is split into three services and how they stay consistent — is in the [README](../README.md).

## The job

A customer reports a fault. Someone has to be sent, and the contract the customer signed says
how quickly. Guardia takes the report and answers one question: **is there an hour in a
suitable technician's diary soon enough to honour what was promised?**

If there is, the incident is assigned to that hour. If there is not, it is cancelled with the
reason, and nothing is left reserved. There is no middle state left lying around.

## What it knows about

**Incidents.** What is wrong, which customer reported it, how urgent, and what skill fixing it
needs. One incident, one answer.

**Technicians and their hours.** Each technician has one skill and a diary of one-hour slots.
A slot is free, held for one incident, or booked to it. Four technicians are seeded:

| Technician | Skill |
|---|---|
| Ane Mendizabal | `NETWORK` |
| Bruno Carvalho | `NETWORK` |
| Carla Otxoa | `DATABASE` |
| Diego Ferreiro | `HARDWARE` |

Each keeps **eight free hours** ahead of them. As hours are taken, later ones are added, so a
busy morning extends the diary into the afternoon rather than exhausting it — a desk always has
next week. The first free hour is at least an hour from now: a desk that could send someone
within ten minutes is not a desk that needs scheduling software.

Capacity can still run out for a moment. Nine incidents raised in the same few seconds will
outrun the top-up, which is exactly how a real queue behaves, and the diary recovers within a
minute.

**Contracts.** What each customer was promised, per urgency: the number of minutes from the
report until someone turns up.

| Customer | `CRITICAL` | `HIGH` | `NORMAL` |
|---|---|---|---|
| `acme` | 30 min | 4 h | 24 h |
| `globex` | 15 min | 1 h | 8 h |
| `initech` | — | — | — |

`initech` is seeded with no contract on purpose, so the "we have no agreement with you" path is
reachable from the page.

## The rules, in order

1. **Find a technician with the right skill.** Skills are matched exactly: a `NETWORK` fault is
   not given to the database specialist. If nobody has the skill, or everyone with it is fully
   booked, the incident is cancelled immediately.
2. **Take the earliest free hour.** Not the most convenient one — the soonest, because the
   contract is about speed. That hour is **held**, not booked: set aside provisionally while
   the rest of the question is settled.
3. **Check the promise.** The held hour is compared against the customer's contract for that
   urgency. If the hour falls within the promised response time, it is approved; if not, it is
   rejected and says by how much it missed.
4. **Confirm, or undo.** Approved, the hour is booked and the incident is assigned. Rejected,
   the hour is released — back to free, available to the next incident — and the incident is
   cancelled.
5. **Nothing waits for ever.** A hold that is never resolved expires on its own, the hour goes
   back, and the incident is cancelled rather than left open.

Two incidents never get the same hour, including two raised at the same instant.

## The states an incident passes through

| State | What it means |
|---|---|
| `RECEIVED` | Taken, and the search for an hour has begun. Nothing is reserved yet. |
| `SLOT_HELD` | An hour is set aside, and the contract is being checked. |
| `ASSIGNED` | Done. A technician has the appointment. |
| `CANCELLED` | Done, unfavourably, with a reason. Nothing is reserved. |

`ASSIGNED` and `CANCELLED` are final. An incident never goes back, and a late answer about an
incident already settled is ignored rather than applied.

Cancellation always says why, in words meant for a person: *"Earliest slot is 54 min past the
15 min response time"*, *"No technician with skill QUANTUM has a free slot"*, *"No contract
covers initech at priority HIGH"*.

## Using the page

<http://localhost:8081> after `docker compose up --wait`.

Three buttons raise a ready-made incident for each of the three endings. Below them, a form
raises any combination you like. The list underneath updates itself as the services work: you
do not reload it, and the states appear as they are decided.

The dot at the top reads **live** when the page is connected to the stream of changes.

### Things to try, and what should happen

| What to do | What you should see |
|---|---|
| Click **Gets assigned** (`acme`, `NORMAL`, `NETWORK`) | Appears as `received`, then within a second or two `assigned`, with the hour shown. `acme` has 24 hours at normal priority and the next free hour is well inside that. |
| Click **Gets cancelled** (`globex`, `CRITICAL`, `NETWORK`) | Briefly `received`, then `cancelled`, saying how many minutes past the 15-minute promise the earliest hour falls. An hour was held and given back in between — too quickly to see, but the next test proves it. |
| Click **Gets cancelled**, then **Gets assigned** | The assigned incident takes the very hour the cancelled one had held. That is the undo working: a rejection must not cost the desk an hour of capacity. |
| Click **No technician has the skill** (`QUANTUM`) | `cancelled` almost immediately, naming the skill. Nothing was held, because nothing could be. |
| Form: `acme`, `CRITICAL`, `NETWORK` | `cancelled`. The same customer that passes at normal priority fails at critical: 30 minutes cannot be met by an hour that is at least an hour away. The contract decides, not the customer. |
| Form: `globex`, `NORMAL`, `NETWORK` | `assigned`. The mirror of the last one — the customer with the tightest critical terms has eight hours at normal priority. |
| Form: `initech`, any priority | `cancelled`, saying no contract covers them. Note it is a clear answer, not an error: an unanswerable question still gets an answer. |
| Raise nine `DATABASE` incidents within a few seconds | The first eight are `assigned`, each to a different hour — only Carla has that skill and only eight of her hours are free at once. The ninth is `cancelled` for want of a slot, because the burst outran the top-up. |
| Wait a minute, then raise another `DATABASE` one | `assigned`. The diary extended itself rather than staying full, which is why clicking around the page for ten minutes does not leave everything cancelled. |
| Raise several `NETWORK` incidents | Each gets its own hour, never a shared one. Two technicians have that skill, so there are more hours to go round. |
| Open the page in two tabs and raise one in either | Both lists update. The page is showing what the system decided, not what that tab did. |
| Stop the `scheduling` service (`docker compose stop scheduling`), raise one, start it again | The incident sits at `received` while scheduling is down, then completes once it is back. Nothing is lost; the work was waiting, not dropped. |

The last one is the most worth doing. It is the difference between services that call each
other and services that leave each other messages.

## What this deliberately does not do

There is no sign-in: anyone who can reach the page can raise an incident, which is fine for
something whose purpose is to show how the services agree with each other, and the first thing
to say out loud if asked.

A cancelled incident is not retried. Someone has to raise a new one. Doing it properly means
deciding what a second attempt may do — a later hour, a different technician, override the
contract — and that is a conversation with the business, not a coding task.

Technicians have exactly one skill, diaries are one-hour slots with no travel time between
them, and nobody is ever ill. Each of those is a real scheduling problem that this does not
pretend to solve.
