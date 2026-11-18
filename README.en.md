# TickCount

[![Build](https://github.com/baishadi123/tickcount/actions/workflows/android.yml/badge.svg)](https://github.com/baishadi123/tickcount/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A minimal countdown calendar for Android. Tap a day, give it a name, and
see how long is left — or how long it has been.

[Chinese](README.md)

**No ads · No account · Open source**

<p align="center">
  <img src="docs/screenshot-home.png" width="200" alt="Home: days left for each countdown, the past below" />
  <img src="docs/screenshot-calendar.png" width="200" alt="The calendar and that day's countdowns" />
  <img src="docs/screenshot-widgets.png" width="240" alt="Both home-screen widgets" />
</p>

## Features

- **Countdown list** — one card each: days left set large, with name, tags and date; the past sits under a **Past** heading; a line at the top says what falls today or tomorrow.
- **Pinning and sorting** — a pinned countdown leads the list; sort by date (nearest/furthest) or date added.
- **Search and filters** — search name, date or time; pills narrow to the next 7 days or one tag.
- **Exact minute** — all day, or set to an exact hour and minute.
- **Tags and colours** — as many tags as you like, and a colour mixed on a hue strip.
- **Countdown screen** — tap a card for the full y/mo/d/h/m/s breakdown, pin switch, date and tags.
- **Calendar** — a hand-drawn month grid with dots for busy days; tap a day to list its countdowns, with the same search and sort. Tap the month title for a year-and-month picker.
- **Long-press a card** — pin, edit or delete without opening it; deletes confirm first.
- **Lunar dates** — pick a countdown on the lunar calendar (1900–2100, leap months included); the lunar day is named after the solar one.
- **Time capsules** — letters with photos and a voice note, sealed until a day; a letter can be re-sealed to a new date. A wall of voice tapes records her voice, plays it back with real tape reels, and imports audio or video (the audio track is kept).
- **Wishes, footprints, the voyage** — a wish list to tick off; places visited together, on a map; a star chart where milestones light up, with music.
- **One picture behind everything** — pick a photo once and it sits behind every page, framed by hand.
- **Notifications** — a reminder time, one app-wide lead, an override per countdown, a test button; reminders return after a restart.
- **Appearance** — six themes or a mixed hue (paper or night steep); glass-surfaced cards and panels throughout.
- **Bilingual** — Chinese or English, following the system or chosen in the drawer; date formats localised.
- **Backup and restore** — every record and setting to a file and back, optionally AES-256-GCM encrypted; an import asks **Overwrite** or **Merge** and says what each would do first.
- **Large fonts** — the UI grows with the system font scale; nothing is squeezed.

## Adding several at once

The name, tags, colour and time are answered once; switch on "Add several at once" and two numbers remain: **Days between** (1–3650) and **How many** (1–100). The chosen date is added as well, each entry that many days after the last. The time of day is carried by every entry; they count as one batch when sorted by date added, and each is an ordinary countdown afterwards.

## How the countdown reads

The target is **00:00 local time on the chosen date**, or its exact time of day. Once passed, the same arithmetic runs backwards — "Time left" becomes "Time since", and "Elapsed today" on the day itself. Leading fields not yet counting are dashes:

| Time left | Shown as |
|---|---|
| 4 months, 11 days, … | `----y 04mo 11d 06h 30m 15s` |
| 2 days and 30 seconds | `----y --mo 02d 00h 00m 30s` |
| 3 years, 4 months, … | `0003y 04mo 11d 06h 30m 15s` |

Years, months and days count on the calendar; hours, minutes and seconds as elapsed time — a daylight-saving day still counts as a whole day.

## Home-screen widget

Long-press an empty spot on the home screen → Widgets: two to choose from.

**Single countdown** — pick a countdown (the picker shows its date and time); it ticks to the second, rearranges as you resize it, and tapping opens that countdown. The seconds are driven by the system itself, without waking the app.

**Countdown list** — nothing to pick: what is coming next, nearest first, one per row; a timed countdown says so after the days. The default 4×2 holds three rows, a taller one four.

Both take their panel — light, dark or the system's — from the Home-screen widgets page; the list also has a text size of its own.

## Download

APKs are published in [Releases](../../releases/latest). What changed in each version is in [`更新日志.txt`](更新日志.txt).

## Building

JDK 17; Android SDK Platform 37 and Build Tools 36.0.0 (`local.properties` or `ANDROID_HOME`).

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleRelease     # APK -> app/build/outputs/apk/release/
```

On Windows, use `gradlew.bat`.

## Built with

| | |
|---|---|
| Language | Kotlin 2.4.20 |
| UI | Jetpack Compose + Material 3 (Compose BOM 2026.09.00) |
| Build | AGP 9.4.1, Gradle 9.6.0, versions in `gradle/libs.versions.toml` |
| SDK | compileSdk 37, targetSdk 36, minSdk 27 |
| Dependencies | AndroidX, Compose, osmdroid (map), androidx.exifinterface |

`java.time` arrived in API 26 and the theme's `windowLightNavigationBar` in 27, so the project needs neither desugaring nor a split theme.

The layout: `data/` holds the models and stores, `domain/` the pure tested logic, `ui/` the screens, `notify/` reminders, `widget/` the home-screen widgets — see `AGENT.md` for the feature-to-file table. The arithmetic, ordering, search, widget splits, changelog parsing, backup codec, encryption, README extraction and the Markdown parser are all unit-tested, including leap days, year boundaries and both daylight-saving changes.

## App icon

Vector XML cannot draw Chinese characters, so the lettering is rasterised by [`.tools/图标生成/IconGen.java`](.tools/图标生成/IconGen.java):

```bash
java .tools/图标生成/IconGen.java app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png
```

## Privacy

TickCount keeps its records — countdowns, wishes, capsules, footprints, voice tapes and settings — in its own private directory, and nothing leaves the device on its own. A backup goes wherever you choose, encrypted or not; Android's own cloud backup follows `res/xml/backup_rules.xml`.

## Licence

[MIT](LICENSE).
