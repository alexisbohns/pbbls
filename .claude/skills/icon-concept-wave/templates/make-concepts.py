import json
SQUADS = {
 "s1-delivery": ("Delivery & record", None, """
delivery|work handed over and arrived in users' hands
shipment|a parcel on its way: something packed and sent out
release|a numbered version going out to users (not the status chip, the act and the version)
beta|pre-release testing with a first circle of early users
test|trying something to see if it works: a check, an experiment
changelog|the written list of what changed between versions
backlog|the queue of work waiting to be picked up
flow|something moving smoothly through steps without blockage
history|past events in order: looking back through time
journal|a personal diary kept day after day
log|an append-only, chronological record of events
ledger|a book of accounts: entries kept in ruled columns
record|one entry kept on file: a fact written down (not a recording button)
inventory|an itemised stock of what exists, counted and listed
commitment|a promise to follow through: a pledge
decision|a choice made at a fork, taken and recorded
"""),
 "s2-system": ("System & code", None, """
data|raw information held: records, values, numbers
database|where data is stored and structured: tables, rows
server|a machine in the back that answers requests
api|the contract through which two programs talk: endpoints, requests and answers
system|many parts working together as one whole
architecture|how the whole is structured: the plan of the building
integration|parts joined so they work with each other: a plug, a connection between services
agentic|an AI agent acting on its own, on your behalf, toward a goal
git|version control: the tool and its tree of changes
branch|a line of work diverging from the main line (git)
fork|a copy taken to go your own way (git fork); also a split in the road
merge|two lines of work coming together into one
node|a point in a graph: a vertex
graph|nodes connected by edges: a network
edge|the link between two nodes
node-add|add a node to the graph (the action)
node-new|a node that is newly created: fresh, just appeared (a state, distinct from node-add)
node-remove|remove a node from the graph (the action)
"""),
 "s3-surface": ("Surface & interface", None, """
journey|a path travelled across steps and time, with a start and an arrival
overview|the whole picture at a glance: a bird's-eye summary
view|one way of looking at the content: a lens on the data
page|a single screen or document of the app or site
screens|the set of an app's screens seen together
layout|how things are arranged on a surface
pane|one panel of a split window
matrix|a grid of rows and columns crossing two dimensions
tooltip|a small floating hint that explains an element
enabled|switched on, available to use
disabled|present but unavailable: cannot be used
expand|open up to show more: unfold, bigger
collapse|fold up to hide detail: smaller
inform|tell, notify, make known: an information notice
affordance|a shape that shows what you can do with it: a handle, a button that looks pressable
"""),
 "s4-quality": ("Quality & verification", None, """
quality|a high standard met: well made, checked, trustworthy
parity|the same thing on every platform: iOS, Android and web in step
code-quality|clean, readable, well-structured code
verification|checking that something is true or correct
quality-assurance|the process of checking quality before release: testing plus sign-off
acceptances|the conditions a piece of work must meet to be accepted, as a checklist
criterion|one standard a thing is judged against: a single check
finding|something discovered during an audit: a reported issue
resolved|an issue that has been dealt with and closed
solved|a problem solved: the answer found
fixed|something broken, now mended
performance|speed and responsiveness: how fast it runs
efficiency|the most result for the least waste
reliability|can be counted on: works every time, stays up
observability|seeing inside a running system through its signals (logs, metrics, traces)
monitoring|watching something continuously, ready for a signal to go off
"""),
 "s5-status": ("Status & priority", "FAMILY SETS. These 20 concepts are two families used as chips and badges on work items: 14 statuses (status-*) and 6 priorities (priority, priority-*). Within ONE idea (classical, bold, pebbles or weird) draw the whole family in ONE shared vocabulary (a common container or gesture, the meaning carried by what changes inside it), so the set reads as a set. Between ideas the vocabularies differ. status-superseded (this one is the old one, replaced) and status-supersede (this one takes over) are two sides of the same relation: draw them as a pair. The priorities form a scale: critical > high > medium > low, and undefined is 'not ranked yet'; the scale must be readable without a label, at 20 px.", """
status-idea|a spark, just an idea, not yet planned
status-backlog|accepted and waiting in the queue
status-development|being built right now
status-release|built and on its way out: being shipped, awaiting review
status-blocked|stuck, cannot move on until something is solved
status-live|in production, in users' hands, running
status-archived|put away, kept but no longer active
status-deprecated|still there but on its way out: do not use any more
status-warning|a caution state: attention needed
status-superseded|replaced by something newer: this is the old one
status-supersede|the act of replacing: this one takes the place of an older one
status-approved|accepted, validated, signed off
status-rejected|refused, turned down
status-draft|a work in progress, not final, unpublished
priority|what comes first: ranking by importance (the general concept)
priority-critical|top of the scale: drop everything
priority-high|important, soon
priority-medium|middle of the scale: normal
priority-low|bottom of the scale: when there is time
priority-undefined|not ranked yet: priority unknown
"""),
 "s6-trust": ("Safety, trust & care", None, """
security|protection against attackers
privacy|what is kept to oneself, shielded from others' eyes
data-protection|the care taken of personal data and the rights of its owner
gdpr|the European data-protection regulation, a legal framework (do not copy the EU flag or its ring of stars)
safety|being kept from harm: a safe place, a net under you
store-compliance|meeting the rules of the app stores to be allowed in
risk|something that might go wrong: a chance of harm
health|the vitals of a person or a system: is it well
diagnosis|identifying what is wrong by examining signs
doctor|the practitioner who cares for health (no face, no cross: a stethoscope-level reading)
therapeutic|that which heals or soothes: a care practice
wellness|being well in body and mind: balance, calm, vitality
accessibility|usable by everyone, whatever their abilities
inclusion|everyone has a place: welcoming difference
anxiety|worry, tension: a mind racing, a tight chest
avoid-hassles|no trouble: friction removed, a smooth path
"""),
 "s7-value": ("Experience & appeal", None, """
simplify|make less complex: fewer parts, stripped down
organize|put things in order: sort, group, tidy
reduce-effort|less work needed: lighter, easier, a shortcut
variety|many different kinds: a mix, a range
sensorial|felt through the senses: touch, sound, sight, smell, taste
aesthetics|beauty: form pleasing to the eye, taste
attractiveness|what draws you in: appeal, charm
fun|playful joy, lightness, a smile
entertainment|something that holds attention and amuses: a show, play
reward|a prize for effort
badge-value|the worth of an earned badge or achievement
recognition|being seen and acknowledged for what you did
"""),
 "s8-meaning": ("Meaning & belonging", None, """
belonging|being part of a group, having a place in it
community|a group of people together, sharing something
hope|expecting good to come: a light ahead
nostalgia|longing for the past: a memory warmed by time
heirloom|an object passed down through generations
legacy|what you leave behind for those who come after
debt|something owed: the weight of an obligation
transcendence|going beyond the ordinary: rising above
money|currency, cost, what things are worth
saving|putting aside for later: thrift, keeping
success|a goal reached: achievement
self-actualization|becoming the fullest version of yourself: growth toward your potential
motivation|the drive that makes you start and keep going
"""),
}
rows, squads = [], {}
for sq, (title, note, body) in SQUADS.items():
    items = [l.split("|", 1) for l in body.strip().splitlines()]
    squads[sq] = {"title": title, "note": note, "count": len(items)}
    for i, (n, m) in enumerate(items):
        rows.append({"name": n, "meaning": m, "squad": sq, "batch": f"{i // 8 + 1:02d}"})
names = [r["name"] for r in rows]
assert len(names) == len(set(names)), "dup"
json.dump({"squads": squads, "concepts": rows}, open("concepts.json", "w"), indent=1, ensure_ascii=False)
print(len(rows), {k: v["count"] for k, v in squads.items()})
