# The Incredible Machine (TIM / TEMIM / TIM2) — Game-Mechanics Reference for a Faithful Clone

Research date: 2026-09-06. The single richest source turned out to be **OpenTIM**, a Rust/C reimplementation whose author disassembled the Windows 3.1 build of *The Even More! Incredible Machine* (TEMIM.EXE, MD5 30c97cd68e4ef7c7a35b68c1654d64fe) with Ghidra and DOSBox-X and dumped the engine's part tables from memory. Everything in §1.1 and §3 marked *(RE)* comes from that decompilation and is therefore exact engine behaviour, not inference. Everything else is from manuals, wikis and walkthroughs (cited inline).

Key sources:
- OpenTIM (fork with the fullest tree): https://github.com/mrfixit2001/OpenTIM — upstream https://github.com/nukep/OpenTIM
- ModdingWiki level-format spec (reverse-engineered by knt47): https://moddingwiki.shikadi.net/wiki/The_Incredible_Machine_Level_Format ; game page https://moddingwiki.shikadi.net/wiki/The_Incredible_Machine ; image format https://moddingwiki.shikadi.net/wiki/The_Incredible_Machine_Image_Format ; resource archive https://moddingwiki.shikadi.net/wiki/TIM_Resource_Format ; TIM2/3 ANM https://moddingwiki.shikadi.net/wiki/The_Incredible_Machine_2-3_ANM_File_Format
- Fandom wiki (fetched via its MediaWiki API): https://the-incredible-machine.fandom.com/wiki/The_Incredible_Machine_(game)/Parts , …/The_Even_More_Incredible_Machine/Parts , …/The_Incredible_Machine_2/Parts , …/Parts_of_RTIMC_and_TIMEMC , …/List_of_puzzles_in_The_Incredible_Machine , …/List_of_characters
- Manuals (archive.org full text): TIM1 DOS https://archive.org/details/the-incredible-machine-dos-manual ; TEMIM https://archive.org/details/the-even-more-incredible-machine-manual ; TIM2 https://archive.org/details/Incredible_Machine_2_-_Manual
- Walkthroughs: Sierra Help https://sierrahelp.com/Walkthroughs/TheIncredibleMachineWalkthrough.html ; JustAdventure (ex the-spoiler) https://www.justadventure.com/walkthrough/incredible/ ; TEMIM with per-puzzle goal + parts-bin lists https://www.agamesroom.com/walkthrough/temim (mirror of Mike8787's GameFAQs guide https://gamefaqs.gamespot.com/pc/564703-the-even-more-incredible-machine/faqs/18514)
- History/design: Digital Antiquarian https://www.filfre.net/2018/06/the-incredible-machine/ ; Kevin Ryan interview https://steemit.com/gaming/@badastroza/interesting-people-25-kevin-ryan-on-the-incredible-machine ; HG101 https://www.hardcoregaming101.net/incrediblemachine/incrediblemachine.htm ; Wikipedia https://en.wikipedia.org/wiki/The_Incredible_Machine_(1993_video_game)

---

## 1. Parts

### 1.1 Master table — engine part IDs and physical constants *(RE)*

The TIM/TEMIM engine has 66 part types (IDs 0x00–0x41). The table below is OpenTIM's `parts/mod.rs` (values dumped from TEMIM's "segment 30/31" part tables). Units: size in pixels (bounding box; second value is the "design" size where it differs); density is used only for the gravity/buoyancy formula (§3.3); mass is used for force, seesaw launch speed, rope tug-of-war and sort order; bounciness is a coefficient of restitution in 1/256ths (1024 = "rigid, defer to the other object"); friction is a surface coefficient (larger = stickier). "Static" parts have flags1 0x4800 (fixed, sit in the bin), "moving" parts have 0x0800 and are affected by gravity. Flip is what the part's create-function enables (H = horizontal, V = vertical).

