# Release v0.0.2: The Layout & Theme Engine Update

This update brings a massive overhaul to the core architecture. We've moved past the initial concept stage—v0.0.2 is almost completely overhauled and fully functional as a daily driver. 

The focus for this release was giving you total layout flexibility and fixing one of the most annoying issues with aftermarket hardware: broken or missing Day/Night system triggers.

---

## ✨ What's New

### 🛠️ Total Sidebar Overhaul
You are no longer locked into a left-side layout. You can now completely customize the positioning of the main control bar to fit your car's interior layout or your driving hand:
* **Dock Positions:** Move the sidebar to the **Left, Right, or Bottom** of the screen.
* **Alignment Control:** Choose which side you want the actual shortcut icons to show up on within that bar.

### 🧩 True Drag & Resize Grid
The Home Screen dashboard has been entirely rebuilt. It now behaves like a fully-fledged Android launcher. You can now freely **drag, drop, and resize all icons and widget panes** to craft the exact cockpit layout you want. 

### 🌗 Smart Day/Night Theme Engine
The settings menu has been streamlined to include a polished dark and light mode toggle setup. Because many aftermarket head units fail to pass the car's headlight toggle to the OS, we built four distinct ways to handle this:
* **Forced Dark Mode / Forced Light Mode:** Standard static overrides.
* **System Sync:** Follows the native Light/Dark theme setting of the head unit itself.
* **Sunset Mode (Custom Offline Logic):** Automatically switches themes based on local sunset/sunrise timings—perfect for head units with broken hardware triggers.

### 📊 New Telemetry Widgets
We've added two crucial offline tracking tools to the dynamic dashboard (Works on some devices for now):
* **Speedometer:** Real-time speed tracking pulling directly from the device GPS.
* **Altimeter:** Live elevation data tracking.

---

<div align="center">
  <i>Open source and built for the drive. Check the Issues tab to report bugs or submit feature requests!</i>
</div>