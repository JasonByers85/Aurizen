#!/bin/bash

# Quality setting (0–10): 5 is ~128 kbps
QUALITY=6

# Loop over all .wav files in the current directory
for f in *.wav; do
    # Skip if no .wav files are found
    [ -e "$f" ] || continue

    # Define output filename
    output="${f%.wav}.ogg"

    echo "Converting: $f → $output"
    ffmpeg -i "$f" -c:a libvorbis -qscale:a "$QUALITY" "$output"
done

echo "✅ All WAV files converted to OGG."
