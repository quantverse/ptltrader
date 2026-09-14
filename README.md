# PTL Trader

PTL Trader is a lightweight, cross-platform software which makes automated (or semi-automated) trading of complex U.S. equity pair strategy portfolios possible. It currently supports the [Interactive Brokers](https://www.interactivebrokers.com) as data & execution backend. 

PTL Trader was originally built as a companion to [Pair Trading Lab](https://www.pairtradinglab.com/), which let you assemble, analyze and backtest equity pairs and pair trading strategy portfolios, and previously supplied all portfolio and strategy configuration over its web API. Pair Trading Lab is shutting down, so PTL Trader now owns its data locally: portfolios, strategy parameters and trade history live in a local database instead of on a server, and you manage them directly in the application (import, export, create, delete, add a pair).

First proprietary version of PTL Trader was released already in 2013 and got gradually field tested in hundreds of instances trading live accounts of PTL's clients. It is already considered mature and well-tested. Since 2021 and version 1.6.0 it is now a free, open source software.

[More Information About PTL Trader](https://www.pairtradinglab.com/ptltrader)

## Migrating from Pair Trading Lab

If you still have portfolios in the Pair Trading Lab Portfolio Manager, **export each one as a JSON file before the service closes** — once it is down there is no way to retrieve them from PTL. Then, in PTL Trader, use **File › Import Portfolio…** to load the file. Importing always creates a new portfolio with fresh internal IDs and an unbound account; nothing is overwritten, so importing the same file twice just gives you two copies to tidy up with **File › Delete Portfolio**.

There is no in-application migration from the old service — the export on the PTL website and the import here are two separate, manual steps, and the export must be done while Pair Trading Lab is still running.

Export **JSON**, not CSV: the CSV export is lossy and cannot be imported. The JSON file format is documented in full in [IMPORT.md](IMPORT.md), so that portfolio files can still be written or generated once the website is gone.

## Data storage

PTL Trader keeps all of its own data — portfolios, strategy configuration, strategy runtime state and trade/leg history — in a local SQLite database. It belongs in your backups.

The database and the log file live together in a platform-specific per-user data directory:

| Platform | Path |
|---|---|
| Windows | `%LOCALAPPDATA%\PTLTrader` |
| Linux | `~/.local/share/ptltrader` |
| macOS | `~/Library/Application Support/PTLTrader` |

Each profile (see `args[0]` under [Running PTL Trader](#running-ptl-trader)) gets its own `<profile>.db` and `<profile>.log` in that directory. PTL Trader already refuses to start a second instance for the same profile, so the existing single-instance-per-profile rule also guarantees there is only ever one process writing to a given database.

## Support

This software comes with no guaranteed support whatsoever. If you have troubles with the software, you can use the [Pair Trading Lab Forum](https://forum.pairtradinglab.com/) to get the community help. You can also use the Helpdesk (part of the forum mentioned above), but please note that support is not guaranteed and not part of any paid subscription.

Please read the [FAQ](https://www.pairtradinglab.com/faq) before reporting anything.

If you have issues with the build, it is better to fill an issue here on GitHub.

Pull Requests are welcome but prepare for a strict review process, as this software trades other people's money.

## Documentation

For end users, see the [PTL Trader Manual](https://wiki.pairtradinglab.com/wiki/PTL_Trader_Manual).

For developers:

* [IMPORT.md](IMPORT.md) — the portfolio import/export JSON file format: every field, its accepted values, and what the importer validates.
* [ARCHITECTURE.md](ARCHITECTURE.md) — runtime topology, components, threading model and the flows that connect them.
* [TECHNICAL.md](TECHNICAL.md) — toolchain, source layout, configuration, external interfaces, trading model reference, engine status codes, safety rules, testing and packaging.

## Building PTL Trader

PTL Trader is written in Java using the [Gradle Build Tool](https://gradle.org/). It is based on [Standard Widget Toolkit](https://www.eclipse.org/swt/) to allow running on multiple platforms: Microsoft Windows (x64), Linux (GTK, x64) and macOS (x64). You still need to build a specific binary (JAR) for each platform though. 32bit Windows is not supported anymore.

### Prerequisites

* [Java Development Kit 11](https://adoptium.net/temurin/releases/?version=11) - yes, for building you need this older JDK, the application then of course runs on newer Java versions as well

### Building the App

To build fat application JARs for all platforms just use the provided script `build_all_architectures.sh` or just use `./gradlew shadowJar -PforceArch=<your_arch>` to build for just a single platform of your choice.

`./gradlew run` will just build the software and run it for your current platform.

Application JARs will be generated in `build/libs` folder. 

This software is covered with a bunch of unit tests. These are build and executed using `./gradlew build` command:

```
> Configure project : 
> Task :compileJava 
Note: /home/carloss/Projects/ptltrader/src/main/java/com/pairtradinglab/ptltrader/Application.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
Note: Some input files use unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.


BUILD SUCCESSFUL in 21s
12 actionable tasks: 10 executed, 2 up-to-date
```

## Running PTL Trader

You need to have 64bit Java JRE/JDK installed first, at least version 11. Then you can use:

```
java --add-opens java.base/java.net=ALL-UNNAMED --add-opens=java.base/sun.security.util=ALL-UNNAMED -jar <your generated application JAR.jar>
```

Under macOS you need to provide extra parameter:

```
java --add-opens java.base/java.net=ALL-UNNAMED --add-opens=java.base/sun.security.util=ALL-UNNAMED -XstartOnFirstThread -jar <your generated application JAR.jar>
```

Please note the section Windows Extras for more information about running PTL Trader under Windows.

You will need a running instance of [IB Trader Workstation (TWS)](https://www.interactivebrokers.com/en/index.php?f=14099) or [IB Gateway](https://www.interactivebrokers.com/en/index.php?f=16457) so PTL Trader can receive market data and submit orders to your IB account.

Market data subscriptions are [required](https://www.pairtradinglab.com/faq#ptltrader-market-data-us) as well.

## Windows Extras

You need to have either Java JDK 11+ or JRE 11+ installed to be able to run PTL Trader under Windows. We recommend to install the [Eclipse Temurin JRE from Adoptium](https://adoptium.net/temurin/releases/).

### Wrapping Application to Single EXE File

You can optionally use [launch4j](http://launch4j.sourceforge.net/) to wrap the generated JAR (win64 arch) to a single EXE if you want. Use the launch configuration in the `launch4j` folder.

Note: launch4j is a cross-platform application, running also under Linux and macOS.

We supply prebuilt EXE launcher for the application in our Release page.

### Building MSI Installer

If you have used the launch4j wrapper above to generate a single EXE file, you can also build a proper MSI installer, so the application can be installed/uninstalled properly under Microsoft Windows. You will need [WiX Toolset](https://wixtoolset.org/) to accomplish that.

Unfortunately WiX Toolset requires Microsoft Windows to run (you may test your luck with Wine / Mono though).

How to build the MSI installer:

* install the [WiX Toolset](https://wixtoolset.org/) if you did not already
* copy the EXE file you got from launch4j to the `wix` folder
* `cd` to your `wix` folder
* run `build_installer.bat` or execute these commands manually:

```
candle installer_win64.wxs
light -ext WixUIExtension -ext WixUtilExtension installer_win64.wixobj -out ptltrader_win64.msi
```

`ptltrader_win64.msi` will be built in your current directory.

**Please note for installing this container you need the JRE installed! JDK will not work.**

## License

PTL Trader is released under the [GNU GPL v3](https://www.gnu.org/licenses/gpl-3.0.html) license. See the included `COPYING` file for a full copy.