# Playtest and UX notes

How the game was tested without a device (this project was built in a container with no emulator):

## Automated playtests

* **Solution replay** (`LevelsTest`): every level's stored solution is placed on the board and the
  machine is run; the goal must trigger within the level's time limit. The same test proves the
  untouched board never solves itself (so every puzzle needs the child to do something) and that
  solutions only use parts from the tray, sit on the 4-unit placement grid and do not overlap.
* **Touch-driven playthrough** (`PlaytestTest`): for every level the test presses the tray tile,
  drags it across the tray edge onto the field with the same lift-above-finger offset a child
  experiences, uses the floating flip/rotate buttons where the solution needs them, presses the big
  Play button and waits for the "You did it!" screen. Screenshots of every solved level are in
  `docs/screenshots/all-levels-solved.png`.
* **Monkey test** (`FuzzTest`): thousands of random taps, drags, cancelled touches, Play presses
  mid-drag and back presses, at four screen sizes including free play. Invariants checked after
  every event: no part outside the field, none off-grid, no overlaps, no negative tray counts, no
  exceptions.
* **Real Activity** (`MainActivityTest`, Robolectric with native graphics): the actual Android
  `Activity` and `View` are created, rendered through the Android `Canvas` painter, tapped through
  `MotionEvent`s (title -> level select -> level 1 -> Play) and the frames are saved as PNGs to
  compare against the Java2D reference renders.

## Design decisions for a six-year-old

* One sentence of text per puzzle, plus a picture of the goal part on the level tile.
* Parts snap to a fine 4-unit grid (160 x 100 positions on the field) so placement is precise but
  still tidy; a drop that would overlap something slides to the nearest free spot within 24 units
  instead of failing; dropping on the tray throws the part back.
* The dragged part floats 48 units above the finger so it is never hidden by the hand.
* Tapping a tray tile (instead of dragging) also places the part, in a free spot near the tray.
* A giant Play button in the tray column turns into a red Stop button while the machine runs.
* On the first three puzzles only: after 7 idle seconds on an untouched board the hint ghost appears
  with a big bouncing arrow from the tray to the right spot (twice at most). The light-bulb button shows the same hint on demand;
  using it costs one star.
* Failure is gentle: "Hmm, not yet! Let's try again" and the board is kept exactly as built.
* Success is loud: confetti, a fanfare, stars, and a Next button.
* No text entry, no menus deeper than one level, back button always does the obvious thing.

## Things found and fixed during playtesting

* Boxes snagged on the seam between two floor tiles (Mort stopped dead mid-walk): the collision
  solver now prefers a vertical separating axis when two bodies barely overlap vertically.
* Balls bounced out of the bucket: restitution now uses the smaller of the two bodies' values,
  as in the original engine, and buckets are soft.
* A ball rolling down a ramp crawled: rolling balls use rolling resistance instead of sliding
  friction; conveyors and the seesaw plank still grip.
* The seesaw's decorative end stops deflected incoming balls; they were replaced by a gripping
  plank so a ball rests on the low end until it is launched.
* Cages squashed the creature they were meant to trap; creatures now collide only with the cage's
  bars, not its roof.
* The pulley puzzle dragged the cage sideways when the rope left it at an angle; the level now
  uses two pulleys so both ropes hang vertically.
