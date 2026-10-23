<div align="center">
  <img width="256" height="256" alt="logoo" src="https://github.com/user-attachments/assets/4c5c4ddb-836d-4c59-8325-76b8c8d78bb3" />
  <h1>Open Launcher</h1>
  <p><strong>An open-source, offline-first Android launcher & navigation system built specifically for aftermarket car head units.</strong></p>

  [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
  [![Mapbox SDK](https://img.shields.io/badge/Mapbox-SDK_11.9.0-blueviolet.svg)](https://www.mapbox.com)
  [![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-green.svg)](https://developer.android.com/jetpack/compose)
</div>

---

## 📖 Table of Contents
- [Why Build Another Car Launcher?](#-why-build-another-car-launcher)
- [The Philosophy](#-the-philosophy-offline-first--oem-aesthetics)
- [Core Features & Architecture](#-core-features--architecture)
- [Contributing](#-contributing-open-source-first)
- [License](#-license)

---

## 🛑 Why build another car launcher?

I built this project for three simple reasons:

1. **No Premium Paywalls:** I didn't want to pay a subscription fee for basic dashboard and navigation functionality.
2. **Community-Driven:** Open-source foundation built for the car modding community to fork, extend, and improve.
3. **Clean OEM+ Aesthetics:** Designed to look clean, professional, and seamlessly integrated into a car's interior.

---

## 🧠 The Philosophy: Offline-First & OEM+ Aesthetics

Most head units rely on wireless CarPlay or Android Auto for navigation and media, meaning the head unit itself is offline most of the time.

**Open Launcher** is designed around that reality. It stores downloaded Mapbox map regions locally with **TileStore** and **Room Database**. The map can use those downloaded tiles offline; route calculation and place search still require an internet connection.

---

## ✨ Core Features & Architecture

### 🛰️ Mapbox-Powered Navigation System
* **Address and POI Search**: Suggestions use the Mapbox Geocoding API, with Nominatim and Android Geocoder fallbacks.
* **Secondary Split-Screen Search**: Search along your active route without leaving the navigation view, with automatic camera padding.
* **GPS Course-Up Camera**: The map follows the live GPS position and bearing with a 3D camera pitch.
* **Online Route Calculation**: Routes use Mapbox Directions, with an online OSRM fallback. The app does not currently provide offline route calculation or spoken turn-by-turn guidance.
* **Persistent Saved Places**: Manage saved places with Room storage and start a route with one tap.

### 🗺️ Offline Navigation & TileStore Manager
* **Shared TileStore**: The map and region manager use the same local store for downloaded map tiles.
* **Region Downloads**: Choose a listed bounding region or enter custom longitude and latitude bounds. The current UI does not search for regions by name.
* **Download Progress and Storage**: Shows tile download progress and records downloaded regions in Room.
* **Offline Map Display**: Downloaded map tiles and style resources remain available offline. Route calculation and online search do not switch to offline services.

### 🎵 Bluetooth Media Player (AVRCP / A2DP)
* **Live Metadata & Artwork**: Reads the current track, artist, duration, and artwork from active Android media sessions.
* **Playback Controls**: Provides Previous, Play/Pause, and Next controls when the media session supports them. No synthetic track is shown when no session is active.

### 🧩 Modular Widget Grid & Instrument Panel
* **Drag-and-Drop Grid**: Fully customizable home screen grid supporting custom widget sizing and positioning.
* **Purpose-Built Car Widgets**: AM/FM Radio Tuner, Speedometer, Altimeter, Trip Meter with 0-100 km/h timer, Vitals monitor, Soundboard (with custom audio pads), Dynamic Weather, and GPS Compass.

### 🌗 Smart Day/Night Theme Engine & Personalization
* **Adaptive Modes**: Forced Dark/Light, System Sync, and automated Sunset/Sunrise calculations based on offline coordinates.
* **Deep Customization**: Dynamic accent color, background gradients, wallpaper dimming, text scale, and custom fonts.

---

## 🤝 Contributing

Open-source contributions are warmly welcomed! Fork the repository, test on your hardware, open issues, and submit pull requests.

## 🔧 Mapbox Build Configuration

Provide `MAPBOX_DOWNLOADS_TOKEN` (your secret `sk.*` downloads token) and `MAPBOX_PUBLIC_TOKEN` (your public `pk.*` app token) through user-level Gradle properties or environment variables. Do not commit either token. Map rendering, search, and online directions need a valid public token; resolving Mapbox dependencies may need the downloads token.

---

## 📄 License

Distributed under the MIT License. See [LICENSE](LICENSE) for more information.
