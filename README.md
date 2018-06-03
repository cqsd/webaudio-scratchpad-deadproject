## Chipper
In-browser chiptune tracker.

Build with `lein cljsbuild once min`, load `resources/public/index.html`.

### Controls
tl;dr just press i and use arrow keys

#### Normal Mode
| Key                  | Effect                          |
|----------------------|---------------------------------|
| `hjkl/←↓↑→`          | left, down, up, right           |
| `}{`                 | forward/back one page           |
| `tab`/`shift+tab`    | right/left one channel          |
| `gG`                 | go to first/last line           |
| `x`                  | delete note under cursor        |
| `i`                  | switch to insert mode           |

#### Insert Mode
| Key                  | Effect                          |
|----------------------|---------------------------------|
| `a..j`               | enter notes C..B                |
| `w..u/shift+a..j`    | enter accidentals               |
| `x`                  | insert "channel off" directive  |
| `X`                  | insert "stop playback" directive|
| `escape`             | switch to normal mode           |
