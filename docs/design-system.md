# Zhan Zhuang design system

## Brand roles

Warm paper (`#FBF8F1`) is the primary reading surface. Deep soil (`#15110B`) and soil (`#241C12`) provide the icon field and high-emphasis dark surfaces. Reflective gold is reserved for identity and primary actions, using the ordered finish `#E7D5A6 → #C6A867 → #A9863F → #D8C18C`; it is never a large flat panel. Sparkle is `#FFF4D6` and appears once, as one restrained four-point mark in the launcher icon.

Use approximately 70% warm/soil surfaces, 25% quiet tonal containers, and at most 5% gold/sparkle emphasis. Pair every semantic foreground and surface for readable contrast; primary controls use dark ink on gold, while body text uses a soil tone on paper.

## Shape, motion, and touch

The radius scale is 6, 10, 16, and 24 dp (24 dp for pill actions). Interactive targets are at least 48 dp in both dimensions. The sparkle animation is one-shot (280–420 ms) only at start or completion. It is suppressed in ambient mode, power saver, and reduced-motion/disabled animator conditions; it never loops.

## Adaptive layout and accessibility

Phone content respects system insets and retains 20 dp horizontal breathing room. Round Wear content keeps a 18 dp horizontal and 16 dp vertical safe zone, is scrollable/rotary reachable, and does not put essential content at the rim. Text uses Material scalable roles rather than fixed layout assumptions; controls and timer state have meaningful TalkBack labels and state descriptions. Large font scale must retain the primary action without clipping.

Use default English and `zh-rCN` resources with exact key parity. Do not concatenate translated sentence fragments; use formatted resources. Heart rate is described only as optional informational session data. Copy contains no medical, weight-loss, paid-tier, or advertising claims.

## Launcher mark

Both apps use adaptive, round, legacy foreground/background, and monochrome launcher assets. The mark is a centered standing gold figure holding one gold-and-soil taiji at chest height on a deep-soil circle, with exactly one four-point sparkle and no text. The hands meet the single taiji at its sides and stop above its lower edge; they must never create a second ball outline or ring. The 108 dp adaptive foreground preserves generous optical margins so the silhouette remains inside Android and round Wear masks.
