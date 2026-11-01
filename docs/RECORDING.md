# Recording the demo GIF

`docs/demo.gif` is currently a placeholder. To replace it with a real recording:

1. Install the release APK on a phone and let the model download.
2. Turn on **airplane mode** (it is the point of the demo) and start the screen recorder.
3. Show: the empty chat → tap a suggestion → the answer streaming in → the tok/s chip → **Stop** on a long answer.
   Keep it under 15 seconds.
4. Convert to a GIF about 300 px wide, under 5 MB, for example:

   ```bash
   ffmpeg -i demo.mp4 -vf "fps=12,scale=300:-1:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse" docs/demo.gif
   ```

5. Commit it and remove the placeholder note under the image in the README.
