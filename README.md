# Team Nicknames

A client-side Fabric mod that shows your own nicknames in place of player names, on your screen only.
If `dr4nkalt1` pings, you see **girlscout pinged**.

It works with CosmicPings without touching it. Instead of editing that mod, it swaps names at the moment
CosmicPings draws its ping labels. Nametags, the tab list, the scoreboard and chat keep showing real names.

Nothing is sent to the server, and other mods still see the real names, so CosmicPings' own logic keeps working.
Enemies never see your nicknames. They only see `dr4nkalt1`, `dr4nkalt2`, and so on.

## Setting nicknames

**In game** (this needs Fabric API installed. It is a client command and is never sent to the server):

```
/nicks add dr4nkalt1 girlscout
/nicks remove dr4nkalt1
/nicks list
/nicks toggle      # quickly switch back to real names
/nicks reload
```

**Or edit the file** `.minecraft/config/teamnicknames.json` (on Lunar, use the Lunar profile's `config` folder):

```json
{
  "enabled": true,
  "format": "{nick}",
  "nicknames": {
    "dr4nkalt1": "girlscout",
    "dr4nkalt2": "boyscout"
  }
}
```

The file reloads by itself within about 1 second of being saved, so you don't need to restart.
To give the whole team the same names, share this one file and have everyone drop it into their config folder.

`format` sets how a swapped name is shown. `{nick}` is the nickname and `{name}` is the real name.
For example, `"{nick} ({name})"` shows `girlscout (dr4nkalt1)`, which is handy while you're setting things up.

### Where names are swapped

| Setting | Default | What it does |
|---|---|---|
| `pingsOnly` | `true` | Only swap names in text drawn by the ping mod. Set it to `false` to swap names everywhere. |
| `alsoInChat` | `false` | Also swap names in chat, which includes ping messages and every other chat line. |
| `pingMods` | `["cosmicpings","cosmic","minecoin"]` | Text used to recognise the ping mod by its class names or jar file name. |
| `debug` | `false` | Writes to the game log which mod drew a nicknamed name. Use it if the pings stop being swapped. |

Matching ignores capital letters and only matches whole names: `alt1` will not match inside `dr4nkalt1` or `alt12`.

## Building the jar (no setup needed)

1. Create a new GitHub repository and upload this whole folder, including the `.github` folder.
2. Open the **Actions** tab and wait for the **Build** run to finish (a few minutes).
3. Open the run and download the artifact for your version:
   `team-nicknames-mc1.21.11`, `team-nicknames-mc1.21.8`, or `team-nicknames-mc1.20.4`.
4. Unzip it and put the `.jar` in your mods folder. On Lunar Client, drag it into the Mods tab of
   that version's settings, the same way you installed CosmicPings.

To build locally instead, install JDK 21 and Gradle 9.2+, then run:

```
gradle build -Pminecraft_version=1.21.8 -Pfabric_api_version=0.136.0+1.21.8 -Pjava_version=21
```

The jar ends up in `build/libs/`.

## Known limits

- If a server colours each letter of a name separately, the name is split into pieces and won't match.
  Normal rank-coloured names work fine.
- On 1.20.4, text that a mod draws as a plain string, rather than as a chat component, is not swapped.
  On 1.21.6 and newer, all of it is swapped.
- Wrapped chat lines are measured with the real name. A very long nickname can make a line run a few pixels wider.
