I want to make improvements to the AntennaPod app to help me save time by listening to episode summaries and skipping to specific sections/topics. 
Here are the more specific requirements:
- When downloading all new podcasts, create ~1 minute summaries (length configurable) for each one with audio narration (prompt to create the summary should also be configurable). Also for each podcast episode if it is already broken into different chapters via timestamps in the notes, save the titles and timestamps. Otherwise use AI to determine what topics are covered at what timestamps and save that somewhere.
- When playing each podcast episode on the playlist, for the main audio player screen (for both phone and android auto) first show a summary page and automatically start playing the 1 minute episode summary
- The summary page should contain the podcast name, the title of the podcast episode, and the list of topics prepended with their start timestamps. If the user clicks on a topic, switch to the standard audio player screen and start playing that portion of the podcast. Once that specific portion finishes don't continue playing the rest of the episode but return back to the summary page.
- The summary page should also show a button to skip to the next podcast which the user can click at any point. There also should be an X button where we can close the summary page, stop playing the summary audio, and just proceed to playing the podcast episode as normal.
- when removing a podcast episode, remove the episode summary and all associated topics data
- Use the following APIs for the summaries (keys are user-configurable in settings, never committed):
  - GROQ_KEY=(enter in settings)
  - GEMINI_KEY=(enter in settings)
- Add a section to the settings for all the things listed here that should be configurable.
- Make sure the android auto page is appropriate for driving, making text and buttons large enough to be easily clickable.