| ID | Part | Kind | Size (px) | Density | Mass | Bounce | Friction | Flip | Resize |
|---|---|---|---|---|---|---|---|---|---|
| 0x00 | Bowling ball | moving | 32×32 | 2832 | 200 | 128 | 16 | – | – |
| 0x01 | Brick wall | static wall | 32×16 | 4153 | 1000 | 1024 | 24 | – | 16–240 both axes, 16-px steps |
| 0x02 | Incline (wood slope) | static | 32×32 | 1510 | 1000 | 1024 | 16 | H+V | width 16/32/48/64 (4 sizes) |
| 0x03 | Teeter-totter (seesaw) | static | 80×32 | 1888 | 1000 | 1024 | 16 | H | – |
| 0x04 | Balloon | moving | 32×48 | 9 | 1 | 64 | 32 | – | – |
| 0x05 | Conveyor belt | static | 96×16 | 3776 | 1000 | 1024 | 16 | – | width 32/48/64/80/96 (5 sizes) |
| 0x06 | Mouse motor (Mort's cage) | static | 48×32 | 3776 | 1000 | 1024 | 16 | H | – |
| 0x07 | Pulley | static | 16×16 | 3776 | 1000 | 0 | 0 | – | – |
| 0x08 | Belt (fan belt) | link | 0×0 | – | – | – | – | – | – |
| 0x09 | Basketball | moving | 32×32 | 1322 | 20 | 192 | 16 | – | – |
| 0x0A | Rope | link | 0×0 | 1600 | 1000 | – | – | – | – |
| 0x0B | Cage (bird cage) | moving | 48×64 | 7552 | 150 | 32 | 64 | – | – |
| 0x0C | Pokey the cat | moving | 40×39 | 2000 | 120 | 0 | 64 | H | – |
| 0x0D | Jack-in-the-box | static | 32×32 (up to 39×91 popped) | 3776 | 1000 | 1024 | 16 | H | – |
| 0x0E | Gear | static | 35×35 (32×32 design) | 7552 | 1000 | 1024 | 48 | – | – |
| 0x0F | Bob the fish (bowl) | static | 48×48 (104×32 broken) | 2000 | 1000 | 128 | 16 | – | – |
| 0x10 | Bellows | static | 64×48 (72–80 wide squeezing) | 3776 | 1000 | 128 | 16 | H | – |
| 0x11 | Bucket (pail) | moving | 40×48 | 7552 | 100 | 32 | 48 | – | – |
| 0x12 | Cannon | static | 64×52 | 14726 | 1000 | 192 | 12 | H | – |
| 0x13 | Dynamite | moving | 48×28 | 1132 | 90 | 64 | 32 | H | – |
| 0x14 | Gun bullet (spawned) | ephemeral | 40×7 | 0 | 20000 | 0 | 1 | – | – |
| 0x15 | Light switch + outlet | static | 48×32 | 3776 | 1000 | 128 | 16 | V | – |
| 0x16 | Dynamite with plunger | static | 135×47 | 3776 | 1000 | 128 | 16 | H | – |
| 0x17 | Eye hook (metal loop) | static | 16×16 | 7552 | 1000 | 128 | 16 | V | – |
| 0x18 | Fan | static | 32×32 | 7552 | 1000 | 1024 | 16 | H | – |
| 0x19 | Flashlight | static | 32×16 | 7552 | 1000 | 128 | 16 | H | – |
| 0x1A | Generator | static | 72×32 | 3776 | 1000 | 128 | 16 | – | – |
| 0x1B | Gun (revolver) | static | 64×31 | 7552 | 1000 | 192 | 16 | H | – |
| 0x1C | Baseball | moving | 15×15 | 2000 | 9 | 64 | 24 | – | – |
| 0x1D | Light bulb (drawstring) | static | 32×32 (32×54 drawn) | 1300 | 1000 | 128 | 16 | H | – |
| 0x1E | Magnifying glass | static | 16×37 | 3776 | 1000 | 128 | 16 | H | – |
| 0x1F | Kelly the monkey (monkey motor) | static | 92×79 | 3776 | 1000 | 128 | 16 | H | – |
| 0x20 | Jack-o'-lantern (Oct 31) | moving | 39×33 | 2400 | 100 | 64 | 32 | – | – |
| 0x21 | Heart balloon (Feb 14) | moving | 37×39 | 11 | 4 | 128 | 8 | – | – |
| 0x22 | Christmas tree (Dec 25) | static | 41×73 | 7552 | 1000 | 128 | 16 | – | – |
| 0x23 | Boxing glove | static | 48×31 (punch reaches −84 px) | 3776 | 1000 | 128 | 16 | H | – |
| 0x24 | Rocket | moving | 16×52 (16×66–83 lit) | 18000 | 1800 | 128 | 32 | – (flips up/L/R in TIM2) | – |
| 0x25 | Scissors | static | 40×32 | 7552 | 1000 | 128 | 16 | H | – |
| 0x26 | Solar panels (light outlet) | static | 72×32 | 7552 | 1000 | 128 | 16 | – | – |
| 0x27 | Trampoline | static | 48×28 | 7552 | 1000 | 128 | 32 | – | – |
| 0x28 | Windmill | static | 40×48 | 7552 | 1000 | 128 | 16 | H | – |
| 0x29 | Explosion (spawned) | ephemeral | 0×0 | 0 | 1 | 256 | 0 | – | – |
| 0x2A | Mort the mouse | moving | 24×11 | 2000 | 1 | 0 | 256 | H | – |
| 0x2B | Cannon ball | moving | 24×23 | 21428 | 28000 | 32 | 16 | – | – |
| 0x2C | Tennis ball | moving | 15×15 | 1322 | 5 | 192 | 16 | – | – |
| 0x2D | Candle | moving | 32×32 (34×36 lit) | 2000 | 12 | 128 | 32 | – | – |
| 0x2E | Pipe wall (straight) | static wall | 32×16 | 7552 | 1000 | 1024 | 16 | – | 32–240 both axes |
| 0x2F | Pipe curve | static | 32×32 | 7552 | 1000 | 1024 | 16 | H+V (4 orientations) | – |
| 0x30 | Wood wall | static wall | 32×16 | 7552 | 1000 | 1024 | 12 | – | 32–240 both axes |
| 0x31 | Rope severed end (spawned) | ephemeral | 0×0 | 1600 | 1000 | 0 | 0 | – | – |
| 0x32 | Electric motor (engine) | static | 56×47 | 7552 | 1000 | 1024 | 16 | H | – |
| 0x33 | Vacuum cleaner (TEMIM) | static | 50×50 | 7552 | 1000 | 1024 | 16 | H | – |
| 0x34 | Cheese (TEMIM) | moving | 30×18 | 1500 | 40 | 64 | 32 | – | – |
| 0x35 | Nail / tack (TEMIM) | static | 14×17 | 7552 | 20 | 1024 | 2 | – | – |
| 0x36 | Mel Schlemming (TEMIM) | moving | 14×24 (16×24 drawn) | 2400 | 40 | 128 | 64 | H | – |
| 0x37–0x39 | Title-screen logo pieces | – | – | 100 | 140 | 64–128 | 32 | – | – |
| 0x3A | Mel's house (TEMIM) | static | 48×64 | 7552 | 4000 | 1024 | 16 | – | – |
| 0x3B | Super ball (TEMIM) | moving | 24×23 | 1800 | 14 | **512** | 16 | – | – |
| 0x3C | Dirt wall (TEMIM) | static wall | 32×16 | 7552 | 1000 | 1024 | **128** | – | 16–240 both axes |
| 0x3D | Ernie the alligator (TEMIM) | static (0x4800) | 80×16 (80×33 snapping) | 2400 | 40 | 1024 | 48 | H | – |
| 0x3E | Teapot (TEMIM) | moving | 30×22 (up to 82×57 steaming) | 2400 | 40 | 64 | 64 | H | – |
| 0x3F | Eight ball (TEMIM) | moving* | 24×23 | 2400 | 40 | 256 | **0** | – | – |
| 0x40 | Pinball bumper (TEMIM) | static | 40×40 | 2400 | 40 | 256 | 16 | – | – |
| 0x41 | Lucky clover (Mar 17) | moving | 32×32 | 2000 | 800 | 128 | 64 | – | – |

\* The eight ball is documented as gravity-immune ("moves in a perfectly straight line until it hits something" — TEMIM manual); the collision code special-cases it (contacts with an eight ball are always treated as elastic part-to-part hits), so its gravity immunity is a special case rather than a density trick.

Parts-bin display order *(RE)*: Bowling ball, Basketball, Cannon ball, Baseball, Tennis ball, Balloon, Teeter-totter, Bellows, Boxing glove, Trampoline, Belt, Gear, Conveyor, Jack-in-the-box, Windmill, Rope, Eye hook, Pulley, Gun, Scissors, Light switch/outlet, Generator, Solar panels, Fan, Electric motor, Magnifying glass, Flashlight, Light bulb, Cannon, Dynamite, Rocket, Candle, Dynamite plunger, Bucket, Cage, Pokey, Mort, Mouse motor, Bob, Kelly, Brick wall, Pipe, Pipe curve, Wood wall, Incline, (spawned types), Vacuum, (title parts), Cheese, Nail, Mel, Mel's house, Super ball, Dirt wall, Ernie, Teapot, Eight ball, Pinball bumper, Jack-o'-lantern, Heart balloon, Christmas tree, Lucky clover.

### 1.2 Behaviour by group

**Balls (all trigger by collision; output = momentum).** Bowling ball: "extremely heavy, doesn't bounce" (manual) — in fact restitution 0.5 (128/256) but mass 200 makes it the standard "heavy trigger". Basketball: 0.75 restitution, mass 20, "bounces prodigiously". Tennis ball: 0.75, mass 5, very light. Baseball: 0.25, mass 9, "doesn't bounce a whole lot". Cannon ball: 0.125, mass 28000 (heaviest object in the game; fired by cannons at high speed; used to flip seesaws instantly). Super ball (TEMIM): restitution 2.0 — it *gains* speed on every bounce and never stops ("wildly unpredictable"), and when it hits something with restitution >0.5 the engine uses the super ball's own value instead of the minimum. Eight ball (TEMIM): frictionless, gravity-immune, moves in straight lines once struck — "think of a pool table viewed from above". Balls have octagonal collision borders (§3.4). None can be flipped or resized.

**Balloon.** Density 9 → strongly buoyant at Earth pressure (accelerates upward at 198 units/tick ≈ 0.39 px/tick², vs 0.53 px/tick² down for a bowling ball). Rope attaches at the knot (16,47). Pops on contact with a *turning* gear, scissor tips, a nail point, any flame (candle, rocket exhaust), a bullet, or in TIM2 a laser; heart balloon cannot be popped. Popping is a 6-frame animation after which the part disappears; if a rope was tied to it, a "severed end" part is spawned so the rope goes slack. Under Moon settings (low pressure) balloons *fall* like very light non-bouncing balls (see puzzle 27).

**Walls / floors.** Brick, wood, pipe (TIM1); dirt (TEMIM). Stretched with the size handle in 16-px steps in either axis (brick/dirt 16–240; pipe/wood 32–240); collision border is the full rectangle. Explosions blow holes in brick and wood, never in pipe, dirt or inclines. Friction: dirt 128 (things barely roll), brick 24, pipe 16, wood 12 (slipperiest). Pipe curve: 32×32 quarter-round, 4 orientations via H/V flip. Incline: wood ramp, 4 widths, rise fixed at 16 px over the width (so 16-wide = 45°, 64-wide ≈ 14°); border is a 4-point wedge; flip H mirrors, flip V inverts (ceiling ramp); explosion-proof.

**Teeter-totter (seesaw).** 80×32 with fulcrum at x = 36–44. Three states: 0 = left end down/right up, 1 = level (transient), 2 = left up/right down; flip toggles 0↔2. Tips when a moving part lands on the high end or something bumps the low end from below, or when a rope tied to an end is pulled (two rope slots, one per end; attach points move with the plank). When it tips, every moving part touching the plank is launched (§3.8). Also ends bump other static parts (dynamite plunger, bellows handle, flashlight button, scissors handle, mouse motor, Bob's bowl) when they swing through the end positions.

**Bellows.** Trigger: something lands on / bumps the handle (from above or below). Output: a puff of air that pushes balloons/light objects and spins a windmill; also blows out candles (TIM2 pump). Flip H. (The push-force code is not in the decompiled portion; strength is qualitative only.)

**Boxing glove.** Trigger: bump the button on its back. Output: spring punch extending up to 84 px in front, knocks balls, starts mouse motors, breaks Bob's bowl. Flip H. Objects glancing off its curved glove deflect at odd angles (walkthrough tip).

**Trampoline.** Anything falling onto the mat within ±15 px of its centre is launched upward with its downward speed plus 2 px/tick; horizontal speed is halved if ≥2 px/tick. Because the ball keeps falling under gravity for the 3 animation frames before launch, the net effect is the manual's "bounces higher and higher with each contact". Off-centre hits are ordinary collisions. Lower placement = more bounce height (walkthrough).

**Belt (fan belt).** Connects any two "rotating" parts (mouse motor, monkey motor, electric motor, generator, gear, conveyor, windmill, jack-in-the-box). Limited stretch: the cursor line turns green when close enough, red when too far. Only one belt per rotating part. Drawn as two lines between the parts' belt hubs (hub width 14 px on conveyors, 12 on the mouse motor, 8 on gears). Transmits direction: a motor's sign (+1/−1) is copied into the other part's `state2` each tick.

**Gear.** 4 rotation frames. Gears mesh only when placed at exactly ±32 px horizontally on the same y, or ±32 px vertically on the same x (checked at design time); each mesh reverses direction. If a gear receives conflicting directions from two neighbours the whole train stalls (state2 reset to 0). A turning gear pops balloons, and kicks any other object touching it at 8 px/tick tangentially (§3.8). Belt hub at (13,13).

**Conveyor belt.** 5 widths. Driven through a belt; direction = sign of the driving part (a gear between motor and conveyor reverses it, as does flipping the mouse motor). Objects on top are accelerated toward ±8 px/tick; objects touching the underside go the opposite way. Sound when running. Explosion-proof.

**Mouse motor.** Trigger: any collision with the cage, a seesaw end, or Pokey walking within 16 px. Output: rotation for 100 ticks (≈3.3 s at 30 Hz), direction +1 or −1 depending on flip. Can be re-triggered.

**Monkey motor (Kelly).** Trigger: rope tied to the window shade is pulled; the shade opens, Kelly sees the banana and pedals indefinitely. Output: rotation via belt on the front wheel. Flip H.

**Electric motor.** Needs to be plugged into an outlet (auto-plugs when placed adjacent, a black plug appears in the socket); outputs rotation when the outlet is live. Flip H (changes hub side / direction).

**Generator.** Input rotation via belt; provides its own 2-socket outlet. **Solar panel:** 2-socket outlet powered while any lit light source (flashlight, light bulb, lit candle) shines on it. **Light switch & outlet:** 2 sockets; switch is flipped ON by dropping something on it (or bumping it from below when flipped upside-down); "the switch always starts OFF regardless of flip" (TEMIM manual). TIM2 adds an always-on outlet and a laser-activated plug.

**Fan.** Plugged in; blows a horizontal air stream that pushes objects away, turns windmills, extinguishes candles. Flip H. **Vacuum (TEMIM):** plugged in; sucks in any gravity-affected object in front of it. **Windmill:** input air from fan or bellows; outputs rotation via belt; must be flipped to face the wind source.

**Ropes, pulleys, hooks.** Rope: unlimited length, ties two rope-capable parts (teeter-totter ends, balloon, bucket, cage, gun trigger, light-bulb string, monkey shade, eye hook, dynamite plunger, Mel's house? no — see list in TIM2 manual: teeter-totter, boat cleat, laundry basket, bucket, phazer, balloon, lava lamp, mandrill motor, pulley, trans-roto-matic, roto-trans converter, remote bomb…). To thread through pulleys, click each pulley in order while stringing. Ropes stretch taut and transmit pulls; a heavier hanging object hoists a lighter one (§3.8). Scissors cut ropes (not belts); the cut leaves a dangling severed end. Eye hook: fixed anchor, flip V to put the eye on top or bottom. Pulley: 16×16, rope enters left (0,8) and leaves right (15,8), simply redirects.

**Bucket.** Rope attaches at the handle (18,0). Contains any moving part whose centre is within x +4…+32 and whose bottom is within y +20…+52; contained parts move with the bucket, add their mass to it (capped at 32000), and are carried when it is hoisted. Dropping something heavy into it is the standard way to pull a rope. **Cage:** hangs from a rope (attach (21,2)); cut the rope to drop it on Pokey/Mort/Mel; when pulled upward it rises in 20-px steps.

**Gun.** Rope tied to the trigger; when pulled it fires one bullet (mass 20000, 40×7) that knocks objects, pops balloons, breaks the fish bowl, sets off dynamite and scares Pokey. Flip H. **Scissors:** drop something on the handles (from above, or bump from below) → snap shut, cutting a rope between the blades; tips pop balloons. Flip H.

**Light & fire chain.** Flashlight: button on top, one-shot ON when hit; beam horizontal (flip H). Light bulb: pull-string (rope) turns it on; flip H. Magnifying glass: placed between a light source and a fuse/wick focuses the light and ignites it "in about a second"; must be flipped to face the light. Candle: lit by a magnifying-glass beam, a rocket or another flame; once lit it is a portable flame that lights fuses, heats the teapot, pops balloons, powers solar panels; fans and bellows blow it out. Cannon: fuse lit → fires a cannon ball (3 muzzle-flash frames) — "does not affect walls or inclines"; TIM2 cannon rotates to 6 angles. Dynamite: fuse lit (6-frame burn) → explosion; blows holes through brick and wood walls, pushes objects. Dynamite plunger: 135-px-wide plunger box + charge; pushing the plunger down (drop something, seesaw end, rope) detonates. Rocket: fuse lit → flies straight up (TIM2: up/left/right), exhaust flame lights other fuses and pops balloons; can flick switches on the way up. Teapot (TEMIM): candle underneath → after a delay emits a steam jet that pushes objects (flip H).

**Jack-in-the-box.** Belt-driven; needs several crank turns (timing element) then pops (19 frames, box grows to 39×91) launching whatever sits on the lid; also breaks Bob's bowl; flip H sets the sideways kick direction.

**Pinball bumper (TEMIM).** 40×40 static; restitution 256 → objects rebound at full speed "in a random direction" (in practice the direction is determined by the polygon normal hit, so it is deterministic but hard to predict).

**Nail / tack (TEMIM).** 14×17 triangle; pops balloons that hit the point from below (bounce angles 0x5E00, 0x8000, 0x9C90 only); rows of tacks make stairs for Mel.

**Creatures.**
- *Mort the mouse* (24×11, mass 1): runs toward cheese he can see on the same level; runs away from Pokey; fits through holes Pokey cannot; killed if Pokey catches him (Pokey's 55×39 reach box). Flip H sets initial facing.
- *Pokey the cat* (40×39): every 10 frames re-scans up to 240 px in his facing direction for Bob's bowl (approach if within 96 px, or 292 px if the bowl is broken) or Mort; walks in 32-px steps; turns around when blocked; startled (jumps, 10-frame animation, sound 7) when hit; falls (flips upside-down after 5 frames of falling) and rights himself on landing. Won't collide with Mort geometrically (collision skip list) — eating is by proximity.
- *Bob the fish*: bowl with idle animation (11 frames); breaks (state 11, sound 10) when hit by any moving object, seesaw end, bullet or Jack; once broken Bob flops for 20 ticks and dies; a broken bowl attracts Pokey from farther away.
- *Kelly the monkey*: see monkey motor.
- *Mel Schlemming* (TEMIM, 14×24, mass 40): walks mindlessly, turns around on bumping anything, bounces on trampolines (special-cased), dies if he falls too far or hits a wall too fast, eaten by Ernie; enters Mel's house (48×64, 9 states) when he walks into its door; house may be programmed as "vacant/occupied" solution part in TIM2. Mel does not collide with other Mels or with his house.
- *Ernie the alligator* (TEMIM, 80×16): fixed in place, eats Mort and Mel (not Pokey); flicks objects dropped on his tail or snout into the air (catapult; snapping animation 80×33).
- *Cheese*: bait for Mort (30×18, mass 40).

### 1.3 Holiday parts (TIM1 free-form only, shown on specific dates)
Pumpkin/jack-o'-lantern (Oct 31, "frictional to horizontal movement"), Christmas tree (Dec 25, static, explosion-proof), Irish shamrock/lucky clover (Mar 17, "slightly bouncy and light" — engine mass is actually 800), Valentine heart balloon (Feb 14, cannot be popped). Saved machines carry them over. (Fandom TIM parts page.)

### 1.4 TIM2 (1994) renames and additions (Fandom TIM2 parts page; TIM2 manual)
Renames with identical behaviour: Bellows→Bike Pump, Bird Cage→Laundry Basket, Bob's bowl→Bill's Fish Tank, Drawstring Light→Lava Lamp, Ernie→Edison Alligator, Metal Loop→Boat Cleat, Monkey Bike→Mandrill Motor (Pavlov Mandrill), Mort→Newton Mouse, Pokey→Curie Cat, Revolver→Captain Z's Super Phazer (programmable shot count), Scissors→Tin Snips and Hedge Trimmers, Teapot→Coffee Pot, Trampoline→Springboard, Windmill→Pinwheel, plus holiday swaps (Cupid, Leprechaun, Boris the Bat, Santa lamp). New: Anti-Gravity Pad (reverses gravity for anything on it), Blimp (flies straight, reverses on contact, pops on sharp/flame), Hot-air balloon (lit candle lifts it), Tipsy Trailer (a seesaw), Steel Cable (rope only cut by tin snips), Trap Door, Thumb Tack (4 orientations), Leaky Bucket (programmable drain = timer), Boxes (5 sizes), Aladdin's Lamp, Flint & Tinder, Match-on-a-Spring, Fireworks, Missile, Nitroglycerine (explodes on impact), Remote-control Bomb (two-piece), Can Opener (lures Curie), Electric Mixer, Toaster (programmable timer, pops toast), Egg Timer (programmable), always-on Electrical Outlet, Laser-Activated Plug, Red/Green/Blue Lasers, Laser Mixer, Angled Mirror, Laser Detector, Tiny Gear (2× speed), Trans-Roto-Matic (rope pull → rotation), Roto-Trans Converter (rotation → rope pull), Pinball Flipper, Pool table walls/pockets/cue/pool balls, Large Pipes/T-connector/curved pipe/Accelerator Tube, Mouse Hole, Message Computer, Programmable Ball (mass/elasticity/density/friction sliders; file stores density, elasticity, friction, gravity_buoyancy, mass — e.g. dialog (8,3,7,3) → file (3000,128,16,269,201); default 2832/200 is exactly the bowling ball), Soccer ball, Pinball, 12 wall/incline skins with documented slipperiness (yellow brick, cinder block, Greco-Roman, log, caution, grass, sand, granite …), and ~40 scenery parts. TIM2 file part IDs known: 76 = steel cable, 87 = programmable ball (ModdingWiki).

---

## 2. Rules of play

### 2.1 Screen layout (TIM/TEMIM DOS, 640×480, 16-colour VGA)
- Playfield (level viewport): **576×368 px** for standard TIM/TEMIM levels; 640×400 for the title/credits .GKC machines; TIM2/3 use 560×377 (ModdingWiki). Full-screen art is 640×480×16 colours (.SCR).
- The level coordinate origin is (−8,−8) at the top-left of the playfield (header fields `unknown_8/10` are always −8).
- The remaining right-hand column (64 px) holds the "runner in starting blocks" start button at top right, the vertical **parts bin** (part icons from ICONS.BMP with a quantity number under each, scroll arrows to page through), and the small buttons (next puzzle, puzzle-select menu, free-form/wrench, clear/restart broom, volume, quit). The strip under the playfield carries the puzzle title/goal in TIM1; the full **Control Panel** (goal text, gravity and air-pressure gauges, score, bonus 1, bonus 2, load/save, password) is a separate screen reached by right-clicking (TIM1/TEMIM manuals).
- Placement is **not tile-based**: moving parts sit at arbitrary pixel positions, but "the game uses a 16×16 grid for walls and a few other puzzle parts" (ModdingWiki); walls stretch in 16-px steps and gears must be placed at exact 32-px offsets to mesh.

### 2.2 The parts bin and part counts
Each puzzle supplies a fixed inventory (num_parts_in_partsbin in the level header; each part in the bin is a separate record with flag 0x800). Typical bins hold 1–12 parts; belts, ropes and pulleys are the most numerous. Extremes among the 87 TIM1 puzzles: 1 part ("Save the Balloons": one rope; "Get Mel Home": one tack) up to ~22 ("Fetch a Pail": 7 belts + 10 gears + more; "Five Gun Salute": 2 baseballs, 4 seesaws, 9 ropes, 2 pulleys, 3 scissors). Puzzles may include decoy parts that are not needed (walkthroughs point these out, e.g. puzzle 16 leaves a pulley, magnifier and flashlight unused).

### 2.3 Placing, moving, flipping, sizing, deleting
- Click a part in the bin → it follows the cursor; click on the playfield to drop it. A red "X" over the part means it overlaps another part and cannot be placed there (overlap test = polygon-border intersection, §3.4). Click a placed part to pick it back up.
- Hovering a placed part shows **smart-cursor handles**: red curved arrows (flip horizontally / vertically, only for parts whose create flags allow it), blue double arrows (stretch: walls both axes, inclines and conveyors horizontally), a trash can (return to bin), and in free-form/TIM2 a padlock (lock). Preset/locked parts cannot be moved.
- Belts and ropes: pick the belt/rope, click the first part (a red line follows the cursor), move over the second part; the line turns **green** when a legal, in-range connection exists; click to attach. For ropes, click each pulley in between. Belts have a limited stretch distance and "disappear if stretched too far" (TIM2); ropes are unlimited and "can stretch around the screen as many times as needed" (TEMIM manual).
- Electrical parts auto-connect: place a fan/motor/vacuum adjacent to an outlet and a plug icon appears in the socket; move it away and the plug disappears. Outlets have two sockets. The outlet must be placed before the appliance.
- TIM2 adds right-click duplicate, an info handle, programming handle (12 programmable parts), solution-flag handle and hints.

### 2.4 Start, stop, control panel, progression, scoring
- **Start** (runner icon / green flag in TIM2): switches from design mode to simulation. All parts are reset to their design positions/states, spawned parts (bullets, severed ropes, explosions) are cleared, then the deterministic tick loop runs (§3.2). Clicking anywhere (left button) stops the machine and returns to design mode with the design layout restored; "P" pauses in TEMIM.
- TIM1 requires solving preset puzzles in order; solving one shows a **password** (e.g. 1 SIERRA, 2 DYNAMIX, 3 MACHINE, 4 DISK, 5 SHUTTLE, 6 SATURN, 7 KING, 8 DRAGON, 9 ANTS, 10 BASEBALL, 11 BEAR, 12 FISH, 13 DALE, 14 CHESTERTON, 15 IRELAND, 27 GRAPHICS …; "GULF" opens level 87). Score = sum of puzzle scores; **Bonus 1** depends on how quickly the puzzle was solved, **Bonus 2** is a preset difficulty bonus (both stored per level in the header as `bonus_1`, `bonus_2`). TIM2 replaces passwords with player profiles and five difficulty tiers (Tutorial/Easy/Medium/Hard/Really Hard).
- Puzzle counts: TIM1 87 (22 tutorials + 65; the manual says "more than 85"; Wikipedia's "80" is wrong), TEMIM 160 (the first 87 identical, +73; puzzle 88 "Tutorial: Get Mel Home" introduces Mel), TIM2 "150-plus".

### 2.5 How success is detected
- **TIM/TEMIM: hard-coded per level.** The level file contains no goal data; TEMIM.EXE holds a table of 160 far-pointers to "clear condition" functions, one per level number (several levels share a function) — OpenTIM's `tim-label-clear-condition-functions.py`. ModdingWiki confirms: renaming L1.LEV to another number makes that level unwinnable or trivially winnable because the wrong condition function runs. Free-form puzzles therefore have **no** win detection ("the player is on the honor system", TEMIM manual).
- Goal types seen in TIM1 (from the goal texts in §4): get object X into container/hoop/hole/off-screen (bottom/edge); pop N (or all) balloons; don't pop any balloon; make all fans/gears/guns/cannons/rockets fire/turn; light or put out a candle; explode all dynamite; break or protect Bob's bowl; get Mort to hole/house/cheese; trap Pokey/Mort in cage; get Mel home; lower bucket onto wall; keep bullets from hitting Bob/Pokey. The engine ends the run with a "beat the level" state (LEVEL_STATE 0x0200) and offers replay/advance.
- **TIM2/3: data-driven.** Each level stores up to 8 solution records (132 bytes): part index, required `part_state_1/2` (an animation-state ID, e.g. balloon popped, candle lit, house occupied, Mel asleep), a `part_count`, and a target rectangle; sentinel rectangles mean Off-Screen (−1,−1,−1,−1), Off-Top (−500,−2000,1640,2000) and Off-Bottom (−500,400,1640,3000); plus a trailing `delay`. Moving parts are usually solved by position, and Mel, balloons/blimps, coffee pot, lamp, cat, mouse, candles, rockets, missiles, dynamite and nitro can be solved by state change. At least five parts must be on screen for the game to recognise a solution.

### 2.6 Free-form mode
Wrench icon → all parts available in unlimited quantity (the bin count per part is set via the "Adjust Parts Bin" screen; a bomb icon clears the bin); build, save (.TIM file, same format as .LEV) and load machines; sample machines shipped (e.g. TONSOFUN). To make a puzzle: build the machine, clear the bin, pick a few key parts and return them to the bin, lock the rest with the padlock, optionally add decoys, type a goal string, save. Gravity and air pressure can be adjusted only here (TIM1/TEMIM); music selectable with keys 1–9/A–L in TEMIM (21 tracks; level header `music` 1001–1016 TIM, 1001–1021 TEMIM). TIM2's "Professor Tim's Workshop" adds solution programming, hints (8 max, each with position, icon flip 0–3, text), locking, scenery, background colour, and a two-player "Head-to-Head" mode.

### 2.7 Environment controls — gravity and air pressure
- Stored per level as two signed 16-bit values. **Gravity: 0 … 512, default 272** ("Earth"); the TIM1 manual describes the slider range as zero gravity up to "gravity as strong as on Saturn". **Air pressure: 0 … 128, default 67**; range "no pressure, as in deep space" to "as great as under the ocean". Sliders are dragged along small graphs on the Control Panel (TIM1) or set in the Workshop's globe menu (TIM2).
- Effect (TIM2 manual): higher gravity → things fall faster; higher pressure → denser atmosphere → objects less dense than the atmosphere float ("a balloon needs only low pressure to rise, a bowling ball needs very high pressure before it floats"). Exact formula in §3.3. Puzzles that alter them in TIM1: 27 "Popping Balloons on the Moon" (balloons fall, act like light non-bouncy balls), 80 "Basketball on the Moon", 45 "You Gotta Smile", 99 "Bumping Up", 103 "Sacrifice" and 115 "Hole in the Floor" in TEMIM (walkthrough notes "it's time to defy gravity").

---

## 3. Physics — engine internals *(RE unless noted)*

### 3.1 Numbers and units
- Pure 16/32-bit integer maths (Kevin Ryan: "it was all integer — no floating point… I had to come up with an integer system with enough resolution, and write sin and cos routines in integer maths"). Sine/cosine tables return −0x4000…+0x4000 over a 65536-unit circle; arctan is a 512-entry lookup giving 4096 distinct angles (~11 per degree), 0 = down, 0x4000 = left, 0x8000 = up, 0xC000 = right, clockwise.
- Position: 32-bit "hi-precision" x/y with **9 fractional bits** (pixel = hi >> 9, i.e. 512 units per pixel); a 16-bit pixel copy is kept for collision/rendering. Velocity is a signed 16-bit pair in the same units: **1 unit = 1/512 px per tick**. Positions are clamped to −1000 … +6000 px.
- Each part keeps current, previous-1 and previous-2 copies of position, size, state and rope lengths (used for sub-tick sweep tests and animation).
- Force (used for seesaws/ropes) = (|vx| + |vy|) × mass, 32-bit.

### 3.2 Per-tick update order (`advance_parts`)
1. Clear per-tick flags on static parts.
2. Run parts that were queued by rope pulls last tick (a 20-entry priority list ordered by force).
3. Run "special" static parts (flag 0x0800: e.g. mouse motors), then all gears, then all other static parts, then teapots.
4. For every moving part: add its gravity/buoyancy acceleration to vy, clamp to terminal velocity, recompute force, reset mass to the type default.
5. For every non-bucket moving part: run its behaviour function (animation/AI), integrate position (§3.1), and run the collision search (§3.4) — also enforcing rope constraints (§3.8).
6. Buckets: collect contents, add their mass, then integrate/collide the bucket, then drag contents along.
7. Resolve contacts: part-vs-part elastic exchange, surface bounce, or rest/slide (§3.5–3.7). Mel gets special "jumpy" handling.
8. Update rope/belt geometry for anything that moved; then all "prev" copies roll over.

Sort order matters for determinism: the static list is sorted by mass then original position, the moving list by ascending mass (ropes/severed ends first). Balls are processed lightest-first each tick.

### 3.3 Gravity, buoyancy and terminal velocity
With g = level gravity and a = level air pressure:
- adj_grav = g/4 + 1 if g < 140; 2g if g > 278; else g. (Range 1 … 1024.)
- adj_air = a/2 if a < 70; 16a otherwise. (Range 0 … 2048 — note the discontinuity at 70, from 34 to 1120: the moment the slider passes 70, everything with density below ~1120 floats.)
- If density > adj_air: accel = adj_grav − adj_air·adj_grav/density (downward, positive). If density < adj_air: accel = −(adj_grav − density·adj_grav/adj_air) (upward). Equal → 0.
- Terminal velocity (applied to both axes, all parts) = 0x2600 − adj_air = 9728 − adj_air units/tick.

Measured (memory-dumped) accelerations at Earth (272, 67 → adj_air 33, terminal 9695 ≈ 18.9 px/tick):

| density | example parts | accel (units/tick) | px/tick² |
|---|---|---|---|
| 0 | bullet, explosion | −272 | — (never falls) |
| 9 | balloon | −198 | −0.39 (rises) |
| 11 | heart balloon | −182 | −0.36 |
| 100 | title parts | +183 | 0.36 |
| 1132 | dynamite | 265 | 0.52 |
| 1300–1322 | light bulb, basketball, tennis ball | 266 | 0.52 |
| 1500–1600 | cheese, rope | 267 | 0.52 |
| 1800–2000 | super ball, baseball, candle, Mort, Pokey, Bob | 268 | 0.52 |
| 2400 | Mel, teapot, eight ball, bumper | 269 | 0.53 |
| 2832 | bowling ball | 269 | 0.53 |
| 3776–4153 | conveyor, brick | 270 | 0.53 |
| 7552 | most static parts, bucket, cage | 271 | 0.53 |
| 14726–21428 | cannon, rocket, cannon ball | 272 | 0.53 |

Other fixtures: g=0 → every part gets ±1 (a creep); g=512,a=67 → bowling ball 1013, balloon −745; g=272,a=128 (max pressure) → bowling ball +76, basketball −97, dynamite −122, terminal 7680; g=512,a=128 → cannon ball 927, bowling ball 284, basketball −363. A part resting on a surface (flag 0x0001) is additionally pushed 2 px per tick into the surface (or away if buoyant) to keep contact.

### 3.4 Collision geometry
- Every part has a polygon **border** (2–12 points in part-local pixels) with a precomputed outward normal angle per edge. Balls use an octagon: 32-px balls (8,0)(23,0)(31,8)(31,23)(23,31)(8,31)(0,23)(0,8); 15-px balls (3,0)(11,0)(14,4)(14,10)(11,14)(3,14)(0,10)(0,4). Walls/conveyors are rectangles; inclines 4-point wedges; the seesaw has 8 points per state including the fulcrum; balloon 8; bucket 6 (open top); cage 12; Pokey 5; Bob 8; Christmas tree 7; nail 3.
- Broad phase: axis-aligned box overlap, swept by the part's movement since last tick. Narrow phase: for each edge of the moving part whose motion is *into* the other's edge (normal test), intersect the movement segment with the edge (integer line-intersection helper). On hit the part is moved back to the contact point, `bounce_part`, `bounce_angle` (edge normal) and `bounce_border_index` are recorded, and a flag says whether this is part-to-part (both moving, → momentum exchange) or part-to-surface. Buckets' contents are exempt from colliding with their bucket; pairs (Pokey, Mort), (Mort, cheese), (Mel, house), (Mel, Mel) never collide geometrically.
- Design-mode placement uses the same border-intersection test (red X); parts flagged 0x4000 (fixed) may overlap each other by bounding-box rule only.

### 3.5 Bounce off a surface
Rotate velocity into the edge frame; restitution b = min(bounciness of ball, bounciness of surface) in 1/256 (walls are 1024 so the ball's own value wins; super ball uses its own 512 if the other's is >127). New normal speed = −(v·b)/256, then reduced by 64 units (0.125 px/tick) toward zero (so tiny bounces die out). Frictionless parts (friction 0: eight ball, pulley, rope) reflect perfectly. If the normal is exactly vertical/horizontal but the contact was at a corner, the angle is skewed ±0x1000 (22.5°). Bowling-ball impact sound plays when speed > 8 px/tick.

### 3.6 Rest and slide (on any surface, including slopes)
Effective friction = max(part friction, surface friction), but 256 if the surface is a *running* conveyor. Gravity's along-slope component (sin/cos of the surface angle) is added to vx; then a decel of |cos(angle)·g·friction/256| + 2 (or +32 for "bucket-like" parts with flag 0x20) is subtracted toward zero; vy is set to vx·tan(slope). This gives rolling down inclines, stopping on flats, and a strong grip on dirt (128) and conveyors.

### 3.7 Part-to-part impacts
Velocities are rotated into the line of centres; a mass-weighted exchange is applied: v1' = (m1·v1 + 2m2·v2 − m2·v1)/(m1+m2), v2' symmetric; results are halved (>>1) — so collisions are lossy. If both end up nearly stationary horizontally (<0.5 px/tick) they are pushed apart at 2 px/tick. Force = (|vx|+|vy|)·mass is what a seesaw/rope compares to decide who wins a tug-of-war.

### 3.8 Part-specific numbers
- **Conveyor**: on top-edge contact (or any contact with |angle|≤0x800), vx is driven toward ±4096 units = **8 px/tick** in the belt direction (quirk preserved by OpenTIM: the negative clamp is written wrongly so leftward speed can overshoot); underside contacts push the opposite way. Belt animation cycles 7 frames per size (state1 = 7·sizeIndex … +6).
- **Gear**: an object touching a turning gear gets a tangential impulse toward 8 px/tick (4096) on the four cardinal edges or two 4 px/tick components on diagonal edges, direction following rotation; balloons pop instead.
- **Trampoline**: contact within ±15 px of centre → starts 5-frame animation; on frame 3, vy = −|vy| − 1024 (adds 2 px/tick), vx halved if |vx| ≥ 1024; the part is released from the surface so it falls freely.
- **Teeter-totter**: tipping sets state ±1 per tick until the end state; when it changes state, every moving part crossing the plank line gets vx = ±speed/4, vy = ∓speed with speed by mass: <2 → 7168 (14 px/tick), <6 → 6656, <10 → 6144, <21 → 5632, <121 → 5120, <151 → 4608, else 4096 (8 px/tick); i.e. light objects fly higher. A landing on the high end needs the ball's centre outside the fulcrum zone (x 37–43 of 80 is dead). Rope pulls on an end are honoured only if the puller's force beats what is on the other end.
- **Ropes/pulleys**: a rope stores two segment lengths (from each end to the next pulley). Each tick the constraint enforces length: if a tied moving part would exceed its segment, it is pulled back along the segment direction and its velocity rewritten from the displacement; if the *other* end is a moving part lighter than this one, rope length is shifted to it (heavier side descends, lighter rises, by (Δlen × (m1−m2))/m1 per tick, min 1 px). Balloons, buckets and cages resist a pull only if the puller's force is less than twice their own force (or less than 1× when a seesaw pulls). A rope crossing a pulley whose far side is a severed end simply pays out. Slack rope is drawn with sag = stored length − straight-line distance (approximate hypot = long + 3/8·short).
- **Belts**: no dynamics; they copy direction each tick and are drawn between hubs (belt_loc/belt_width per part).
- **Bucket**: see §1.2 — contents copy the bucket's velocity and are moved with it after collision.
- **Balloon**: pops in 6 ticks; rope detaches at pop.
- **Mouse motor**: runs 100 ticks per trigger, cage animation toggles every tick.
- **Pokey**: rescans every 10 ticks; walks 32 px per step (state 1 → after 12 ticks of counter), reverses if the step collides; upside-down fall after 5 ticks of |Δy|≥2.
- **Bob**: 11-frame idle, break at any moving-part contact when state ≤10; 20 ticks of flopping then death.

### 3.9 Timing and determinism
- No random number generator anywhere; all state is integer; part lists are deterministically ordered. Wikipedia and HG101 both note the engine "does not use a random number generator, ensuring results are deterministic". The one non-determinism to avoid in a clone is sort instability for equal-mass parts (the original tie-breaks by original y then x).
- The Windows build runs the simulation off a timer; OpenTIM advances the world every second frame of a 60 Hz loop (**30 ticks/s**), which makes a bowling ball cross the 368-px playfield in ≈1.2 s — consistent with the original's feel. The DOS tick rate is not documented anywhere I found; treat 30 Hz as the working assumption and keep all constants per-tick.
- Positions clamp at −1000/+6000 px, so off-screen objects keep simulating (needed for "off the bottom of the screen" goals and for balloons that fly up and come back).

---

## 4. Puzzle progression (TIM1 = puzzles 1–87; TEMIM reuses them verbatim then adds 88–160)

Goal text and parts-bin contents are from the TEMIM walkthrough (identical levels); fixed-part descriptions from the Sierra Help and JustAdventure walkthroughs. "Slant/slope" = incline, "mouse exercise wheel" = mouse motor, "fan belt" = belt.

| # | Title | Goal (in-game text) | Parts bin | Fixed layout (summary) |
|---|---|---|---|---|
| 1 | Tutorial: Put the Ball in the Hoop | Make the basketball go through the hoop. | 3 belts, 3 mouse motors, 3 inclines | Three bowling balls on three stepped conveyors, a basketball at the top, a hoop at right. Password SIERRA |
| 2 | Tutorial: Mirror Images | Put both bowling balls in the metal baskets (place and flip the mouse cage, hook belt to conveyor). | Basketball, belt, mouse motor | Left half is a complete machine; build its mirror on the right. DYNAMIX |
| 3 | Tutorial: Bellows and Balloons | Pop all the balloons. | Basketball, tennis ball, 2 bellows, scissors | Two balloons, one already popped; a turning gear. MACHINE |
| 4 | Tutorial: Flip, Flip, Flip | Make the bowling ball fall off the bottom of the screen. | 4 teeter-totters | Column of cannonballs; each seesaw flips the next. DISK |
| 5 | Tutorial: Punch Out | Use the boxing gloves to punch the baseball up to the metal pipes. | 2 teeter-totters, 2 boxing gloves | 2 bowling balls, 2 cannonballs, baseball, wood/pipe channels. SHUTTLE |
| 6 | Tutorial: Bouncing over to Mort | Make Mort run in his cage by hitting the cage with the basketball. | 3 trampolines | Basketball high left, mouse motor far right. SATURN |
| 7 | Tutorial: Jack Says "Hi Bob" | Make the jack-in-the-box break Bob's fishbowl. | 2 belts, mouse motor | Two jacks, a mouse motor, cannonball, Bob in a brick square. KING |
| 8 | Tutorial: Tilting at Windmills | Pop the balloon (flip the windmill to pick its direction). | 2 bellows, 2 belts, 2 windmills | Bellows, three conveyors, gear, baseball, tennis ball, balloon. DRAGON |
| 9 | Tutorial: Lower All the Buckets | Lower all three buckets by cutting both ropes with the scissors. | Cannonball, trampoline, scissors | Three hanging buckets, two scissors, baseball. ANTS |
| 10 | Tutorial: Bang, Bang, Bang | Shoot all three guns. | 2 ropes, gun, bucket | Two guns (one already rigged), seesaw, two pulleys. BASEBALL |
| 11 | Tutorial: Like a Hurricane | Turn on all the fans (flip the switch to power the plug; plugs start off). | Trampoline, 2 fans | Three switched outlets, one fan, balloon, baseball, cannonball. BEAR |
| 12 | Tutorial: Generators and Motors | Make the tennis ball fall into the pipe hole. | 3 belts, windmill, generator, fan, electric motor | Powered fan, generator, conveyor with basketball, mouse motor, tennis ball, pipe hole. FISH |
| 13 | Tutorial: Putting the Gears in Motion | Make all the gears turn (light on the solar panel powers its plug). | 3 belts, rope, fan, electric motor, flashlight | Outlets, solar panel, conveyor with cannonball, windmill, bucket, light bulb, gear train. DALE |
| 14 | Tutorial: Lighting a Fuse | Fire all the cannons (flip one magnifier). | 2 magnifying glasses, 2 flashlights | Three cannons in tunnels; first already rigged with flashlight+magnifier. CHESTERTON |
| 15 | Tutorial: Boom, Boom, Bang | Fire the cannon. | Rope, magnifying glass, flashlight, dynamite | Bucket, light bulb, basketball, cannon. |
| 16 | Tutorial: Blastoff | Light the candle. | Teeter-totter, rope, pulley, magnifier, flashlight, light bulb (+decoys) | Bowling ball launcher, two rockets, balloon, candle. IRELAND |
| 17 | Tutorial: Doing Some Blasting | Push both plungers down and explode all dynamite on the playfield. | Teeter-totter, rope, pulley, 3 dynamite | Bowling ball drop, gun, two plungers, brick square with three ledges. |
| 18 | Tutorial: Sending Mort the Mouse Home | Get Mort safely to the mouse hole bottom right; a broken fishbowl attracts Pokey. | Trampoline, belt, conveyor, Pokey | Bowling ball, Bob's bowl, Mort, mouse motor, brick levels, hole. |
| 19 | Tutorial: Monkey Business | Make both monkeys ride their bicycles. | 2 ropes, 2 pulleys, light bulb, rocket | Two monkey motors, hanging cage. |
| 20 | Tutorial: Bridging the Gap | The basketball must cross all the gaps (rope two seesaws through the pulleys; shrink an incline). | Teeter-totter, rope, incline | Wooden track with gaps, seesaws, pulleys. |
| 21 | Tutorial: Climbing a Hill | Make the ball climb the hill and knock Pokey off the cliff (ball must roll over the mouse cages). | 2 belts, 2 conveyors, 2 mouse motors | Stepped hill with gaps, Pokey on top. |
| 22 | Turn, Turn, Turn… Pop, Pop, Pop | Pop any three of the four balloons (turning gears pop balloons). | 4 belts, gear, rope | Gear cluster, two monkeys, seesaw, mouse motor, 4 balloons. |
| 23 | Trick Shooting | Get the three baseballs, but none of the tennis balls, into the container on the right. | 3 ropes, 3 pulleys, 3 guns | Three seesaws with baseballs/tennis balls, container. |
| 24 | Save the Balloons | Don't let any of the balloons pop. | Rope | Four balloons, two tied over pulleys, nails/gear above. |
| 25 | Pop Two Balloons | Pop the two rightmost balloons. | Bowling ball, 2 balloons, 3 ropes, 3 pulleys | Gear, mouse motor, brick ledges. |
| 26 | Dropping the Ball | Make the bowling ball drop into the cave. | Teeter-totter, 2 belts, 3 gears, 2 conveyors, … | Gun, two pails, cannon, light bulb, magnifier. |
| 27 | Popping Balloons on the Moon | Pop all three balloons. Note that you are on the moon. **(low gravity/pressure: balloons fall)** | 2 boxing gloves, 2 ropes, 2 guns, 2 inclines | Scissors, gear, trampoline, seesaw, pulleys, pipe tunnel. GRAPHICS |
| 28 | A Baseball in Every Pot | Put one baseball in each of the six pipe containers. | 6 teeter-totters, 2 trampolines, belt, conveyor, mouse motor | Six baseballs, bowling ball. |
| 29 | Weighing the Situation | Make the baseball fall off the bottom of the screen. | Cannonball, 3 tennis balls, 2 ropes | Four hanging pails, two pulleys, incline. |
| 30 | Pop All the Balloons | Pop both balloons. | Teeter-totter, 3 belts, 2 conveyors, 2 ropes, monkey motor | Gears, pipe, bowling ball. |
| 31 | Put Away the Basketballs | Put all the basketballs into the wooden container at right. | Teeter-totter, belt, conveyor, mouse motor, … | Stack of basketballs. |
| 32 | Five Gun Salute | Shoot all five guns. | 2 baseballs, 4 teeter-totters, 9 ropes, 2 pulleys, 3 scissors | Five guns, five tethered balloons. |
| 33 | A Farewell to Balloons | Pop the balloon. | 6 belts, gear, fan, magnifying glass | Outlets, generators, six gears, candle, tennis ball, flashlight. |
| 34 | Pop Goes the Weasel | Get the jack-in-the-box to spring up. | 2 teeter-totters, 4 belts, 6 gears, 2 ropes, solar panel | Mouse motor, fan, basketball, tennis ball, light bulb. |
| 35 | Wheeeeeee! | Put the bowling ball into the metal basket. | 2 balloons, teeter-totter, 3 trampolines, 2 inclines | Trampoline course. |
| 36 | Pokey and Bob Shoot It Out | Let both guns fire but don't let bullets hit Bob or Pokey. | Bowling ball, scissors | Two guns, pails on ropes, pulleys. |
| 37 | Mouse in the House | Send Mort to his house at bottom left. | 2 belts, gear, generator, 2 fans, electric motor, … | Outlets, incline, pipes, house. |
| 38 | Inside the Walls | Put the bowling ball inside the brick walls. | Bellows, 4 trampolines, belt, conveyor, windmill, … | Pipe levels with gap, baseball. |
| 39 | Trap Pokey the Cat | Trap Pokey in the cage and make the cannon ball go into the bucket. | Bowling ball, scissors, magnifier, flashlight, cannon, … | Hanging cage on rope, brick walls, Pokey. |
| 40 | Going in the Hole | Make both basketballs fall into the brick hole. | Teeter-totter, belt, conveyor, 2 ropes, pulley, … | Switched outlet, balloon, two basketballs. |
| 41 | Help Pokey Get Home | Help Pokey reach the house in the lower right. | Teeter-totter, rope, 2 pulleys, 2 mice, 2 fishbowls | Brick floors with gaps. |
| 42 | Ring of Fire | Explode all the dynamite. | Teeter-totter, boxing glove, trampoline, 2 magnifiers, … | Dynamite ring, tennis ball, flashlight. |
| 43 | Lower the Bucket | Lower the bucket so it rests on the brick wall. | Bowling ball, 5 belts, 4 conveyors, 2 ropes, 3 pulleys, … | Candle, seesaw, gears, solar panel, outlet. |
| 44 | Play a Set | Make the tennis ball go over the net. | Teeter-totter, trampoline, rope, pulley, gun, scissors, … | Net, dynamite, rocket, pail, flashlight, magnifier. |

Remaining TIM1 titles in order (45–87): You Gotta Smile; Save Bob the Fish; Fetch a Pail; Exercise Kelly the Monkey; Feeling a Little Out of Sorts; Pop, Pop, Pop, Pop, and… Pop; Replacing Bob's Bowl; Trap Mort the Mouse; Red Alert!; Set Off Fireworks; Punch the Bucket; Happy Second Birthday; Let Mort Out of the Box; Exercise All Four Mice; Lower the Boom; Cat-a-pulting; Put Mort in Prison; Eliminate the Balloons; Launch All the Rockets; Fire the Cannon; Break Bob's Fishbowl; Knock It Off; Ten, Nine, Eight, Ignition Sequence Start…; Mort-Trap; Shedding Some Light; Save the Bob Squad; The Wooden Shaft; Laying Down a Bunt; A Pirate's Life for Me; Chase Away the Mice; Blow Up; Balloons in Danger; Getting the Balls Together; Free Poor Pokey the Cat; Put the Balls into the Baskets; Basketball on the Moon (altered gravity); Breaking Down the Wall; Bounce, Bounce, Bounce Go the Balls; Light My Fire; Removing the Pattern; Put the Cage in the Hole; Save Mort from the Cats; Up, Up, and Away. (Full titles, goals, bins and solutions for all 160 TEMIM levels, including 88 "Tutorial: Get Mel Home" through 160 "Expert: A Long Walk", are in the agamesroom/GameFAQs guide linked above; solutions for 1–64 are also in the JustAdventure walkthrough.)

Design pattern worth copying: puzzles 1–21 each introduce one mechanism (belt/motor → mirror/flip → bellows/scissors → seesaw chain → boxing glove → trampoline aiming → jack-in-the-box → windmill → scissors/rope → gun/pulley → outlet/fan → generator/motor → solar panel/light → magnifier/fuse → dynamite → candle/light bulb → plunger → Mort/Pokey/Bob → monkey → incline sizing → conveyors as bridges). Several early puzzles ship with part of the machine pre-built so that pressing Start before placing anything already shows most of the chain reaction.

---

## 5. Open-source reimplementations, tooling and reverse-engineering resources

| Project | URL | What it contains |
|---|---|---|
| **OpenTIM** (nukep; mrfixit2001 fork has the same tree) | https://github.com/nukep/OpenTIM , https://github.com/mrfixit2001/OpenTIM | Rust + C, MIT-style. Reverse-engineered from TEMIM (Win 3.1) with Ghidra; includes the full part table (§1.1), gravity/air/terminal-velocity formulas with memory-dumped test fixtures, the tick loop, collision (polygon sweep), bounce/friction/impact math, ropes/pulleys/buckets, conveyor, gear, trampoline, seesaw, balloon, Pokey, Bob, Mort cage, walls/inclines; loaders for .LEV/.TIM levels and RESOURCE.MAP/.00x archives (LZW/LZHUF decoders, 4-bit BMP/SCN sprite decoder, TIM.PAL), a nannou renderer, and a `reverse-engineering/` folder with Ghidra scripts (labelling per-part function tables, per-level clear-condition functions, Win16 imports) plus CSVs of the in-memory Part/BeltData/RopeData structs. Many part behaviours (cannon, dynamite, fan, bellows, gun, scissors, lightbulb, magnifier, Kelly, Mel, vacuum, teapot, bumper, alligator) are still `unimplemented`. Last commit Oct 2020. |
| **ModdingWiki (knt47)** | https://moddingwiki.shikadi.net/wiki/The_Incredible_Machine_Level_Format | Byte-exact .LEV/.TIM/.GKC level format for TIM, TEMIM, Sid & Al's, TIM2, TIM3 (magic numbers, header incl. gravity/pressure/bonus/music, 48-byte part records, 52-byte belts, 54-byte ropes, 56-byte pulleys, 60-byte programmable ball, flag bits, TIM2 hints and solution records, viewport sizes). Sister pages cover the RESOURCE.MAP archive, the SCN/OFF RLE sprite format, and TIM2/3 .ANM animation files. |
| **Engie File Converter** | https://moddingwiki.shikadi.net/wiki/Engie_File_Converter | Tool listed on ModdingWiki for extracting/converting Dynamix-era resource files (TIM images among them). |
| **The Butterfly Effect** | https://github.com/the-butterfly-effect/tbe , http://the-butterfly-effect.org | C++/Qt/Box2D, GPL-2. A TIM-inspired re-imagining (80+ levels, level creator), not a faithful clone; useful for UI ideas only. |
| **sevren/The-Incredible-Machine-Game-Clone-Java** | https://github.com/sevren/The-Incredible-Machine-Game-Clone-Java | University group project, Java, runnable JAR ("TimRunner"); custom physics, custom assets. |
| **bonnal-enzo/the-new-tim** | https://github.com/bonnal-enzo/the-new-tim | 2016 student project, Java/Swing, custom multithreaded physics with rotation, ~40 levels, level editor; French comments. |
| **davidalfia/-The-Incredible-Machine** | https://github.com/davidalfia/-The-Incredible-Machine | C++ SFML + Box2D clone of *Even More Contraptions*: menus, win conditions, build mode, custom assets. |
| **cbartsch/TheIncrediblePlatformer** | https://github.com/cbartsch/TheIncrediblePlatformer | TIM/platformer mash-up, not a faithful clone. |
| **TwoBitCode — Incredible Machine** | https://twobitcode.itch.io/incredible-machine | Itch.io fan remake (browser), no source located. |
| **OSGC listing** | https://osgameclones.com/the-incredible-machine/ | Index page (lists only The Butterfly Effect). |
| **Contraption Maker** (Spotkin; Kevin Ryan + Brian Hahn) | https://store.steampowered.com/app/241240/Contraption_Maker/ | Closed-source spiritual successor; 100+ parts, Steam Workshop. Ryan's interview confirms he used Chipmunk converted to **integer** arithmetic for the same determinism reason ("floating-point resolution changes the further you get from the origin, so copy-pasted parts wouldn't run the same"). No public parts documentation beyond Steam guides was found. |
| Kevin Ryan interview | https://steemit.com/gaming/@badastroza/interesting-people-25-kevin-ryan-on-the-incredible-machine | Design history: 9 months, $37.5k, all-integer physics with hand-written integer sin/cos, bit-trick collision angle culling, engine and collision built first then "a part every few days", editor grew into free-form mode. |

Not found despite searching: any published decompilation of the DOS TIM.EXE (it is packed; OpenTIM deliberately used the unpacked Windows TEMIM build), any public Contraption Maker physics documentation, and any explicit statement of the DOS simulation tick rate.

Working files (manual texts, wikitext dumps, OpenTIM checkout, walkthrough extracts) are in `/tmp/claude-0/-home-user-the-incredible-machine/4f611904-7d89-51f1-aec7-590dcec94b8c/scratchpad/` (notably `OpenTIM/src/parts/mod.rs`, `OpenTIM/c_src/main.c`, `OpenTIM/c_src/part_defs.c`, `OpenTIM/src/atmosphere.rs`, `tim2_manual.txt`, `temim_manual.txt`, `tim1_manual.txt`, `agamesroom_temim.txt`, `fandom/*.txt`, `mw_levelformat.txt`).