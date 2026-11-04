# BPM Player branding and Google Play materials

Based on the original green triangular play / waveform / BPM scale sketch.
The three greens, two internal waves, diagonal BPM mark and right-side tempo
ticks are preserved. The launcher uses a simplified Android VectorDrawable
rather than font-dependent SVG text, so it works at any display density.

## Android launcher
- `app/src/main/res/values/strings.xml` sets `BPM Player`.
- `app/src/main/res/drawable/ic_launcher_foreground.xml` is a vector logo.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher*.xml` are adaptive icons.
- Android minSdk is 26; legacy bitmap launchers are not required.
- The application ID remains `com.wombatriy.androidplayer` to avoid losing
  installed data and to preserve future upgrade paths.

## Main store listing
- Editable masters: `branding/source/*.svg`.
- Rendered PNGs: `branding/play-store/main_store_icon_512.png` (512×512)
  and `branding/play-store/feature_graphic_1024x500.png` (1024×500).
- English/Russian listing text in `branding/play-store/*.txt`. Each short
  description is at most 80 characters; the descriptions make no promise
  of streaming or online music downloads.

To regenerate PNGs after editing the SVGs:

```powershell
python -m pip install cairosvg
python branding/generate_assets.py
```

Before Google Play submission check actual device icon mask and the artwork in
Play Console preview. Feature graphic and main icon require PNG export;
Play Console does not accept SVG in those fields.

These new graphics do not affect the Rubber Band licence. An R3-enabled
release distributed to friends still requires appropriate GPL source-package
and notice compliance or a commercial licence.
