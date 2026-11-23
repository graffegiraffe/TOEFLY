# Reference vectors of FsrsSchedulerReferenceTest: py-fsrs 5.1.3 (FSRS-5, the defaults of fsrs-params-v1).
# Regenerate: python -m venv venv && venv/bin/pip install "fsrs==5.1.3" && venv/bin/python -I generate_vectors.py > py-fsrs-5.1.3-vectors.json
import json, sys
from datetime import datetime, timedelta, timezone
from fsrs import Scheduler, Card, Rating, State
sch = Scheduler(maximum_interval=365, enable_fuzzing=False)
# each sequence: list of ratings; each review happens exactly when the card is due
seqs = {
 "good-x8": [3]*8,
 "easy-first": [4,3,3,3],
 "again-hard-good": [1,2,3,3,3,1,3,3,3],
 "hard-path": [2,2,3,2,2,2],
 "lapse-twice": [3,3,3,3,1,1,3,3,4,3],
 "easy-x5": [4,4,4,4,4],
 "good-to-cap": [3]*14,
}
out = {}
t0 = datetime(2026,11,2,9,0,tzinfo=timezone.utc)
for name, ratings in seqs.items():
    card = Card(); now = t0; rows=[]
    for r in ratings:
        card, _ = sch.review_card(card, Rating(r), now)
        delta = card.due - now
        rows.append({"rating": r, "at": now.isoformat(), "state": card.state.name.upper(), "step": card.step,
                     "stability": round(card.stability, 6), "difficulty": round(card.difficulty, 6),
                     "intervalSeconds": int(delta.total_seconds())})
        now = card.due
    out[name] = rows
json.dump(out, sys.stdout, indent=1)
