# TV Material Redesign: "Cinematic Overhaul" Plan

This plan outlines the complete restructuring of the TV interface using a "TV-First" philosophy. We will move away from mobile-inherited layouts and build a high-performance, D-pad optimized architecture from the ground up.

## User Review Required

> [!IMPORTANT]
> **Complete Screen Separation**: Each feature (Live, VOD, Settings) will be moved into its own dedicated package. The old `TvLiveTvScreen` etc. will be replaced by a cleaner structure.
> **Logic Preservation**: We will keep your existing `ViewModels` (LiveViewModel, MovieViewModel, etc.) but strip away all UI-specific "mobile logic" from them to ensure the TV layer is pure.

## Proposed Structural Changes

### 1. The Shell & Sidebar (Global Controller)
The `TvMainShell.kt` will act as the master container.
- **SideNavRail**: A collapsible left sidebar that is always accessible.
- **Focus Rule**: Pressing **D-Pad Left** from any screen's edge will smoothly slide focus into the Sidebar.
- **Transitions**: Screens will use a "Fade + Slide" transition when switching sections from the sidebar.

---

### 2. Home Screen: "The Dashboard"
- **Hero Metadata (Fixed)**: A top-left card for the currently highlighted item (Title, Star Rating, Description).
- **Theater Backdrop**: The background artwork will use a "Cinema Gradient" that darkens behind text for readability.
- **Horizontal Rows**: Optimized horizontal lists for "Trending" and "Continue Watching."
- **Focus Rule**: Focus lands on the Sidebar on startup. Pressing **Right** jumps to the first Trending item.

---

### 3. Live TV Screen: "The Triple-Pane"
- **Group Shelf (Pane 1)**: Vertical list of channel categories.
- **Channel Grid (Pane 2)**: A secondary vertical list that updates only when a Group is "Entered" (Clicked).
- **Preview Hub (Pane 3)**: Fixed on the right. Shows the video preview + current EPG program.
- **Focus Rule**:
    - Groups -> (Press Right) -> Channels.
    - Channels -> (Press Right) -> Preview Fullscreen action.
    - Channels -> (Press Left) -> Groups.

---

### 4. VOD Hub (Movies & Series): "The Gallery"
- **Sticky Filter Header**: A horizontal bar at the top for Categories/Genres.
- **High-Density Grid**: A 5 or 6 column grid of posters.
- **Focus Rule**: Focus stays on the Grid. Scrolling up past the first row moves focus to the Category Header.

---

### 5. Unified Components (`ui/tv/components/`)
We will create a library of standardized TV parts:
- `TvPoster`: Optimized card with focus scale, glow, and title overlay.
- `TvBadge`: High-contrast rating and resolution chips.
- `TvDivider`: Vertical/Horizontal lines to separate panes without adding "weight."

## Verification Plan

### Automated Build Check
- Run `./gradlew :app:assembleDebug` after each screen rewrite to ensure no broken references.

### Manual UX Walkthrough
1. **The "Left Edge" Test**: Verify that the sidebar opens reliably when pressing Left from any list.
2. **The "Preview Punch" Test**: Ensure 4K video is visible through the UI layers in Live TV.
3. **Focus Loop Test**: Ensure focus never gets stuck or "disappears" off-screen.

---

## Open Questions
- **Settings**: Would you prefer the Settings to be a full-screen multi-tab page (Standard TV style) or a sliding overlay from the right?
- **Login**: Should the Login screen also be redesigned for TV with a QR code focus (Phone Login)?

**Shall I proceed with this architectural split?** I will start by creating the new directory structure and the SideNavRail.
