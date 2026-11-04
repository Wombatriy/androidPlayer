## Library: Current, permanent delete and Exclude

The Library toolbar has a **Current** button that scrolls to the song
currently playing without switching tabs or restarting it.

Long-press a Library track for four actions: Add to playlist, Rename with
BPM, Exclude from library, and Delete permanently. **Delete permanently**
requires a separate confirmation and invokes Android's Storage Access
Framework delete operation; behavior such as trash/recycle handling is
ultimately controlled by the selected storage provider.

**Exclude from library** physically moves a selected audio file out of
the indexed source directory. The first time, choose a writable **PARENT**
of your library folder in Android's system file picker. Example:

    Music Library: /android/abc/downloads/music/jazz/
    Choose parent: /android/abc/downloads/music/
    Result:        /android/abc/downloads/music/excluded/track.mp3

The application creates the sibling `excluded` directory if missing.
The selected parent's SAF grant is remembered for later exclusions, but is
reset if you choose a different library source. Android's SAF tree permission
for `jazz/` never grants automatic write access to sibling `excluded/`.
If you select the wrong parent, cancel and use Android's file manager to
move the file back before retrying.

To protect your music, Exclude does not delete the original until the new
file has been **fully copied, closed, re-read and SHA-256 verified**. It
refuses a duplicate filename and does not overwrite a destination file.
The move runs on a background worker. If verification fails, the original
remains. If the copy was verified but Android rejects deletion, the valid
copy may remain in `excluded/` as well; the app shows a failure message.

After successful deletion/exclusion the track is removed from Library cache,
all saved playlists, playback queue, stored BPM overrides/detections, and
waveform cache. If the removed song was playing, playback stops; the user
can pick another track from Library or Queue. Batch BPM analysis and folder
scans cannot overlap destructive operations.

A directory named `excluded` (case-insensitive) is always skipped during
recursive Library scanning. Therefore files remain hidden even when the
user later chooses a parent music directory as Library root.

**Important:** Exclude and Delete modify real files on the device. Test with
disposable copies first. Never use Delete on irreplaceable recordings. Some
cloud-backed/storage providers can differ in their SAF implementation.
