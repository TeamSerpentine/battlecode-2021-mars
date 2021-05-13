# Battlecode 2021 M.A.R.S Team

<img src="https://serpentine.ai/wp-content/uploads/2019/02/Final-design-serpentine.png" width="400px">

This is the repository of the M.A.R.S. (Ministry of Alien RattleSnakes) team
from [E.S.A.I.V. Serpentine](https://www.serpentine.ai), containing the competition code for Battlecode 2021.

<img src="https://serpentine.ai/wp-content/uploads/2021/05/marswiki.png" width="400px">

For the technical report for this competition and more, check out
the [publications](https://serpentine.ai/publications/) from Serpentine.

Team Members: Dik van Genuchten, Koen Ligthart, Max de Louw, Gijs Pennings

### Useful Commands

- `./gradlew run`
  Runs a game with the settings in gradle.properties
- `./gradlew update`
  Update to the newest version! Run every so often

### Project Structure

- `README.md`
  This file.
- `build.gradle`
  The Gradle build file used to build and run players.
- `src/mars`
  M.A.R.S source code.
- `test/`
  test code.
- `client/`
  Contains the client. The proper executable can be found in this folder (don't move this!)
- `build/`
  Contains compiled player code and other artifacts of the build process. Can be safely ignored.
- `matches/`
  The output folder for match files.
- `maps/`
  The default folder for custom maps.
- `gradlew`, `gradlew.bat`
  The Unix (OS X/Linux) and Windows versions, respectively, of the Gradle wrapper. These are nifty scripts that you can
  execute in a terminal to run the Gradle build tasks of this project. If you aren't planning to do command line
  development, these can be safely ignored.
- `gradle/`
  Contains files used by the Gradle wrapper scripts. Can be safely ignored.

# Acknowledgements

Forked from the [MIT BattleCode 2021 Scaffold](https://2021.battlecode.org/getting-started).

[E.S.A.I.V. Serpentine](https://www.serpentine.ai) is a student team from
[Eindhoven University of Technology](https://www.tue.nl).
