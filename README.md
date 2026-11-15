# Bike Dashboard

A personal fuel and servicing tracker for motorbikes, built for riding in Singapore and Malaysia.
It started as a replacement for a Google Sheet that tracked a Yamaha R3's fill-ups, and grew into a
full Android app.

> **Built mostly with Claude.** Nearly all of the code, tests and design work in this repo was written
> by [Claude Code](https://claude.com/claude-code) (Anthropic's AI coding assistant), directed and
> tested by the owner. Commits made this way carry a `Co-Authored-By: Claude` line.

Everything stays on your phone. There's no account, no server and no tracking. Your data only leaves the
device when you export a backup yourself.

## Features

**Fill-ups**
- Quick entry, one question per card: date → where → amounts → litres → odometer → fuel left.
- **Singapore:** litres (with Full Tank? and an optional brand: Shell, Esso, SPC, or Caltex and Sinopec
  under Others) → the full price, plus an optional after-discount amount to track card and membership
  discounts.
- **Malaysia:** RM paid → exchange rate (remembers your last one) → RM the pump charged, converted to SGD.
- Partial fills: economy is worked out at your next full tank.
- Warnings for values that look wrong (too many litres for the tank, odd km/L, odd price per litre),
  which you can override.

**Dashboard and history**
- Rolling week, month, 30-day and lifetime summaries: km/L, cost per KM, cost and km per day.
- Petrol savings from filling up in Malaysia, minus the trip cost.
- Charts: price per litre, monthly spend by country, KM daily, costs and savings. You can arrange or
  hide charts and cards.
- History as cards, with edit and delete.

**Servicing and paperwork**
- Service records and intervals with editable categories.
- Reminders for services that are due and for paperwork expiries (road tax, insurance and so on).
- Bike details, including importing a log card PDF.

**More than one bike**
- Separate profiles for each bike, including rentals, so they don't mix into your own numbers.
- Archive bikes you no longer ride.

**Personalisation**
- Colour presets, AMOLED black, your own primary and accent colours, and app icon choices.
- Animated backgrounds (Racetrack with six tracks, Rain on Visor with a choice of direction or one
  that follows gravity, Night Ride and more) with parallax tilt.

**Backups**
- Full JSON backup of everything, plus a CSV backup of fill-ups.
- Import from the original spreadsheet and the servicing sheet.

## Tech

- Kotlin, Jetpack Compose, Material 3
- Room (with tested migrations), SharedPreferences settings exposed as `StateFlow`s
- Vico for charts, PdfBox-Android for log card import
- minSdk 26, target/compile SDK 37, one `app` module, no DI framework

## Building

```
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug
./gradlew assembleRelease      # needs your own keystore.properties + keystore, otherwise unsigned
```

Signing keys, personal data and release APKs are deliberately left out of this repo.

## Contributing

This is a personal project, so **only the owner changes this repository**. Nobody else has push access,
and changes are never merged without the owner's review.

You're still welcome to make changes:
- **Suggest something:** open an issue with the idea or bug.
- **Change it yourself:** fork the repo, make your changes in your fork, and open a pull request. The
  owner may merge it, adapt it or decline it.
- **Keep your own version:** your fork is yours to change however you like for personal use.
