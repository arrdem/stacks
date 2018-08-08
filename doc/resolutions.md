# Screen Resolution

These are screen resolution metrics pulled from Grimoire, used to shape how Stacks renders content to try and serve developers as my existing tools profile their usage patterns.


| resolution | percentage |
|------------|------------|
| 1920x1080  | 17%        |
| 1680x1050  | 11.48%     |
| 1440x900   | 10.36%     |
| 800x600    | 8.12%      |

The latest published Steam survey suggests that 1366x768 is the most widely available desktop resolution.

Grimoire (http://conj.io) always failed in presenting content to mobile users.
The format was simply too desktop oriented, and while the cheatsheet page was fairly responsive and would behave on smaller screens, the documentation pages failed in a number of critical ways when screen widths got small.

Stacks attempts to be mobile friendly, maintaining the same widescreen oriented layout with some scaling modifications down to fairly low resolutions.
My testing gold standard for Stacks is iPad (768x1024).

Wider screens are supported with scaling up for the most part, although this means that the current content layout starts to fail around 2kpx wide.
There simply isn't a good way to render that much text width, and even with larger font sizes legibility is at least to my eyes sacrificed.
