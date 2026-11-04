# Library file operations

The Library long-press menu provides **Add to playlist**, **Rename with
BPM**, **Exclude from library**, and **Delete**. These actions use Android's
Storage Access Framework (SAF), never direct filesystem path manipulation.

## Current

The **Current** button scrolls Library to the song that is presently
playing, without changing playback or selecting another song.

## Exclude from library

You choose the **destination folder itself**, not its parent. Example:

```text
Library:      Internal storage/Music/jazz/
Track:        Internal storage/Music/jazz/aaaa/music.mp3
Destination:  Internal storage/Music/excluded/
Result:       Internal storage/Music/excluded/music.mp3
```

Create the destination folder in a file manager first if it does not exist.
On the first Exclude action, Android's folder picker requests access to that
folder. Later Exclude actions offer two choices:

- **Use saved folder** to move to the previously chosen location.
- **Choose another folder** to change the destination whenever needed.

An old saved parent-folder setting is **ignored** after this update, so
previous incorrect selections do not trap you in repeated errors.

For safety, the app rejects choosing the **Library root itself** as
destination. Choosing a separate folder outside Library is recommended.
If a selected destination is inside the scanned Library tree, that
specific folder is omitted from future scans by its SAF document identity;
directories literally named `excluded` are always skipped. File
paths/SAF document IDs are never split to guess parent access.

### What "move" means

SAF providers expose file access via URI permissions; a move to a
separately authorized tree is implemented by:

1. Confirming the action and checking read/write grants.
2. Refusing to overwrite an existing destination filename.
3. Streaming a copy on a background worker.
4. Closing it, reading it back, comparing its byte count and SHA-256.
5. Rechecking that the original did not change during the copy.
6. Only then asking Android to delete the original.

If an error occurs **before** source deletion, the original remains.
If copying succeeds but the provider declines deletion, the verified
destination copy might also remain. You will receive an error and can
resolve that duplicate using a file manager.

## Delete from device

**Delete** shows a distinct confirmation and asks the SAF storage
provider to delete the original audio document. Some providers may
implement a trash/recycle facility. This is a real storage operation,
not merely hiding a row from Library.

## Metadata and playlists

After successful Delete/Exclude, the app removes the original URI from:
the local Library snapshot, active playback queue, saved playlists, manual
and automatically detected BPM records, and generated waveform cache.
If the removed file was playing, playback stops cleanly.

The folder exclusion setting persists for the currently selected music
library; selecting a different library resets this setting.

**Test both operations on disposable MP3/FLAC copies before using your
actual music collection.** Some cloud providers do not implement every SAF
operation consistently.
