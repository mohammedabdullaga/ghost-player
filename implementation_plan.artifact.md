# Implementation Plan: Redesign TvSeriesDetailsScreen

Redesign the TV Series Details screen to match the provided UI design. This includes a full-screen backdrop, a top section with series info and actions (Play, Fav, More), and a bottom section for season and episode selection.

## User Review Required

> [!IMPORTANT]
> The design specifies a specific layout for the "Play" button and metadata. I will align it as described: Play button on the left, metadata (Genre, Date, Rating, Duration) on the right of it, and Overview below the metadata.

## Proposed Changes

### UI Layer

#### [MODIFY] [TvSeriesDetailsScreen.kt](file:///C:/Users/User/Desktop/ElitPrime-after-vpn-beforeTV/new-iptv-app-mobile-and-tv/app/src/main/java/com/eliteprime/app/ui/tv/screens/TvSeriesDetailsScreen.kt)
- Restructure the layout to use a vertical `Column` within a `verticalScroll`.
- Implement `SeriesHeaderSection`:
    - Full-screen backdrop handling.
    - Title at the top.
    - Action Row: Play button (with "Play Episode 1" or "Resume EX" logic) + [Fav, More] buttons.
    - Metadata Column: Genre, Release Date, Rating, Duration + Overview.
- Implement `SeasonsSection`:
    - Horizontal list of season tabs.
- Implement `EpisodesSection`:
    - Horizontal list of large episode cards.
    - Episode Card details: Thumbnail, Progress bar, "Watched" badge, Episode number/title, Date, Duration.

## Verification Plan

### Manual Verification
- Deploy to a TV emulator or device.
- Verify the layout matches the description:
    - Backdrop is visible.
    - Play button text correctly reflects watch history.
    - Seasons are selectable.
    - Episode cards show progress and status.
    - Vertical scrolling between details and seasons/episodes is smooth.
