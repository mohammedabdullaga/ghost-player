# Phased UI Porting: Unified Professional Design with ObjectBox Architecture

This plan covers the transformation of the current app's UI to match the professional **Mobile** design of `my-iptv-app` for **both Mobile and Android TV**, using the new ObjectBox-based data architecture.

## Strict Architectural Rules
1. **Unified UI Base**: Extract ONLY the Mobile UI designs, layouts, and components from the old app. Use this Mobile foundation for both platforms.
2. **D-Pad Adaptation**: Adapt ported Mobile Compose screens for TV by adding `FocusRequester`, `focusProperties`, and `Modifier.focusable()`. Ensure grids, sidebars, and player controls are flawlessly navigable with a TV remote.
3. **Architecture Integrity**: NO imports or references to old database logic, old DAOs, or old repositories.
4. **Data Mapping**: All UI components must accept state from the NEW ObjectBox ViewModels using new entities (`LiveStream`, `Movie`, `Series`, `Category`).
5. **Phased Review**: Execute step-by-step with mandatory user checkpoints.

## Phase 1: Foundation & Home [DONE]
Port the core styling and the main dashboard layout.

### [Step 1.1] Styling Tokens [DONE]
#### [NEW] [AppColors.kt](file:///C:/Users/User/Desktop/ElitPrime-after-vpn-beforeTV/new-iptv-app-mobile-and-tv/app/src/main/java/com/eliteprime/app/ui/theme/AppColors.kt)
Ported the exact hex codes and palette from the old app's mobile theme.

### [Step 1.2] Professional Home Screen [DONE]
#### [MODIFY] [MobileHomeScreen.kt](file:///C:/Users/User/Desktop/ElitPrime-after-vpn-beforeTV/new-iptv-app-mobile-and-tv/app/src/main/java/com/eliteprime/app/ui/screens/home/MobileHomeScreen.kt)
Ported the Hero Card layout.
- Used `DashboardViewModel` (new) for data (Live, VOD, and Series counts from ObjectBox).
- **D-Pad Adaptation**: Added focus handling and scaling to Hero Cards for TV navigation.

### [Step 1.3] Main Shell Overhaul [DONE]
#### [MODIFY] [MainShell.kt](file:///C:/Users/User/Desktop/ElitPrime-after-vpn-beforeTV/new-iptv-app-mobile-and-tv/app/src/main/java/com/eliteprime/app/ui/MainShell.kt)
Transitioned to an adaptive shell based on the old Mobile navigation:
- Mobile: Custom TopBar navigation (moved inside screens).
- TV: Professional adaptive layout.

## Phase 2: Live TV & Content Browsing [DONE]
Ported the high-end browsing experience.
- UI: Ported `MobileLiveTvScreen.kt`, `MovieScreen.kt` and `SeriesScreen.kt` using the unified three-pane design.
- Logic: Wired to `LiveViewModel`, `MovieViewModel` and `SeriesViewModel` (ObjectBox).
- **D-Pad Adaptation**: Implemented explicit focus handling and highlight effects for all content grids and category sidebars.

---
**STOP: Phase 2 is complete. Requesting review.**

## Phase 3: Advanced Player Engine & Settings [DONE]
Ported the high-end player and settings.
- Player: Ported `MobilePlayerControls.kt` and updated `PlayerScreen.kt`.
- Settings: Ported `SettingsScreen.kt` with the professional sidebar design.
- **D-Pad Adaptation**: Optimized on-screen controls and sidebar navigation for remote interaction.

---
**COMPLETED**: All phases of the professional UI porting are finished.

