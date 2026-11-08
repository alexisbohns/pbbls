# Verdict — wave 2026-11-everyday

Date: 2026-11-08. Decided by the maintainer through one blind game and a naming list.

## How the set was chosen
1. 124 figures (`figures.json`): the 26 chrome figures from `../2026-11-chrome/brief.md`, never drawn before, plus 98 everyday objects.
2. Three hands drew five default + active pairs per figure (`hands/`): h31 Opus, h32 Sonnet, h33 Opus with five random seeds per figure (`seeds.json`). Every batch passes `check.py`.
3. Blind two-round game (`game-build.py`): round 1 defaults, round 2 default + active pairs, four tiles (Paper and Night, ink and accent). Pick three, unlimited likes. Stopped at 159 of 248 steps because it was too long (`results/results-2026-11-08.json`).
4. Naming list (`rename-build.py`): every drawing picked or liked at least once, 902 drawings from 123 figures (treasure got nothing). The maintainer named all 902 (`results/names-2026-11-08.json`): 828 kept, 74 marked `tbr` and dropped.
5. `library-build.py` turns the names into `library.json` + `library.svg`; `../../scripts/explore/extract.py .` wrote `src/`.

## The set
828 icons, each with its `active` twin, all `candidate`. Row meanings are the figure's meaning; refine them when an icon gets a call site. `library.json` maps each name back to its figure and drawing.

## Twin rule
Loosened for this wave at the maintainer's ask: the active twin may fill the meaning-bearing body (up to three filled paths), not only one lit stone. 113 of the new twins trip the package lint's 64 square unit fill warning; they are kept on purpose.

## Next time
The game rules change: no hands or scores, pick one to three, likes are a sticky on/off, and the game ends in the mandatory naming list.
